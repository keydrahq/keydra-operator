package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import java.util.List;
import java.util.Map;

/**
 * The values a spec that says nothing gets.
 *
 * <p>They are the chart's, and the two security contexts are worth reading rather than skipping.
 * Nothing here needs root, and the image already runs as uid 185 in group 0 — which is what lets a
 * platform assign it an arbitrary uid instead, the way OpenShift does. A spec that supplies its own
 * replaces these wholesale rather than merging with them: a half-overridden security context is a
 * thing whose actual value nobody can read off either document.
 */
final class Defaults {

    private Defaults() {}

    static PodSecurityContext podSecurityContext() {
        return new PodSecurityContextBuilder()
                .withRunAsNonRoot(true)
                .withNewSeccompProfile()
                .withType("RuntimeDefault")
                .endSeccompProfile()
                .build();
    }

    static SecurityContext containerSecurityContext() {
        return new SecurityContextBuilder()
                .withAllowPrivilegeEscalation(false)
                .withReadOnlyRootFilesystem(false)
                .withNewCapabilities()
                .withDrop(List.of("ALL"))
                .endCapabilities()
                .build();
    }

    /**
     * A ceiling, because the image sizes its heap as a percentage of what it can see. With no limit
     * that is the whole node.
     */
    static ResourceRequirements applicationResources() {
        return new ResourceRequirementsBuilder()
                .withRequests(Map.of("memory", new Quantity("512Mi"), "cpu", new Quantity("250m")))
                .withLimits(Map.of("memory", new Quantity("2Gi")))
                .build();
    }

    static ResourceRequirements uiResources() {
        return new ResourceRequirementsBuilder()
                .withRequests(Map.of("memory", new Quantity("64Mi"), "cpu", new Quantity("50m")))
                .withLimits(Map.of("memory", new Quantity("256Mi")))
                .build();
    }
}
