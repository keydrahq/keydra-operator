package io.keydra.operator.v1alpha1;

import io.fabric8.kubernetes.api.model.Condition;
import java.util.ArrayList;
import java.util.List;

/**
 * What the installation is doing, in the vocabulary a cluster already reads.
 *
 * <p>Three conditions and no more, because three is what the platform's own controllers publish and
 * what {@code oc status} and OLM both know how to render:
 *
 * <ul>
 *   <li>{@code Available} — at least one instance is ready and answering its readiness endpoint.
 *   <li>{@code Progressing} — something is being rolled out, or is waiting for something it was
 *       told to expect.
 *   <li>{@code Degraded} — the operator has installed what it can and something is wrong that it
 *       cannot fix by trying again. A named Secret that is not there, a Route asked for on a
 *       cluster with no Routes, a spec that contradicts itself.
 * </ul>
 *
 * <p>A refusal is a {@code Degraded} condition rather than a thrown reconciliation. The chart could
 * refuse to render because there was a person watching it do so; an operator's equivalent audience
 * is {@code kubectl describe}, and an exception that only reaches the manager's log is a refusal
 * nobody reads.
 */
public class KeydraStatus {

    /**
     * The {@code metadata.generation} this status was last written for.
     *
     * <p>Kept by hand rather than inherited: the framework dropped the base class that used to
     * carry it, and what it is for outlives that. A condition says what was true; this says what it
     * was true <em>of</em>, so a reader can tell a status that is stale from one that is bad news.
     */
    public Long observedGeneration;

    /** Condition types, so nothing spells one differently in two places. */
    public static final String AVAILABLE = "Available";

    public static final String PROGRESSING = "Progressing";
    public static final String DEGRADED = "Degraded";

    public List<Condition> conditions = new ArrayList<>();

    /**
     * Where a browser reaches this, once something publishes it.
     *
     * <p>Taken from the Route's assigned hostname or the first Ingress host. Empty where neither is
     * enabled — an installation reachable only from inside the cluster has no such address, and
     * inventing one from the Service name would be answering a different question.
     */
    public String url;

    /** How many instances are ready, out of how many were asked for. */
    public Integer readyReplicas;

    /**
     * The image that is actually running, as the Deployment names it.
     *
     * <p>Reported rather than echoed from the spec: a spec naming a moving tag and a cluster
     * running what that tag meant last week are two different facts, and this is the second one.
     */
    public String image;
}
