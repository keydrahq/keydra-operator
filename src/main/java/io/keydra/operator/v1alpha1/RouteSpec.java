package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;

/**
 * An OpenShift Route, which is what an Ingress is on the cluster this operator is most likely to be
 * installed on.
 *
 * <p>Offered beside the Ingress rather than instead of it, and never both: two objects publishing
 * one service under two hostnames is a way of ending up with a {@code publicUrl} that is right for
 * one of them. Turned on where the cluster has no Route CRD, the operator says so in a condition
 * rather than failing to reconcile — the answer to "this is not an OpenShift" is a sentence, not a
 * crash loop.
 *
 * <p>Edge termination by default, which is TLS at the router and plain HTTP inside the cluster.
 * That is the arrangement {@code cookieSecure} and {@code proxy.enabled} both assume.
 */
public class RouteSpec {

    public Boolean enabled = false;

    @JsonPropertyDescription("Empty lets OpenShift generate one from the name and the domain.")
    public String host;

    @JsonPropertyDescription("edge, passthrough or reencrypt.")
    public String termination = "edge";

    @JsonPropertyDescription("What a plain HTTP request gets: Allow, Redirect or None.")
    public String insecureEdgeTerminationPolicy = "Redirect";

    public Map<String, String> annotations;
}
