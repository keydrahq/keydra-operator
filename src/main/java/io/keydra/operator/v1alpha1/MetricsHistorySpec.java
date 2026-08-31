package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Where readings are kept when they are kept.
 *
 * <p>What an alert rule written against what a metric read last week needs. Without it a rule can
 * still be written against a number, and one written against the past is refused while it is being
 * written rather than silently turned into something else.
 */
public class MetricsHistorySpec {

    @JsonPropertyDescription("ClickHouse, for example http://clickhouse:8123.")
    public String url;

    public String username;

    @JsonPropertyDescription(
            "Key in the Secret named by spec.secret holding the password. The value itself is not"
                    + " taken here — a password in a custom resource is a password in etcd that"
                    + " anybody who can read the resource can read.")
    public String passwordSecretKey;
}
