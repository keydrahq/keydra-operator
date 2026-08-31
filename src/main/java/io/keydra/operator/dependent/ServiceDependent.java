package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;

/**
 * The interface and the API on one port, health and metrics on another.
 *
 * <p>Two ports rather than one, and the second is the reason the first can be published. What
 * answers on the management port answers without a session — which is what a scraper and the
 * platform's probes need — and what it says is which instances are running, how many targets there
 * are and how much work is moving: a map of the installation, handed to anybody who can reach it.
 * So it is on a port of its own, and nothing that faces outwards ever points at it.
 */
@KubernetesDependent
public class ServiceDependent extends CRUDKubernetesDependentResource<Service, Keydra> {

    @Override
    protected Service desired(Keydra keydra, Context<Keydra> context) {
        var service = keydra.getSpec().service;
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .withAnnotations(service.annotations)
                .endMetadata()
                .withNewSpec()
                .withType(service.type)
                .addNewPort()
                .withName("http")
                .withPort(service.port)
                .withTargetPort(new IntOrString("http"))
                .endPort()
                .addNewPort()
                .withName("management")
                .withPort(service.managementPort)
                .withTargetPort(new IntOrString("management"))
                .endPort()
                .withSelector(Names.selector(keydra))
                .endSpec()
                .build();
    }
}
