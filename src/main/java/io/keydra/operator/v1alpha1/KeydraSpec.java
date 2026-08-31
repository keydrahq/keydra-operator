package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.Affinity;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import java.util.List;
import java.util.Map;

/**
 * What an installation is.
 *
 * <p>The order below is the chart's and is deliberate: what an install cannot start without comes
 * first, then the settings where leaving the default alone looks exactly like setting it correctly,
 * then everything a deployment may or may not want.
 *
 * <p>Keydra reads fifty-two environment variables and this names about twenty. The rest have
 * defaults that are right, and a spec listing all of them would be configuration documentation with
 * a worse layout — {@link #extraEnv} reaches any of them, and what is set there wins over what the
 * operator computes, so it is also the way to override something the operator got wrong for you.
 */
// The chart refuses to render rather than install something that cannot work, and says why in a
// paragraph, because there is a person watching `helm install` do it. An operator's equivalent
// audience is the API server, and these are the same refusals expressed where it can make them:
// a spec that breaks one of these is rejected at `kubectl apply` with the sentence attached,
// which is earlier and louder than a Degraded condition somebody has to go and read.
@ValidationRule(
        value =
                "!has(self.proxy) || !has(self.proxy.enabled) || !self.proxy.enabled ||"
                        + " (has(self.proxy.trusted) && self.proxy.trusted != \"\")",
        message =
                "proxy.enabled is on and proxy.trusted names nobody. With the switch on and no"
                        + " proxies named, any client can claim any address — which is worse than not"
                        + " trusting the header at all, because the sign-in checks and the attempt limit"
                        + " both believe what they are told.")
@ValidationRule(
        value =
                "!(has(self.ingress) && has(self.ingress.enabled) && self.ingress.enabled &&"
                        + " has(self.route) && has(self.route.enabled) && self.route.enabled)",
        message =
                "ingress and route are both enabled. Two objects publishing one service under"
                        + " two hostnames is a way of ending up with a publicUrl that is right for one of"
                        + " them; pick the one the cluster actually uses.")
@ValidationRule(
        value =
                "!has(self.identityProvider) || !has(self.identityProvider.url) ||"
                        + " self.identityProvider.url == \"\" || (has(self.identityProvider.clientId) &&"
                        + " self.identityProvider.clientId != \"\")",
        message =
                "identityProvider.url names a provider and identityProvider.clientId is empty."
                        + " A provider is turned on by being named, so a half-named one is an instance"
                        + " that starts and then cannot complete a sign-in.")
public class KeydraSpec {

    // ---------------------------------------------------------------------------------
    // What an install cannot start without
    // ---------------------------------------------------------------------------------

    @Required public DatabaseSpec database = new DatabaseSpec();

    @Required public SecretSpec secret = new SecretSpec();

    // ---------------------------------------------------------------------------------
    // The ones a real deployment has to decide
    // ---------------------------------------------------------------------------------

    public ProxySpec proxy = new ProxySpec();

    @JsonPropertyDescription(
            "The address a browser reaches this at. A redirect URI is agreed with an identity"
                    + " provider in advance and has to match to the character, and it is what an"
                    + " invitation link is built from — so without it those links point at whatever"
                    + " the request happened to say, which behind a proxy is the proxy's idea of the"
                    + " host. Left empty with a Route enabled, the operator fills it in from the"
                    + " hostname OpenShift assigned.")
    public String publicUrl;

    @JsonPropertyDescription(
            "Session cookies are marked secure, which is right for https and wrong for a"
                    + " demonstration served over plain http — where the browser refuses to keep"
                    + " them and nobody stays signed in.")
    public Boolean cookieSecure = true;

    // ---------------------------------------------------------------------------------
    // The shape, and the images
    // ---------------------------------------------------------------------------------

    public DeploymentMode mode = DeploymentMode.standalone;

    public ImageSpec image = new ImageSpec();

    public List<LocalObjectReference> imagePullSecrets;

    public UiSpec ui = new UiSpec();

    // ---------------------------------------------------------------------------------
    // How many, and what makes more than one work
    // ---------------------------------------------------------------------------------

    @JsonPropertyDescription(
            "More than one instance against one database is supported: whichever holds the lease"
                    + " in the database does the schedules, the alert decisions and the sampling.")
    public Integer replicas = 1;

    public SharedStoreSpec sharedStore = new SharedStoreSpec();

    public DisruptionBudgetSpec podDisruptionBudget = new DisruptionBudgetSpec();

    // ---------------------------------------------------------------------------------
    // Getting to it
    // ---------------------------------------------------------------------------------

    public ServiceSpec service = new ServiceSpec();

    public IngressSpec ingress = new IngressSpec();

    public RouteSpec route = new RouteSpec();

    // ---------------------------------------------------------------------------------
    // Signing people in
    // ---------------------------------------------------------------------------------

    public IdentityProviderSpec identityProvider = new IdentityProviderSpec();

    @JsonPropertyDescription(
            "On by default, and the default is the answer. Turned off, the interface says"
                    + " \"security off\" on every page — because an open instance that looks"
                    + " secured is how one ends up exposed.")
    public Boolean securityEnabled = true;

    @JsonPropertyDescription(
            "The account the operator signs in as to register targets declared as"
                    + " KeydraConnection resources. Leave it out where none are.")
    public ApiAccountSpec apiAccount = new ApiAccountSpec();

    // ---------------------------------------------------------------------------------
    // Storage, and everything else
    // ---------------------------------------------------------------------------------

    public BackupsSpec backups = new BackupsSpec();

    public MetricsHistorySpec metricsHistory = new MetricsHistorySpec();

    public MailSpec mail = new MailSpec();

    public ObservabilitySpec observability = new ObservabilitySpec();

    public ServiceMonitorSpec serviceMonitor = new ServiceMonitorSpec();

    @JsonPropertyDescription(
            "A memory limit is not optional in practice: the image sizes its heap as a percentage"
                    + " of what it can see, and with no limit that is the whole node.")
    public ResourceRequirements resources;

    public PodSecurityContext podSecurityContext;

    public SecurityContext securityContext;

    public ServiceAccountSpec serviceAccount = new ServiceAccountSpec();

    public ProbesSpec probes = new ProbesSpec();

    public Map<String, String> nodeSelector;
    public List<Toleration> tolerations;
    public Affinity affinity;
    public Map<String, String> podAnnotations;
    public Map<String, String> podLabels;
    public List<Volume> extraVolumes;
    public List<VolumeMount> extraVolumeMounts;

    @JsonPropertyDescription(
            "Any of the fifty-two, by name. What is set here wins over what the operator"
                    + " computes.")
    public List<EnvVar> extraEnv;
}
