package io.keydra.operator.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * What the API says back.
 *
 * <p>Deliberately partial. The response carries a dozen fields about certificates, tunnels and
 * console policy that this operator has no use for, and a record that named them all would be a
 * record that breaks when one of them is added. {@code ignoreUnknown} is the contract: the operator
 * reads the three things it needs and is indifferent to the rest.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectionResponse(Long id, String name, ConnectionStatus status) {}
