package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Toleration;
import java.util.List;
import java.util.Map;

/** The interface's own deployment. Read only when {@code mode} is {@code split}. */
public class UiSpec {

    @JsonPropertyDescription("Empty means quay.io/keydrahq/keydra-ui.")
    public ImageSpec image = new ImageSpec();

    @JsonPropertyDescription("How many copies of the static files there are.")
    public Integer replicas = 1;

    @JsonPropertyDescription("The port the nginx serves on, and the one the Ingress sends to.")
    public Integer servicePort = 8080;

    public ResourceRequirements resources;

    public Map<String, String> podAnnotations;
    public Map<String, String> nodeSelector;
    public List<Toleration> tolerations;
    public Affinity affinity;
}
