package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Whether there is something in front, and what it is.
 *
 * <p>Behind a reverse proxy, which under Kubernetes with an Ingress or a Route is always. Told
 * nothing, Keydra sees every sign-in as coming from the ingress controller: the checks that compare
 * a sign-in with the ones before it then compare everybody with everybody, and the limit on
 * attempts counts a whole office as one network.
 *
 * <p>Naming the proxies is not optional once the switch is on. With it on and nobody named, any
 * client can claim any address — which is worse than not trusting the header at all, because the
 * sign-in checks and the attempt limit both believe what they are told. The operator refuses that
 * combination rather than installing it.
 */
public class ProxySpec {

    @JsonPropertyDescription("Trust the forwarding headers of whatever sits in front.")
    public Boolean enabled = false;

    @JsonPropertyDescription(
            "The proxies whose headers are believed, as a comma-separated list of CIDRs — for"
                    + " example 10.0.0.0/8,192.168.0.0/16. Required when enabled is true.")
    public String trusted;
}
