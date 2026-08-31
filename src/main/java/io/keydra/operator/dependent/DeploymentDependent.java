package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Environment;
import io.keydra.operator.install.Images;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.KeydraSpec;
import io.keydra.operator.v1alpha1.ProbeSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The application itself.
 *
 * <p>Startup, readiness and liveness are three different questions and this asks them separately. A
 * JVM applying migrations to an empty schema takes longer than a liveness probe should ever wait,
 * so the startup probe holds the other two off instead of their thresholds being widened to cover a
 * case that happens once. All three ask the management port, which is where {@code /q} lives in a
 * packaged run.
 */
@KubernetesDependent
public class DeploymentDependent extends CRUDKubernetesDependentResource<Deployment, Keydra> {

    @Override
    protected Deployment desired(Keydra keydra, Context<Keydra> context) {
        KeydraSpec spec = keydra.getSpec();

        Map<String, String> podLabels = new LinkedHashMap<>(Names.selector(keydra));
        if (spec.podLabels != null) {
            podLabels.putAll(spec.podLabels);
        }

        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .endMetadata()
                .withNewSpec()
                .withReplicas(spec.replicas)
                .withNewSelector()
                .withMatchLabels(Names.selector(keydra))
                .endSelector()
                .withNewTemplate()
                .withNewMetadata()
                .withLabels(podLabels)
                .withAnnotations(spec.podAnnotations)
                .endMetadata()
                .withNewSpec()
                .withImagePullSecrets(spec.imagePullSecrets)
                .withServiceAccountName(Names.serviceAccount(keydra))
                .withSecurityContext(
                        spec.podSecurityContext != null
                                ? spec.podSecurityContext
                                : Defaults.podSecurityContext())
                .withContainers(container(keydra, context))
                .withVolumes(volumes(keydra))
                .withNodeSelector(spec.nodeSelector)
                .withAffinity(spec.affinity)
                .withTolerations(spec.tolerations)
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
    }

    private Container container(Keydra keydra, Context<Keydra> context) {
        KeydraSpec spec = keydra.getSpec();
        return new ContainerBuilder()
                .withName("keydra")
                .withImage(Images.application(keydra))
                .withImagePullPolicy(Images.pullPolicy(keydra))
                .withSecurityContext(
                        spec.securityContext != null
                                ? spec.securityContext
                                : Defaults.containerSecurityContext())
                .withPorts(port("http", 8181), port("management", 9001))
                .withEnv(Environment.forApplication(keydra, context))
                .withStartupProbe(probe("/q/health/started", spec.probes.startup))
                .withReadinessProbe(probe("/q/health/ready", spec.probes.readiness))
                .withLivenessProbe(probe("/q/health/live", spec.probes.liveness))
                .withResources(
                        spec.resources != null ? spec.resources : Defaults.applicationResources())
                .withVolumeMounts(volumeMounts(keydra))
                .build();
    }

    private static ContainerPort port(String name, int number) {
        return new ContainerPortBuilder().withName(name).withContainerPort(number).build();
    }

    private static Probe probe(String path, ProbeSpec timings) {
        return new ProbeBuilder()
                .withNewHttpGet()
                .withPath(path)
                .withNewPort("management")
                .endHttpGet()
                .withFailureThreshold(timings == null ? null : timings.failureThreshold)
                .withPeriodSeconds(timings == null ? null : timings.periodSeconds)
                .withTimeoutSeconds(timings == null ? null : timings.timeoutSeconds)
                .build();
    }

    private static List<Volume> volumes(Keydra keydra) {
        List<Volume> volumes = new ArrayList<>();
        var backups = keydra.getSpec().backups;
        if (backups != null && Boolean.TRUE.equals(backups.enabled)) {
            String claim =
                    Names.isSet(backups.existingClaim)
                            ? backups.existingClaim
                            : Names.backupClaim(keydra);
            volumes.add(
                    new VolumeBuilder()
                            .withName("backups")
                            .withNewPersistentVolumeClaim()
                            .withClaimName(claim)
                            .endPersistentVolumeClaim()
                            .build());
        }
        if (keydra.getSpec().extraVolumes != null) {
            volumes.addAll(keydra.getSpec().extraVolumes);
        }
        return volumes;
    }

    private static List<VolumeMount> volumeMounts(Keydra keydra) {
        List<VolumeMount> mounts = new ArrayList<>();
        var backups = keydra.getSpec().backups;
        if (backups != null && Boolean.TRUE.equals(backups.enabled)) {
            mounts.add(
                    new VolumeMountBuilder()
                            .withName("backups")
                            .withMountPath(backups.mountPath)
                            .build());
        }
        if (keydra.getSpec().extraVolumeMounts != null) {
            mounts.addAll(keydra.getSpec().extraVolumeMounts);
        }
        return mounts;
    }
}
