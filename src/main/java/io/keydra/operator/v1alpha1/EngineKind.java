package io.keydra.operator.v1alpha1;

/**
 * Which protocol the target speaks.
 *
 * <p>The constant names a protocol, not a product. Redis, Valkey, KeyDB, DragonflyDB and Garnet all
 * share {@code RESP} and are told apart at runtime by what they answer, not by configuration. A
 * store with a genuinely different protocol gets its own constant.
 *
 * <p>{@code TIKV} is not in the published image — its client is an uber-jar carrying dozens of
 * advisories nothing can upgrade, so it lives behind a Maven profile. Naming it here on an
 * installation running the ordinary image gets a refusal that says so, which is better than a
 * profile that saves and never connects.
 */
public enum EngineKind {
    RESP,
    AEROSPIKE,
    TIKV
}
