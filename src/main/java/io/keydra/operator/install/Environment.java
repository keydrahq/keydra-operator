package io.keydra.operator.install;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.KeydraSpec;
import io.keydra.operator.v1alpha1.SecretSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The spec, said in the fifty-two environment variables the application actually reads.
 *
 * <p>This is where the operator earns the comparison with the chart, and where the two have to
 * agree: a field that maps to a different variable here than it does in {@code deployment.yaml} is
 * a deployment that behaves differently depending on how it was installed, which is the failure
 * this whole exercise exists to avoid. {@code EnvironmentMatchesTheChartTest} reads the chart's
 * template and checks the two lists against each other, because a rule that is checked is a
 * different kind of thing from a rule that is written down.
 *
 * <p>Two deliberate departures from the chart, both in the same direction.
 *
 * <p><b>Nothing secret is ever a literal.</b> The chart writes the ClickHouse password and the mail
 * password into the Deployment as values, which is defensible for a chart — they came from a values
 * file somebody was already holding. Here they would have to come from the custom resource, where
 * they would be readable by anybody who can {@code get keydra}. So both are key references into the
 * Secret the spec already names, and there is no field to put a password in.
 *
 * <p><b>{@code extraEnv} wins by removal, not by ordering.</b> The chart appends it last and relies
 * on the kubelet preferring the later of two entries with one name. That is true and it is also a
 * thing nobody should have to know: a computed variable whose name appears in {@code extraEnv} is
 * dropped here, so what the container is given says what the container will read.
 */
public final class Environment {

    private Environment() {}

    /** What the application container is given. */
    public static List<EnvVar> forApplication(Keydra keydra, Context<Keydra> context) {
        KeydraSpec spec = keydra.getSpec();
        SecretSpec secret = spec.secret;
        List<EnvVar> env = new ArrayList<>();

        env.add(value("KEYDRA_DB_URL", spec.database.url));
        env.add(value("KEYDRA_DB_USERNAME", spec.database.username));
        env.add(fromSecret("KEYDRA_DB_PASSWORD", secret.name, secret.databasePasswordKey));
        env.add(fromSecret("KEYDRA_SECRET_KEY", secret.name, secret.secretKeyKey));

        // Each instance on the roster under the name the platform gave its pod, so a person
        // reading the instances page and a person reading `kubectl get pods` are looking at the
        // same list.
        env.add(
                new EnvVarBuilder()
                        .withName("KEYDRA_INSTANCE_ID")
                        .withNewValueFrom()
                        .withNewFieldRef()
                        .withFieldPath("metadata.name")
                        .endFieldRef()
                        .endValueFrom()
                        .build());

        env.add(value("KEYDRA_SECURITY_ENABLED", String.valueOf(spec.securityEnabled)));
        env.add(value("KEYDRA_COOKIE_SECURE", String.valueOf(spec.cookieSecure)));

        if (spec.proxy != null && Boolean.TRUE.equals(spec.proxy.enabled)) {
            env.add(value("KEYDRA_BEHIND_PROXY", "true"));
            env.add(value("KEYDRA_TRUSTED_PROXIES", spec.proxy.trusted));
        }

        publicUrl(keydra, context).ifPresent(url -> env.add(value("KEYDRA_PUBLIC_URL", url)));

        if (spec.sharedStore != null && Names.isSet(spec.sharedStore.url)) {
            env.add(value("KEYDRA_STORE_URL", spec.sharedStore.url));
        }

        if (spec.backups != null && Boolean.TRUE.equals(spec.backups.enabled)) {
            env.add(value("KEYDRA_BACKUP_DIR", spec.backups.mountPath));
        }

        if (spec.identityProvider != null && Names.isSet(spec.identityProvider.url)) {
            env.add(value("KEYDRA_OIDC_URL", spec.identityProvider.url));
            env.add(value("KEYDRA_OIDC_CLIENT_ID", spec.identityProvider.clientId));
            env.add(fromSecret("KEYDRA_OIDC_SECRET", secret.name, secret.oidcSecretKey));
            if (Names.isSet(spec.identityProvider.rolesClaim)) {
                env.add(value("KEYDRA_OIDC_ROLES_CLAIM", spec.identityProvider.rolesClaim));
            }
        }

        if (spec.metricsHistory != null && Names.isSet(spec.metricsHistory.url)) {
            env.add(value("KEYDRA_CLICKHOUSE_ENABLED", "true"));
            env.add(value("KEYDRA_CLICKHOUSE_URL", spec.metricsHistory.url));
            if (Names.isSet(spec.metricsHistory.username)) {
                env.add(value("KEYDRA_CLICKHOUSE_USER", spec.metricsHistory.username));
            }
            if (Names.isSet(spec.metricsHistory.passwordSecretKey)) {
                env.add(
                        fromSecret(
                                "KEYDRA_CLICKHOUSE_PASSWORD",
                                secret.name,
                                spec.metricsHistory.passwordSecretKey));
            }
        }

        if (spec.mail != null && Names.isSet(spec.mail.host)) {
            env.add(value("KEYDRA_MAIL_HOST", spec.mail.host));
            env.add(value("KEYDRA_MAIL_PORT", String.valueOf(spec.mail.port)));
            env.add(value("KEYDRA_MAIL_TLS", String.valueOf(spec.mail.tls)));
            env.add(value("KEYDRA_MAIL_FROM", spec.mail.from));
            if (Names.isSet(spec.mail.username)) {
                env.add(value("KEYDRA_MAIL_USERNAME", spec.mail.username));
            }
            if (Names.isSet(spec.mail.apiKeySecretKey)) {
                env.add(fromSecret("KEYDRA_MAIL_API_KEY", secret.name, spec.mail.apiKeySecretKey));
            }
        }

        if (spec.observability != null) {
            if (Names.isSet(spec.observability.otlpEndpoint)) {
                env.add(value("KEYDRA_OTLP_ENDPOINT", spec.observability.otlpEndpoint));
            }
            env.add(value("KEYDRA_JSON_LOGS", String.valueOf(spec.observability.jsonLogs)));
            env.add(value("KEYDRA_ACCESS_LOG", String.valueOf(spec.observability.accessLog)));
        }

        // Reads the container's own limit rather than the node's, so a limited pod does not size
        // its heap for a machine it cannot use.
        env.add(value("JAVA_OPTS", "-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"));

        return withOverrides(env, spec.extraEnv);
    }

