package io.keydra.operator.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * A profile, in the shape {@code POST /api/v1/connections} takes.
 *
 * <p>A copy of the application's record rather than a shared artefact, and that is the right way
 * round: an operator that depended on the backend's jar would be an operator that has to be
 * released whenever the backend is, for a contract that is HTTP and is versioned as HTTP. What
 * keeps the two honest is {@code /api/v1} — a field this omits is a field the API treats as absent,
 * which is exactly what the API promises for a v1 request.
 *
 * <p>{@code null} is not the same as empty here and the difference is the API's, not this record's:
 * an absent password leaves the stored one alone, an empty one clears it. The operator always sends
 * what the Secret says, so a rotated Secret is a rotated credential.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConnectionRequest(
        String name,
        String host,
        int port,
        String username,
        String password,
        boolean tls,
        String tlsCaCert,
        String tlsClientCert,
        String tlsClientKey,
        String tlsClientKeyPassphrase,
        boolean guarded,
        boolean requiresApproval,
        List<String> consoleAllowed,
        int database,
        String engine,
        String type,
        String sentinelMasterName,
        String namespace,
        String notes) {}
