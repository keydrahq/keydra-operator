package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Environment;
import io.keydra.operator.install.Images;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.UiSpec;

/**
 * The interface as static files behind an nginx, in the split shape.
 *
 * <p>Its probes ask the index and say nothing about the backend, which is deliberate: an interface
 * that reports itself unhealthy because the API is down is an interface that cannot show you the
 * error.
 */
@KubernetesDependent
public class UiDeploymentDependent extends CRUDKubernetesDependentResource<Deployment, Keydra> {

    @Override
    protected Deployment desired(Keydra keydra, Context<Keydra> context) {
        UiSpec ui = keydra.getSpec().ui;
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(Names.ui(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.uiLabels(keydra))
                .endMetadata()
                .withNewSpec()
                .withReplicas(ui.replicas)
                .withNewSelector()
                .withMatchLabels(Names.uiSelector(keydra))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(Names.uiSelector(keydra))
                .withAnnotations(ui.podAnnotations)
                .endMetadata()
                .withNewSpec()
                .withImagePullSecrets(keydra.getSpec().imagePullSecrets)
                .withServiceAccountName(Names.serviceAccount(keydra))
                .withSecurityContext(
                        keydra.getSpec().podSecurityContext != null
                                ? keydra.getSpec().podSecurityContext
                                : Defaults.podSecurityContext())
                .addNewContainer()
                .withName("ui")
                .withImage(Images.ui(keydra))
                .withImagePullPolicy(Images.pullPolicy(keydra))
                .withSecurityContext(
                        keydra.getSpec().securityContext != null
                                ? keydra.getSpec().securityContext
                                : Defaults.containerSecurityContext())
                .addNewPort()
                .withName("http")
                .withContainerPort(8080)
                .endPort()
                .withEnv(Environment.forUi(keydra))
                .withReadinessProbe(indexProbe(10))
                .withLivenessProbe(indexProbe(30))
                .withResources(ui.resources != null ? ui.resources : Defaults.uiResources())
                .endContainer()
                .withNodeSelector(ui.nodeSelector)
                .withAffinity(ui.affinity)
                .withTolerations(ui.tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private static Probe indexProbe(int periodSeconds) {
        return new ProbeBuilder()
                .withNewHttpGet()
                .withPath("/")
                .withNewPort("http")
                .endHttpGet()
                .withPeriodSeconds(periodSeconds)
                .build();
    }
}
