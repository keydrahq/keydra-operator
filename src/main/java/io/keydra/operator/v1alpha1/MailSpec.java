package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Where invitations, alert mail and the rest are posted. */
public class MailSpec {

    public String host;

    public Integer port = 587;

    public Boolean tls = true;

    @JsonPropertyDescription("The address the mail comes from.")
    public String from;

    public String username;

    @JsonPropertyDescription(
            "Key in the Secret named by spec.secret holding the API key or password.")
    public String apiKeySecretKey;
}
