package io.keydra.operator.v1alpha1;

/** One probe's timings. */
public class ProbeSpec {

    public Integer failureThreshold;
    public Integer periodSeconds;
    public Integer timeoutSeconds;
}
