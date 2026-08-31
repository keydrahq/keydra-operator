package io.keydra.operator.v1alpha1;

/**
 * Startup, readiness and liveness, asked separately.
 *
 * <p>A JVM applying migrations to an empty schema takes longer than a liveness probe should ever
 * wait, so the startup probe holds the other two off instead of their thresholds being widened to
 * cover a case that happens once.
 *
 * <p>Draining works through the readiness endpoint: an instance taken out of service from the
 * instances page reports itself unready, and the platform stops sending it work.
 */
public class ProbesSpec {

    public ProbeSpec startup = probe(30, 5, null);
    public ProbeSpec readiness = probe(null, 10, 5);
    public ProbeSpec liveness = probe(null, 30, 5);

    private static ProbeSpec probe(Integer failureThreshold, Integer period, Integer timeout) {
        ProbeSpec spec = new ProbeSpec();
        spec.failureThreshold = failureThreshold;
        spec.periodSeconds = period;
        spec.timeoutSeconds = timeout;
        return spec;
    }
}
