package io.keydra.operator.dependent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.keydra.operator.install.Specs;
import io.keydra.operator.v1alpha1.Keydra;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What the Deployment the operator writes actually says. */
class DeploymentDependentTest {

    private final DeploymentDependent dependent = new DeploymentDependent();

    @SuppressWarnings("unchecked")
    private static Context<Keydra> context() {
        Context<Keydra> context = mock(Context.class);
        when(context.getSecondaryResource(any(), anyString())).thenReturn(Optional.empty());
        return context;
    }

    private Container container(Keydra keydra) {
        Deployment deployment = dependent.desired(keydra, context());
        return deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
    }

    @Test
    void asksTheManagementPortForAllThreeHealthAnswers() {
        Container container = container(Specs.minimal());
        assertThat(container.getStartupProbe().getHttpGet().getPath())
                .isEqualTo("/q/health/started");
        assertThat(container.getStartupProbe().getHttpGet().getPort().getStrVal())
                .isEqualTo("management");
        assertThat(container.getReadinessProbe().getHttpGet().getPort().getStrVal())
                .isEqualTo("management");
        assertThat(container.getLivenessProbe().getHttpGet().getPort().getStrVal())
                .isEqualTo("management");
    }

    @Test
    void givesTheStartupProbeRoomTheOthersDoNotNeed() {
        Container container = container(Specs.minimal());
        assertThat(container.getStartupProbe().getFailureThreshold()).isEqualTo(30);
        assertThat(container.getLivenessProbe().getFailureThreshold()).isNull();
    }

    @Test
    void capsMemoryBecauseTheHeapIsAPercentageOfWhatItCanSee() {
        Container container = container(Specs.minimal());
        assertThat(container.getResources().getLimits()).containsKey("memory");
    }

    @Test
    void dropsEveryCapabilityAndRefusesEscalation() {
        Container container = container(Specs.minimal());
        assertThat(container.getSecurityContext().getAllowPrivilegeEscalation()).isFalse();
        assertThat(container.getSecurityContext().getCapabilities().getDrop())
                .containsExactly("ALL");
    }

    @Test
    void mountsNothingWhenBackupsAreOff() {
        Deployment deployment = dependent.desired(Specs.minimal(), context());
        assertThat(deployment.getSpec().getTemplate().getSpec().getVolumes()).isEmpty();
        assertThat(container(Specs.minimal()).getVolumeMounts()).isEmpty();
    }

    @Test
    void mountsTheClaimItCreatesWhenBackupsAreOn() {
        Keydra keydra = Specs.maximal();
        Deployment deployment = dependent.desired(keydra, context());
        assertThat(
                        deployment
                                .getSpec()
                                .getTemplate()
                                .getSpec()
                                .getVolumes()
                                .get(0)
                                .getPersistentVolumeClaim()
                                .getClaimName())
                .isEqualTo("keydra-backups");
        assertThat(container(keydra).getVolumeMounts().get(0).getMountPath())
                .isEqualTo("/var/lib/keydra/backups");
    }

    @Test
    void takesTheStandaloneImageByDefaultAndTheBackendWhenSplit() {
        assertThat(container(Specs.minimal()).getImage()).startsWith("quay.io/keydrahq/keydra:");

        Keydra split = Specs.minimal();
        split.getSpec().mode = io.keydra.operator.v1alpha1.DeploymentMode.split;
        assertThat(container(split).getImage()).startsWith("quay.io/keydrahq/keydra-backend:");
    }

    @Test
    void aDigestWinsOverATag() {
        Keydra keydra = Specs.minimal();
        keydra.getSpec().image.tag = "0.0.9";
        keydra.getSpec().image.digest = "sha256:" + "0".repeat(64);
        assertThat(container(keydra).getImage()).contains("@sha256:").doesNotContain(":0.0.9");
    }

    @Test
    void selectsOnLabelsTheChartAlsoUsesSoAnInstallationCanBeAdopted() {
        Deployment deployment = dependent.desired(Specs.minimal(), context());
        assertThat(deployment.getSpec().getSelector().getMatchLabels())
                .containsExactlyInAnyOrderEntriesOf(
                        java.util.Map.of(
                                "app.kubernetes.io/name", "keydra",
                                "app.kubernetes.io/instance", "keydra"));
    }
}
