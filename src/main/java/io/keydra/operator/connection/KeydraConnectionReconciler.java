package io.keydra.operator.connection;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.keydra.operator.api.ConnectionRequest;
import io.keydra.operator.api.ConnectionResponse;
import io.keydra.operator.api.ConnectionStatus;
import io.keydra.operator.api.KeydraApi;
import io.keydra.operator.install.Names;
import io.keydra.operator.status.Conditions;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.KeydraConnection;
import io.keydra.operator.v1alpha1.KeydraConnectionSpec;
import io.keydra.operator.v1alpha1.KeydraConnectionStatus;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * A target, kept registered in the instance the resource names.
 *
 * <p>Everything difficult about this reconciler comes from the same fact: what it owns is not in
 * the cluster. There is no owner reference to a row in somebody else's PostgreSQL, so none of the
 * safety a Kubernetes-only controller gets for free applies here, and three rules take its place.
 *
 * <p><b>The id in the status is the identity, not the name.</b> A reconciliation that cannot reach
 * the instance must not look like a reconciliation that has never run — otherwise a network blip
 * produces a second profile, and then a third. So a profile is created exactly once, its id is
 * written to the status, and every later pass addresses it by that id.
 *
 * <p><b>A profile it did not create is not adopted.</b> If the instance already has a profile by
 * that name and this resource has no id, the answer is a refusal that says so rather than an
 * update. Adopting would mean a resource silently taking ownership of a target somebody configured
 * by hand — and then deleting it when the resource goes.
 *
 * <p><b>Deleting the resource deletes the profile.</b> Which is the property this exists for, and
 * is why it holds a finalizer: a target that exists because a manifest says so should stop existing
 * when the manifest does. Where the instance cannot be reached, the finalizer holds and the
 * deletion waits, because the alternative is a resource that disappears leaving a target nobody
 * remembers declaring.
 */
