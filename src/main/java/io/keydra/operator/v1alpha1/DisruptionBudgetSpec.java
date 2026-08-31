package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** How much of the installation a voluntary disruption may take at once. */
public class DisruptionBudgetSpec {

    public Boolean enabled = false;

    @JsonPropertyDescription("How many instances have to stay up while a node is drained.")
    public Integer minAvailable = 1;
}
