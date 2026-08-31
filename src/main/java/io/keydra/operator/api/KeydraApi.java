package io.keydra.operator.api;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

/**
 * Talking to an instance, and staying signed in to it.
 *
 * <p>Keydra has no notion of a machine caller — no token, no service account — so this signs in the
 * way a browser does and keeps the cookies it is given. Two consequences are designed for rather
 * than worked around.
 *
 * <p><b>A session ends without warning.</b> It expires on its schedule, and it ends immediately
 * when somebody sets that account's password or ends its sessions from the instance. So a 401 is
 * not an error: it is the signal to sign in again and make the call a second time. Only a second
 * 401 is a failure, because a loop that re-authenticates on every 401 for ever is a loop that
 * hammers a throttle designed to slow down exactly that.
 *
 * <p><b>Signing in is expensive on purpose.</b> Argon2 is slow by design and the instance counts
 * attempts per account and per network. So a session is held per instance and reused for every
 * resource pointing at it, rather than being established per reconciliation — which with a dozen
 * targets and a retry loop would look, correctly, like a machine guessing passwords.
 */
@ApplicationScoped
public class KeydraApi {

    private static final Logger LOG = Logger.getLogger(KeydraApi.class);

    private final Map<String, KeydraEndpoint> endpoints = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    /** A signed-in call, retried once against a session that has ended. */
    public <T> T call(Instance instance, Function<String, T> call) {
        String cookie = sessions.computeIfAbsent(instance.key(), key -> signIn(instance));
        try {
            return call.apply(cookie);
        } catch (WebApplicationException failure) {
            if (failure.getResponse().getStatus() != 401) {
                throw failure;
            }
            LOG.debugf("The session for %s had ended; signing in again", instance.baseUri());
            sessions.remove(instance.key());
            String renewed = sessions.computeIfAbsent(instance.key(), key -> signIn(instance));
            return call.apply(renewed);
        }
    }

    public KeydraEndpoint endpoint(Instance instance) {
        return endpoints.computeIfAbsent(
                instance.baseUri(),
                uri ->
                        QuarkusRestClientBuilder.newBuilder()
                                .baseUri(URI.create(uri))
                                .build(KeydraEndpoint.class));
    }

    /** Drops the held session, so the next call establishes a new one. */
    public void forget(Instance instance) {
        sessions.remove(instance.key());
    }

    private String signIn(Instance instance) {
        try (Response response =
                endpoint(instance).signIn(instance.username(), instance.password())) {
            if (response.getStatus() >= 400) {
                throw new SignInRefusedException(
                        "The instance at "
                                + instance.baseUri()
                                + " refused the account \""
                                + instance.username()
                                + "\" with "
                                + response.getStatus()
                                + ". Registering a target is an administrator's permission, so the"
                                + " account has to hold that role, and a repeated refusal counts"
                                + " against the same limit a person's would.");
            }
            List<String> setCookie = response.getStringHeaders().get("Set-Cookie");
            if (setCookie == null || setCookie.isEmpty()) {
                throw new SignInRefusedException(
                        "The instance at "
                                + instance.baseUri()
                                + " accepted the sign-in and set no cookie. Something is answering"
                                + " on that address that is not a Keydra.");
            }
            // Both of them: the framework's cookie says which account, and keydra_sid names the
            // session row that can be ended. A request carrying only the first is refused.
            return setCookie.stream()
                    .map(header -> header.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
        }
    }

    /** One instance, and the account to reach it as. */
    public record Instance(String baseUri, String username, String password) {

        /**
         * What a held session is filed under.
         *
         * <p>The password is part of it, so that rotating the Secret invalidates the held session
         * rather than leaving the operator using a credential that has been withdrawn.
         */
        String key() {
            return baseUri + " " + username + " " + password.hashCode();
        }
    }

    /** The account was refused, which is not a thing retrying differently will fix. */
    public static class SignInRefusedException extends RuntimeException {
        public SignInRefusedException(String message) {
            super(message);
        }
    }
}
