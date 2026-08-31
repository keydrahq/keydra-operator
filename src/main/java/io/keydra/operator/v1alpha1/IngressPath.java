package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** One path under a hostname. */
public class IngressPath {

    public String path = "/";

    @JsonPropertyDescription("Prefix, Exact or ImplementationSpecific.")
    public String pathType = "Prefix";
}