@ControllerConfiguration(name = "keydraconnection")
@CSVMetadata(name = "keydra-operator")
// The Secret holding the account to sign in as, the Secret holding the target's password, and
// the Keydra resource that says where the instance is. All three are reads: this controller
// writes nothing to the cluster except its own resource's status, because what it writes lives
// in somebody else's database.
@AdditionalRBACRules({
    @RBACRule(
            apiGroups = "",
            resources = "secrets",
            verbs = {"get"}),
    @RBACRule(
            apiGroups = "keydra.io",
            resources = "keydras",
            verbs = {"get", "list", "watch"})
})
public class KeydraConnectionReconciler
        implements Reconciler<KeydraConnection>, Cleaner<KeydraConnection> {

    private static final Logger LOG = Logger.getLogger(KeydraConnectionReconciler.class);

    private final KubernetesClient client;
    private final KeydraApi api;

    @Inject
    public KeydraConnectionReconciler(KubernetesClient client, KeydraApi api) {
        this.client = client;
        this.api = api;
    }

    @Override
    public UpdateControl<KeydraConnection> reconcile(
            KeydraConnection resource, Context<KeydraConnection> context) {

        KeydraConnectionStatus status =
                resource.getStatus() == null ? new KeydraConnectionStatus() : resource.getStatus();
        if (status.conditions == null) {
            status.conditions = new ArrayList<>();
        }
        status.observedGeneration = resource.getMetadata().getGeneration();

        KeydraApi.Instance instance;
        try {
            instance = instanceFor(resource);
        } catch (NotReadyException refusal) {
            return refuse(resource, status, refusal.getMessage());
        }

        ConnectionRequest request = requestFor(resource);

        ConnectionResponse profile;
        if (status.profileId == null) {
            Optional<ConnectionResponse> existing =
                    api.call(instance, cookie -> api.endpoint(instance).list(cookie)).stream()
                            .filter(candidate -> request.name().equals(candidate.name()))
                            .findFirst();
            if (existing.isPresent()) {
                return refuse(
                        resource,
                        status,
                        "The instance already has a profile called \""
                                + request.name()
                                + "\" that this resource did not create. The operator does not"
                                + " adopt one: it would mean taking ownership of a target somebody"
                                + " configured by hand, and then deleting it when this resource"
                                + " goes. Rename one of the two, or delete the profile in Keydra"
                                + " and let this create it.");
            }
            profile = api.call(instance, cookie -> api.endpoint(instance).create(cookie, request));
            status.profileId = profile.id();
            LOG.infof(
                    "Registered %s as profile %d on %s",
                    request.name(), profile.id(), instance.baseUri());
        } else {
            long id = status.profileId;
            try {
                profile =
                        api.call(
                                instance,
                                cookie -> api.endpoint(instance).update(cookie, id, request));
            } catch (WebApplicationException failure) {
                if (failure.getResponse().getStatus() != 404) {
                    throw failure;
                }
                // Somebody deleted it in the console. The resource still says it should exist, so
                // it is created again on the next pass rather than argued about.
                LOG.infof(
                        "Profile %d is gone from %s; it will be created again",
                        id, instance.baseUri());
                status.profileId = null;
                resource.setStatus(status);
                return UpdateControl.patchStatus(resource);
            }
        }

        Conditions.set(
                status.conditions,
                KeydraConnectionStatus.REGISTERED,
                true,
                "Registered",
                "Profile " + status.profileId + " on " + resource.getSpec().keydraRef + ".",
                resource.getMetadata().getGeneration());

        probe(instance, status, resource);

        resource.setStatus(status);
        return UpdateControl.patchStatus(resource);
    }

    @Override
    public DeleteControl cleanup(KeydraConnection resource, Context<KeydraConnection> context) {
        KeydraConnectionStatus status = resource.getStatus();
        if (status == null || status.profileId == null) {
            return DeleteControl.defaultDelete();
        }
        KeydraApi.Instance instance;
        try {
            instance = instanceFor(resource);
        } catch (NotReadyException gone) {
            // The installation this belonged to is itself being deleted. There is nothing left to
            // remove the profile from, and holding the finalizer would leave the resource stuck
            // for ever waiting on something that will never come back.
            LOG.infof(
                    "Letting %s go without removing profile %d: %s",
                    resource.getMetadata().getName(), status.profileId, gone.getMessage());
            return DeleteControl.defaultDelete();
        }
        long id = status.profileId;
        try (Response ignored =
                api.call(instance, cookie -> api.endpoint(instance).delete(cookie, id))) {
            LOG.infof("Removed profile %d from %s", id, instance.baseUri());
            return DeleteControl.defaultDelete();
        } catch (WebApplicationException failure) {
            if (failure.getResponse().getStatus() == 404) {
                return DeleteControl.defaultDelete();
            }
            throw failure;
        }
    }

    @Override
    public ErrorStatusUpdateControl<KeydraConnection> updateErrorStatus(
            KeydraConnection resource, Context<KeydraConnection> context, Exception exception) {
        KeydraConnectionStatus status =
                resource.getStatus() == null ? new KeydraConnectionStatus() : resource.getStatus();
        if (status.conditions == null) {
            status.conditions = new ArrayList<>();
        }
        Conditions.set(
                status.conditions,
                KeydraConnectionStatus.REGISTERED,
                false,
                "ReconciliationFailed",
                String.valueOf(exception.getMessage()),
                resource.getMetadata().getGeneration());
        resource.setStatus(status);
        return ErrorStatusUpdateControl.patchStatus(resource);
    }

    /**
     * Asks the instance what the target said about itself.
     *
     * <p>A probe that fails is not a reconciliation that failed. The profile is registered — which
     * is what this resource asked for — and whether the server behind it happens to be up is a
     * second, separate answer. Conflating them would make a resource go red because somebody
     * restarted a Redis.
     */
    private void probe(
            KeydraApi.Instance instance, KeydraConnectionStatus status, KeydraConnection resource) {
        long id = status.profileId;
        try {
            ConnectionStatus probed =
                    api.call(instance, cookie -> api.endpoint(instance).probe(cookie, id));
            boolean up = "UP".equals(probed.state());
            if (probed.server() != null) {
                status.flavor = probed.server().flavor();
                status.serverVersion = probed.server().version();
            }
            Conditions.set(
                    status.conditions,
                    KeydraConnectionStatus.REACHABLE,
                    up,
                    up ? "Answered" : "NoAnswer",
                    up
                            ? describe(status)
                            : Optional.ofNullable(probed.message())
                                    .orElse("The target did not answer."),
                    resource.getMetadata().getGeneration());
        } catch (RuntimeException failure) {
            Conditions.set(
                    status.conditions,
                    KeydraConnectionStatus.REACHABLE,
                    false,
                    "ProbeFailed",
                    "The instance could not be asked to probe this target: " + failure.getMessage(),
                    resource.getMetadata().getGeneration());
        }
    }

    private static String describe(KeydraConnectionStatus status) {
        if (status.flavor == null) {
            return "The target answered.";
        }
        return status.serverVersion == null
                ? status.flavor + " answered."
                : status.flavor + " " + status.serverVersion + " answered.";
    }

    /** Where the instance is, and who to be when talking to it. */
    private KeydraApi.Instance instanceFor(KeydraConnection resource) {
        String namespace = resource.getMetadata().getNamespace();
        String reference = resource.getSpec().keydraRef;

        Keydra keydra =
                client.resources(Keydra.class).inNamespace(namespace).withName(reference).get();
        if (keydra == null) {
            throw new NotReadyException(
                    "No Keydra called \""
                            + reference
                            + "\" in this namespace. A connection names the installation it belongs"
                            + " to, and cross-namespace is deliberately not possible.");
        }
        var account = keydra.getSpec().apiAccount;
        if (account == null || !Names.isSet(account.secretName)) {
            throw new NotReadyException(
                    "Keydra \""
                            + reference
                            + "\" declares no apiAccount, so the operator has no way to sign in to"
                            + " it. Create an administrator account for the operator, put its name"
                            + " and password in a Secret, and name that Secret in"
                            + " spec.apiAccount.secretName.");
        }
        Secret secret = client.secrets().inNamespace(namespace).withName(account.secretName).get();
        if (secret == null) {
            throw new NotReadyException(
                    "Secret \"" + account.secretName + "\" does not exist in this namespace.");
        }
        String username = read(secret, account.usernameKey);
        String password = read(secret, account.passwordKey);
        if (username == null || password == null) {
            throw new NotReadyException(
                    "Secret \""
                            + account.secretName
                            + "\" is missing "
                            + (username == null ? account.usernameKey : account.passwordKey)
                            + ".");
        }

        String base =
                "http://"
                        + keydra.getMetadata().getName()
                        + "."
                        + namespace
                        + ".svc:"
                        + keydra.getSpec().service.port;
        return new KeydraApi.Instance(base, username, password);
    }

    /** Builds the profile the way the API takes it. */
    private ConnectionRequest requestFor(KeydraConnection resource) {
        KeydraConnectionSpec spec = resource.getSpec();
        String name =
                Names.isSet(spec.profileName) ? spec.profileName : resource.getMetadata().getName();
        return new ConnectionRequest(
                name,
                spec.host,
                spec.port,
                spec.username,
                resolve(resource, spec.passwordSecret),
                Boolean.TRUE.equals(spec.tls),
                spec.tlsCaCert,
                spec.tlsClientCert,
                resolve(resource, spec.tlsClientKeySecret),
                resolve(resource, spec.tlsClientKeyPassphraseSecret),
                Boolean.TRUE.equals(spec.guarded),
                Boolean.TRUE.equals(spec.requiresApproval),
                spec.consoleAllowed,
                spec.database == null ? 0 : spec.database,
                spec.engine.name(),
                spec.type.name(),
                spec.sentinelMasterName,
                spec.namespace,
                spec.notes);
    }

    /** Reads one key out of one Secret, or nothing where the spec named none. */
    private String resolve(KeydraConnection resource, SecretKeySelector selector) {
        if (selector == null || !Names.isSet(selector.getName())) {
            return null;
        }
        Secret secret =
                client.secrets()
                        .inNamespace(resource.getMetadata().getNamespace())
                        .withName(selector.getName())
                        .get();
        if (secret == null) {
            throw new NotReadyException(
                    "Secret \"" + selector.getName() + "\" does not exist in this namespace.");
        }
        String value = read(secret, selector.getKey());
        if (value == null && !Boolean.TRUE.equals(selector.getOptional())) {
            throw new NotReadyException(
                    "Secret \"" + selector.getName() + "\" is missing " + selector.getKey() + ".");
        }
        return value;
    }

    private static String read(Secret secret, String key) {
        if (secret.getStringData() != null && secret.getStringData().containsKey(key)) {
            return secret.getStringData().get(key);
        }
        if (secret.getData() != null && secret.getData().containsKey(key)) {
            return new String(
                    Base64.getDecoder().decode(secret.getData().get(key)), StandardCharsets.UTF_8);
        }
        return null;
    }

    private UpdateControl<KeydraConnection> refuse(
            KeydraConnection resource, KeydraConnectionStatus status, String message) {
        Conditions.set(
                status.conditions,
                KeydraConnectionStatus.REGISTERED,
                false,
                "NotRegistered",
                message,
                resource.getMetadata().getGeneration());
        resource.setStatus(status);
        return UpdateControl.patchStatus(resource);
    }

    /**
     * Something the resource depends on is not there yet.
     *
     * <p>Distinct from a failure because the answer is different: a missing Secret is very often a
     * manifest applied in the order a person wrote it rather than the order things become ready, so
     * it becomes a condition somebody can read and a retry, not a stack trace.
     */
    static class NotReadyException extends RuntimeException {
        NotReadyException(String message) {
            super(message);
        }
    }
}
