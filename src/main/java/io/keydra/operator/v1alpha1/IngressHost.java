package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import java.util.List;

/** One hostname an Ingress answers on, and the paths under it. */
public class IngressHost {

    @Required
    @JsonPropertyDescription("For example keydra.example.com.")
    public String host;

    public List<IngressPath> paths = List.of(new IngressPath());
}
