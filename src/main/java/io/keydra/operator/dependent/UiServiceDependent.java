package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;

/** Where the Ingress or the Route sends people in the split shape. */
@KubernetesDependent
public class UiServiceDependent extends CRUDKubernetesDependentResource<Service, Keydra> {

    @Override
    protected Service desired(Keydra keydra, Context<Keydra> context) {
        return new ServiceBuilder()
                .withNewMetadata()
                .withName(Names.ui(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.uiLabels(keydra))
                .withAnnotations(keydra.getSpec().service.annotations)
                .endMetadata()
                .withNewSpec()
                .withType(keydra.getSpec().service.type)
                .addNewPort()
                .withName("http")
                .withPort(keydra.getSpec().ui.servicePort)
                .withTargetPort(new IntOrString("http"))
                .endPort()
                .withSelector(Names.uiSelector(keydra))
                .endSpec()
                .build();
    }
}
