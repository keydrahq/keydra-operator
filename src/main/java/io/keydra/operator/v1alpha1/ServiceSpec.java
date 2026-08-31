package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;

/**
 * The two ports, and what kind of Service carries them.
 *
 * <p>They are two ports for a reason rather than for tidiness. Health, readiness and metrics answer
 * without a session — which is what a scraper and a scheduler need — and what they say is which
 * instances are running, how many targets there are and how much work is moving: a map of the
 * installation, handed to anybody who can reach the port. So the Ingress and the Route publish
 * {@code port} and never {@code managementPort}, and the ServiceMonitor scrapes the second one from
 * inside the cluster where it already is.
 */
public class ServiceSpec {

    @JsonPropertyDescription("ClusterIP, NodePort or LoadBalancer.")
    public String type = "ClusterIP";

    @JsonPropertyDescription("The interface and the API.")
    public Integer port = 8181;

    @JsonPropertyDescription("Health, readiness and metrics. Never published outside the cluster.")
    public Integer managementPort = 9001;

    public Map<String, String> annotations;
}
