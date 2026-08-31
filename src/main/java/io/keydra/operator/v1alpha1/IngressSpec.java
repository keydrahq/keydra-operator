package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import java.util.List;
import java.util.Map;

/**
 * An Ingress in front of whichever service serves the interface.
 *
 * <p>Which service that is differs by shape — the application's own port when standalone, nginx
 * when split — and the operator works it out rather than asking. What it never sends to is the
 * management port; see {@link ServiceSpec}.
 */
public class IngressSpec {

    public Boolean enabled = false;

    @JsonPropertyDescription("Empty uses the cluster's default IngressClass.")
    public String className;

    public Map<String, String> annotations;

    public List<IngressHost> hosts = List.of();

    public List<IngressTLS> tls;
}
