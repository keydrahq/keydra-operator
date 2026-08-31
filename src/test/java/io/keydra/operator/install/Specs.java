package io.keydra.operator.install;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.keydra.operator.v1alpha1.IngressHost;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.KeydraSpec;
import java.util.List;

/** Resources for the tests to work on, so no test has to build one from twenty setters. */
public final class Specs {

    private Specs() {}

    /** The smallest thing that installs: a database, and a Secret to read credentials from. */
    public static Keydra minimal() {
        Keydra keydra = new Keydra();
        keydra.setMetadata(
                new ObjectMetaBuilder().withName("keydra").withNamespace("apps").build());
        KeydraSpec spec = keydra.getSpec();
        spec.database.url = "postgresql://db:5432/keydra";
        spec.secret.name = "keydra";
        return keydra;
    }

    /**
     * Everything set, which is what the chart-agreement test needs.
     *
     * <p>A maximal spec is the only way to see every environment variable the operator can emit:
     * most of them are conditional, and a comparison against a minimal one would agree with the
     * chart about the six that are unconditional and say nothing about the rest.
     */
    public static Keydra maximal() {
        Keydra keydra = minimal();
        KeydraSpec spec = keydra.getSpec();

        spec.proxy.enabled = true;
        spec.proxy.trusted = "10.0.0.0/8";
        spec.publicUrl = "https://keydra.example.com";
        spec.cookieSecure = true;
        spec.replicas = 2;
        spec.sharedStore.url = "redis://keydra-store:6379";

        spec.backups.enabled = true;

        spec.identityProvider.url = "https://sso.example.com/realms/keydra";
        spec.identityProvider.clientId = "keydra";
        spec.identityProvider.rolesClaim = "keydra_roles";

        spec.metricsHistory.url = "http://clickhouse:8123";
        spec.metricsHistory.username = "keydra";
        spec.metricsHistory.passwordSecretKey = "clickhouse-password";

        spec.mail.host = "smtp.example.com";
        spec.mail.from = "keydra@example.com";
        spec.mail.username = "keydra";
        spec.mail.apiKeySecretKey = "mail-api-key";

        spec.observability.otlpEndpoint = "http://tempo:4317";

        spec.ingress.enabled = true;
        IngressHost host = new IngressHost();
        host.host = "keydra.example.com";
        spec.ingress.hosts = List.of(host);

        return keydra;
    }
}