    /** What the interface's nginx is given, in the split shape. */
    public static List<EnvVar> forUi(Keydra keydra) {
        // Where /api and /graphql go. The interface is proxied rather than calling the API across
        // origins: the session is a cookie, and a cookie sent to another origin needs
        // SameSite=None and a CORS policy that allows credentials.
        return List.of(
                value(
                        "KEYDRA_BACKEND",
                        "http://" + Names.of(keydra) + ":" + keydra.getSpec().service.port));
    }

    /**
     * The address a browser reaches this at.
     *
     * <p>Named in the spec, or worked out from whatever publishes the installation. The Route is
     * asked for its assigned hostname rather than the one the spec requested, because the spec is
     * allowed to request none — and on the first reconciliation of a Route with a generated
     * hostname there is not one yet. That is one extra rollout on first install and it is the
     * honest arrangement: the alternative is an instance whose invitation links point at the
     * ingress controller's idea of the host until somebody notices.
     */
    public static Optional<String> publicUrl(Keydra keydra, Context<Keydra> context) {
        KeydraSpec spec = keydra.getSpec();
        if (Names.isSet(spec.publicUrl)) {
            return Optional.of(spec.publicUrl);
        }
        if (spec.route != null && Boolean.TRUE.equals(spec.route.enabled)) {
            return routeHost(context).map(host -> "https://" + host);
        }
        if (spec.ingress != null
                && Boolean.TRUE.equals(spec.ingress.enabled)
                && spec.ingress.hosts != null
                && !spec.ingress.hosts.isEmpty()) {
            String host = spec.ingress.hosts.get(0).host;
            boolean secured = spec.ingress.tls != null && !spec.ingress.tls.isEmpty();
            return Optional.of((secured ? "https://" : "http://") + host);
        }
        return Optional.empty();
    }

    /** The hostname OpenShift settled on, read out of the Route's status. */
    @SuppressWarnings("unchecked")
    public static Optional<String> routeHost(Context<Keydra> context) {
        return context.getSecondaryResource(GenericKubernetesResource.class, "route")
                .map(GenericKubernetesResource::getAdditionalProperties)
                .map(properties -> properties.get("status"))
                .filter(java.util.Map.class::isInstance)
                .map(status -> ((java.util.Map<String, Object>) status).get("ingress"))
                .filter(List.class::isInstance)
                .map(ingress -> (List<Object>) ingress)
                .filter(ingress -> !ingress.isEmpty())
                .map(ingress -> ingress.get(0))
                .filter(java.util.Map.class::isInstance)
                .map(first -> ((java.util.Map<String, Object>) first).get("host"))
                .map(String::valueOf)
                .filter(Names::isSet);
    }

    private static List<EnvVar> withOverrides(List<EnvVar> computed, List<EnvVar> extra) {
        if (extra == null || extra.isEmpty()) {
            return computed;
        }
        Set<String> overridden = new LinkedHashSet<>();
        extra.forEach(var -> overridden.add(var.getName()));
        List<EnvVar> merged = new ArrayList<>(computed.size() + extra.size());
        computed.stream().filter(var -> !overridden.contains(var.getName())).forEach(merged::add);
        merged.addAll(extra);
        return merged;
    }

    private static EnvVar value(String name, String value) {
        return new EnvVarBuilder().withName(name).withValue(value).build();
    }

    private static EnvVar fromSecret(String name, String secretName, String key) {
        return new EnvVarBuilder()
                .withName(name)
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(secretName)
                .withKey(key)
                .endSecretKeyRef()
                .endValueFrom()
                .build();
    }
}
