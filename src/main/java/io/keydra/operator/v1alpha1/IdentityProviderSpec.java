package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * An OIDC provider, if there is one.
 *
 * <p>Left empty, Keydra has accounts of its own: the first administrator is created through {@code
 * /api/v1/auth/setup} on an instance that has none, and after that it is passwords this application
 * hashes and sessions it issues.
 *
 * <p>Naming a provider turns it on, because you named it — not because a second flag says so.
 */
public class IdentityProviderSpec {

    @JsonPropertyDescription("Issuer URL, for example https://keycloak.example.com/realms/keydra.")
    public String url;

    public String clientId;

    @JsonPropertyDescription(
            "Which claim carries viewer/operator/admin. Empty uses the provider's default"
                    + " arrangement.")
    public String rolesClaim;
}
