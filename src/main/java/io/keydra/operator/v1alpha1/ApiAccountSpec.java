package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The account the operator signs in as when a {@link KeydraConnection} asks it to register a
 * target.
 *
 * <p>Only read by that reconciler. An installation that declares no targets as resources needs none
 * of this, and leaving it out is the right thing rather than an omission: an operator that holds an
 * administrator's password on a cluster where nobody asked it to is holding it for no reason.
 *
 * <p>Two things about it are worth saying plainly.
 *
 * <p><b>It is an ordinary account, signed in the ordinary way.</b> Keydra has no notion of a
 * machine caller today — no token, no service account — so the operator posts a username and a
 * password to {@code /api/v1/auth/login} and keeps the session cookie it gets back, exactly as a
 * browser would. That works, and it has consequences somebody should agree to before turning it on:
 * the account appears in the audit log as the author of every profile the operator writes, its
 * sign-ins are counted by the same throttle as a person's, and the session it holds expires on the
 * same schedule. A first-class API credential is the right answer and is a change to Keydra rather
 * than to this operator; until there is one, this is the honest arrangement rather than a hidden
 * one.
 *
 * <p><b>It has to be an administrator.</b> Writing a connection profile is {@code
 * CONNECTION_CREATE}, which is an administrator's permission — so an operator that can declare
 * targets can do everything an administrator can. Give it its own account rather than a person's,
 * so the audit log says which changes were somebody typing and which were a resource being applied.
 */
public class ApiAccountSpec {

    @JsonPropertyDescription(
            "Name of a Secret in this namespace holding the credentials. Absent means this"
                    + " installation registers no targets from resources.")
    public String secretName;

    @JsonPropertyDescription("Key holding the account name.")
    public String usernameKey = "api-username";

    @JsonPropertyDescription("Key holding its password.")
    public String passwordKey = "api-password";
}
