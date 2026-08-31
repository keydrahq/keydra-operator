package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;

/**
 * The Secret the installation reads its credentials from.
 *
 * <p>Named, never supplied. The chart has a fallback that writes one from values; there is no
 * equivalent here on purpose. A key written into a custom resource is readable by anybody who can
 * {@code get keydra} in the namespace, which is a wider audience than anybody who can read Secrets,
 * and it is the audience least likely to have been thought about.
 *
 * <p>The key it names encrypts every stored target password and tunnel key. Losing it means losing
 * them; sharing it means sharing them. The operator will not generate one for the same reason the
 * chart will not: a generated key is regenerated on the next reconciliation unless something
 * remembers it, and the failure that produces is not an error — it is an instance that starts,
 * finds it cannot read a single stored credential, and reports it one target at a time.
 *
 * <pre>
 * kubectl create secret generic keydra \
 *   --from-literal=secret-key="$(openssl rand -base64 32)" \
 *   --from-literal=database-password='...'
 * </pre>
 */
public class SecretSpec {

    @Required
    @JsonPropertyDescription(
            "Name of a Secret in this namespace. It has to exist before the instance can start,"
                    + " and the operator neither creates nor edits it.")
    public String name;

    @JsonPropertyDescription("Key holding the AES key that encrypts stored target credentials.")
    public String secretKeyKey = "secret-key";

    @JsonPropertyDescription("Key holding the password for the database role.")
    public String databasePasswordKey = "database-password";

    @JsonPropertyDescription(
            "Key holding the OIDC client secret. Read only when identityProvider.url is set.")
    public String oidcSecretKey = "oidc-secret";
}
