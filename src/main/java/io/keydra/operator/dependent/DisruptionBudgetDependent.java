package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudgetBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;

/** How much of the installation a node drain may take at once. */
@KubernetesDependent
public class DisruptionBudgetDependent
        extends CRUDKubernetesDependentResource<PodDisruptionBudget, Keydra> {

    @Override
    protected PodDisruptionBudget desired(Keydra keydra, Context<Keydra> context) {
        return new PodDisruptionBudgetBuilder()
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .endMetadata()
                .withNewSpec()
                .withMinAvailable(
                        new IntOrString(keydra.getSpec().podDisruptionBudget.minAvailable))
                .withNewSelector()
                .withMatchLabels(Names.selector(keydra))
                .endSelector()
                .endSpec()
                .build();
    }
}
