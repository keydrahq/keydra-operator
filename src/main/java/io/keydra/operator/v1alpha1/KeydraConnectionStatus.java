package io.keydra.operator.v1alpha1;

import io.fabric8.kubernetes.api.model.Condition;
import java.util.ArrayList;
import java.util.List;

/**
 * What became of the declaration.
 *
 * <p>{@link #profileId} is the load-bearing field and not a convenience. It is how the reconciler
 * knows the difference between "this target has never been registered" and "the instance did not
 * answer just now" — without it, an instance that is briefly unreachable looks exactly like a
 * profile that was never created, and the second reconciliation writes a duplicate.
 */
public class KeydraConnectionStatus {

    /**
     * The {@code metadata.generation} this status was last written for.
     *
     * <p>Kept by hand rather than inherited: the framework dropped the base class that used to
     * carry it, and what it is for outlives that. A condition says what was true; this says what it
     * was true <em>of</em>, so a reader can tell a status that is stale from one that is bad news.
     */
    public Long observedGeneration;

    public static final String REGISTERED = "Registered";
    public static final String REACHABLE = "Reachable";

    public List<Condition> conditions = new ArrayList<>();

    /** The profile's id in Keydra's database, once there is one. */
    public Long profileId;

    /** What the target said about itself when it was last probed. */
    public String serverVersion;

    /** Which of Redis, Valkey, KeyDB and the rest answered, as the instance worked it out. */
    public String flavor;
}
