package io.keydra.operator.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Point-in-time health of one profile, as the instance last saw it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConnectionStatus(String state, String message, ServerInfo server) {}
