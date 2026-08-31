package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** What leaves the instance for somebody else to read. */
public class ObservabilitySpec {

    @JsonPropertyDescription("Where traces go, for example http://tempo:4317. Empty sends none.")
    public String otlpEndpoint;

    @JsonPropertyDescription("JSON on the console, which is what a log shipper reads.")
    public Boolean jsonLogs = true;

    @JsonPropertyDescription(
            "A line per request. Off by default: a request path can hold a key"
                    + " name, and a key name is the contents of somebody's target.")
    public Boolean accessLog = false;
}
