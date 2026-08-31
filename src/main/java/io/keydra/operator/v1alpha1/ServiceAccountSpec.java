package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;

/**
 * The account the instance's pods run as.
 *
 * <p>Keydra talks to a database and to the servers somebody configured. It asks the API server for
 * nothing, so its token is not mounted — which is also why an installation needs no Role of its
 * own.
 */
public class ServiceAccountSpec {

    public Boolean create = true;

    @JsonPropertyDescription("Empty means one named after this resource.")
    public String name;

    public Map<String, String> annotations;
}
