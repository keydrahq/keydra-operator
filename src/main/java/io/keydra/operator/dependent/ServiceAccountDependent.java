package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;

/**
 * The account the instance's pods run as.
 *
 * <p>Keydra talks to a database and to the servers somebody configured. It asks the API server for
 * nothing, so its token is not mounted and it has no Role — an application that is handed a token
 * it never uses is an application whose token is worth stealing for no reason.
 */
@KubernetesDependent
public class ServiceAccountDependent
        extends CRUDKubernetesDependentResource<ServiceAccount, Keydra> {

    @Override
    protected ServiceAccount desired(Keydra keydra, Context<Keydra> context) {
        return new ServiceAccountBuilder()
                .withNewMetadata()
                .withName(Names.serviceAccount(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .withAnnotations(keydra.getSpec().serviceAccount.annotations)
                .endMetadata()
                .withAutomountServiceAccountToken(false)
                .build();
    }
}
