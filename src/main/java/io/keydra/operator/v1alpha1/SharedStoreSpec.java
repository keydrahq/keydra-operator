package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * A RESP server Keydra uses for itself, so that what one instance does is announced to browsers
 * attached to the others.
 *
 * <p>Not one of the targets, and not the database. Raising {@code replicas} without this is still
 * correct — whichever instance holds the lease in the database does the schedules, the alert
 * decisions and the sampling — it is only the browser that notices, because a change made on one
 * instance does not reach a browser attached to another until it reloads.
 */
public class SharedStoreSpec {

    @JsonPropertyDescription("For example redis://keydra-store:6379.")
    public String url;
}
