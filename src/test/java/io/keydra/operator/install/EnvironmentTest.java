package io.keydra.operator.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.keydra.operator.v1alpha1.Keydra;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** What the container is actually given. */
class EnvironmentTest {

    private static Context<Keydra> noSecondaries() {
        @SuppressWarnings("unchecked")
        Context<Keydra> context = mock(Context.class);
        when(context.getSecondaryResource(any(), anyString())).thenReturn(Optional.empty());
        return context;
    }

    private static Map<String, EnvVar> byName(List<EnvVar> env) {
        return env.stream().collect(Collectors.toMap(EnvVar::getName, Function.identity()));
    }

    @Test
    void takesEveryCredentialFromTheSecretAndNeverFromTheResource() {
        Keydra keydra = Specs.maximal();
        Map<String, EnvVar> env = byName(Environment.forApplication(keydra, noSecondaries()));

        List<String> credentials =
                List.of(
                        "KEYDRA_SECRET_KEY",
                        "KEYDRA_DB_PASSWORD",
                        "KEYDRA_OIDC_SECRET",
                        "KEYDRA_CLICKHOUSE_PASSWORD",
                        "KEYDRA_MAIL_API_KEY");
        for (String name : credentials) {
            assertThat(env).containsKey(name);
            assertThat(env.get(name).getValue()).as("%s must never be a literal", name).isNull();
            assertThat(env.get(name).getValueFrom().getSecretKeyRef().getName())
                    .isEqualTo("keydra");
        }
    }

    @Test
    void namesTheInstanceAfterThePodSoTheTwoListsAgree() {
        Map<String, EnvVar> env =
                byName(Environment.forApplication(Specs.minimal(), noSecondaries()));
        assertThat(env.get("KEYDRA_INSTANCE_ID").getValueFrom().getFieldRef().getFieldPath())
                .isEqualTo("metadata.name");
    }

    @Test
    void saysNothingAboutAProxyWhenThereIsNoneInFront() {
        Map<String, EnvVar> env =
                byName(Environment.forApplication(Specs.minimal(), noSecondaries()));
        assertThat(env).doesNotContainKeys("KEYDRA_BEHIND_PROXY", "KEYDRA_TRUSTED_PROXIES");
    }

    @Test
    void namesTheProxiesWhenThereIsOne() {
        Map<String, EnvVar> env =
                byName(Environment.forApplication(Specs.maximal(), noSecondaries()));
        assertThat(env.get("KEYDRA_BEHIND_PROXY").getValue()).isEqualTo("true");
        assertThat(env.get("KEYDRA_TRUSTED_PROXIES").getValue()).isEqualTo("10.0.0.0/8");
    }

    @Test
    void extraEnvReplacesWhatItNamesRatherThanShadowingIt() {
        Keydra keydra = Specs.minimal();
        keydra.getSpec().extraEnv =
                List.of(
                        new EnvVarBuilder()
                                .withName("JAVA_OPTS")
                                .withValue("-XX:MaxRAMPercentage=50")
                                .build(),
                        new EnvVarBuilder()
                                .withName("KEYDRA_EGRESS_ALLOW_PRIVATE")
                                .withValue("true")
                                .build());

        List<EnvVar> env = Environment.forApplication(keydra, noSecondaries());

        assertThat(env.stream().filter(var -> "JAVA_OPTS".equals(var.getName())).count())
                .as("one entry, not two with the later one quietly winning")
                .isEqualTo(1);
        assertThat(byName(env).get("JAVA_OPTS").getValue()).isEqualTo("-XX:MaxRAMPercentage=50");
        assertThat(byName(env)).containsKey("KEYDRA_EGRESS_ALLOW_PRIVATE");
    }

    @Test
    void takesThePublicUrlFromTheIngressWhenTheSpecNamesNone() {
        Keydra keydra = Specs.maximal();
        keydra.getSpec().publicUrl = null;
        assertThat(Environment.publicUrl(keydra, noSecondaries()))
                .contains("http://keydra.example.com");
    }

    @Test
    void hasNoPublicUrlWhenNothingPublishesIt() {
        assertThat(Environment.publicUrl(Specs.minimal(), noSecondaries())).isEmpty();
    }

    @Test
    void pointsTheInterfaceAtTheApiByServiceName() {
        assertThat(Environment.forUi(Specs.minimal()).get(0).getValue())
                .isEqualTo("http://keydra:8181");
    }
}
