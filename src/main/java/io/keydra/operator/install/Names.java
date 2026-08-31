package io.keydra.operator.install;

import io.keydra.operator.v1alpha1.DeploymentMode;
import io.keydra.operator.v1alpha1.Keydra;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What everything an installation owns is called, and what it is labelled with.
 *
 * <p>The names and the labels are the chart's, to the character, and that is not nostalgia. An
 * estate that installed Keydra with Helm and wants the operator instead has to be able to point the
 * operator at what is already there; a Deployment's {@code selector} is immutable, so a scheme that
 * differed by one label would make that migration a delete and a recreate rather than an adoption.
 *
 * <p>The one thing that is not the chart's is {@code app.kubernetes.io/managed-by}, which says
 * which of the two is looking after this. It is not part of the selector, so changing it is
 * something a running installation survives — which is exactly why the selector is the wrong place
 * for it.
 */
public final class Names {

    private Names() {}

    /** What the operator writes into {@code managed-by}. */
    public static final String MANAGER = "keydra-operator";

    public static String of(Keydra keydra) {
        return keydra.getMetadata().getName();
    }

    /** The interface's own Deployment and Service, in the split shape. */
    public static String ui(Keydra keydra) {
        return truncate(of(keydra) + "-ui");
    }

    /** The claim a local backup destination writes to. */
    public static String backupClaim(Keydra keydra) {
        return truncate(of(keydra) + "-backups");
    }

    /**
     * The account the pods run as.
     *
     * <p>Falls back to {@code default} where the spec says not to create one and names none, which
     * is what the chart does and what a cluster with its own account arrangement expects.
     */
    public static String serviceAccount(Keydra keydra) {
        var spec = keydra.getSpec().serviceAccount;
        if (spec == null || Boolean.TRUE.equals(spec.create)) {
            return spec != null && isSet(spec.name) ? spec.name : of(keydra);
        }
        return isSet(spec.name) ? spec.name : "default";
    }

    /**
     * Which Service an Ingress or a Route should send people to.
     *
     * <p>Not the same one in the two shapes: standalone serves the interface from the API's own
     * port, split serves it from nginx. Worked out here so neither the Ingress nor the Route has to
     * know which shape it is in.
     */
    public static String frontDoorService(Keydra keydra) {
        return keydra.getSpec().mode == DeploymentMode.split ? ui(keydra) : of(keydra);
    }

    public static int frontDoorPort(Keydra keydra) {
        return keydra.getSpec().mode == DeploymentMode.split
                ? keydra.getSpec().ui.servicePort
                : keydra.getSpec().service.port;
    }

    /** Everything an object carries. */
    public static Map<String, String> labels(Keydra keydra) {
        Map<String, String> labels = new LinkedHashMap<>(selector(keydra));
        labels.put("app.kubernetes.io/managed-by", MANAGER);
        labels.put("app.kubernetes.io/part-of", "keydra");
        labels.put("app.kubernetes.io/version", Images.version(keydra));
        return labels;
    }

    /** The two a Deployment matches on, and which therefore may never change. */
    public static Map<String, String> selector(Keydra keydra) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app.kubernetes.io/name", "keydra");
        labels.put("app.kubernetes.io/instance", of(keydra));
        return labels;
    }

    public static Map<String, String> uiLabels(Keydra keydra) {
        Map<String, String> labels = new LinkedHashMap<>(uiSelector(keydra));
        labels.put("app.kubernetes.io/managed-by", MANAGER);
        labels.put("app.kubernetes.io/part-of", "keydra");
        labels.put("app.kubernetes.io/component", "ui");
        labels.put("app.kubernetes.io/version", Images.version(keydra));
        return labels;
    }

    public static Map<String, String> uiSelector(Keydra keydra) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app.kubernetes.io/name", "keydra-ui");
        labels.put("app.kubernetes.io/instance", of(keydra));
        return labels;
    }

    public static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /** A label value and a resource name both stop at 63 characters. */
    private static String truncate(String name) {
        String cut = name.length() <= 63 ? name : name.substring(0, 63);
        while (cut.endsWith("-")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }
}
