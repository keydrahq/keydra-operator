package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;

/**
 * Keydra's own PostgreSQL.
 *
 * <p>Not one of the servers it manages: this holds the connection profiles, the accounts, the
 * grants, the audit log, the schedules and the rules.
 *
 * <p>The operator does not install one, and that is a decision rather than an omission. What lives
 * here is everything Keydra knows, and a database an application's operator brings up beside itself
 * is a database nobody is backing up — running on a pod whose replacement is what an upgrade is.
 * Point this at a PostgreSQL somebody operates.
 */
public class DatabaseSpec {

    @Required
    @JsonPropertyDescription(
            "Reactive PostgreSQL, so postgresql://host:port/name — no jdbc: prefix. Required: an"
                    + " installation with no database starts, finds it cannot answer a request, and"
                    + " says so one request at a time.")
    public String url;

    @JsonPropertyDescription("The role Keydra connects as.")
    public String username = "keydra";
}
