package io.keydra.operator.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** What answered, as the instance worked it out from what the server said about itself. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerInfo(String flavor, String version, String mode) {}
