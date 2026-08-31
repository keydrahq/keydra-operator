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
import io.keydra.operator.v1alpha1.ServiceMonitorSpec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Prometheus operator scrapes.
 *
 * <p>The management port, which is where {@code /q} lives in a packaged run and is the reason it is
 * a separate port at all: the scraper reaches the pod directly, from inside the cluster where it
 * already is, and whatever publishes the installation publishes nothing that describes it.
 */
@KubernetesDependent
public class ServiceMonitorDependent extends GenericKubernetesDependentResource<Keydra>
        implements Creator<GenericKubernetesResource, Keydra>,
                Updater<GenericKubernetesResource, Keydra>,
                GarbageCollected<Keydra> {

    public static final GroupVersionKind GVK =
            new GroupVersionKind("monitoring.coreos.com", "v1", "ServiceMonitor");

    public ServiceMonitorDependent() {
        super(GVK);
    }

    @Override
    protected GenericKubernetesResource desired(Keydra keydra, Context<Keydra> context) {
        ServiceMonitorSpec monitor = keydra.getSpec().serviceMonitor;

        Map<String, String> labels = new LinkedHashMap<>(Names.labels(keydra));
        if (monitor.labels != null) {
            labels.putAll(monitor.labels);
        }

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("selector", Map.of("matchLabels", Names.selector(keydra)));
        spec.put(
                "endpoints",
                List.of(
                        Map.of(
                                "port",
                                "management",
                                "path",
                                "/q/metrics",
                                "interval",
                                monitor.interval)));

        return new GenericKubernetesResourceBuilder()
                .withApiVersion(GVK.apiVersion())
                .withKind(GVK.getKind())
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .addToAdditionalProperties("spec", spec)
                .build();
    }
}
