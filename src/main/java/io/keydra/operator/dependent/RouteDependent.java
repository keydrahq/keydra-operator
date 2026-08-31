package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.api.model.GenericKubernetesResourceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.RouteSpec;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The front door, where the cluster is an OpenShift.
 *
 * <p>Untyped on purpose. Taking {@code openshift-client} for one object would put a second model
 * tree into an image whose whole job is to be small, and it would not buy what it looks like it
 * buys: the classes compile whether or not the cluster has the CRD, so the check for whether Routes
 * exist has to happen at runtime anyway. Here it happens in the activation condition, where a
 * cluster with no Routes means this dependent is simply not part of the workflow.
 */
@KubernetesDependent
public class RouteDependent extends GenericKubernetesDependentResource<Keydra>
        implements Creator<GenericKubernetesResource, Keydra>,
                Updater<GenericKubernetesResource, Keydra>,
                GarbageCollected<Keydra> {

    public static final GroupVersionKind GVK =
            new GroupVersionKind("route.openshift.io", "v1", "Route");

    public RouteDependent() {
        super(GVK);
    }

    @Override
    protected GenericKubernetesResource desired(Keydra keydra, Context<Keydra> context) {
        RouteSpec route = keydra.getSpec().route;

        Map<String, Object> spec = new LinkedHashMap<>();
        if (Names.isSet(route.host)) {
            spec.put("host", route.host);
        }
        spec.put(
                "to",
                Map.of("kind", "Service", "name", Names.frontDoorService(keydra), "weight", 100));
        // By name rather than by number: the Service names its ports, and a Route that pointed at
        // 8181 would be right until somebody moved it.
        spec.put("port", Map.of("targetPort", "http"));
        if (!"passthrough".equals(route.termination)) {
            spec.put(
                    "tls",
                    Map.of(
                            "termination",
                            route.termination,
                            "insecureEdgeTerminationPolicy",
                            route.insecureEdgeTerminationPolicy));
        } else {
            spec.put("tls", Map.of("termination", route.termination));
        }

        return new GenericKubernetesResourceBuilder()
                .withApiVersion(GVK.apiVersion())
                .withKind(GVK.getKind())
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .withAnnotations(route.annotations)
                .endMetadata()
                .addToAdditionalProperties("spec", spec)
                .build();
    }
}
