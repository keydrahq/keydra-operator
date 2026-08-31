package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.Map;

/**
 * A ServiceMonitor for the Prometheus operator, scraping the management port.
 *
 * <p>Requires {@code monitoring.coreos.com/v1} on the cluster. Asked for where that is absent, the
 * operator reports it in a condition and installs everything else — a missing optional CRD is a
 * sentence rather than a failed installation.
 */
public class ServiceMonitorSpec {

    public Boolean enabled = false;

    public String interval = "30s";

    @JsonPropertyDescription(
            "Labels the Prometheus operator's serviceMonitorSelector is looking for.")
    public Map<String, String> labels;
}
