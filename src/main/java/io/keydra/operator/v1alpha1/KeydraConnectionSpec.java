package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.generator.annotation.Max;
import io.fabric8.generator.annotation.Min;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.ValidationRule;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import java.util.List;

/** What target this is, and which Keydra is to be told about it. */
@ValidationRule(
        value =
                "!has(self.type) || self.type != \"SENTINEL\" ||"
                        + " (has(self.sentinelMasterName) && self.sentinelMasterName != \"\")",
        message =
                "type is SENTINEL and sentinelMasterName is empty. A sentinel is asked which"
                        + " server it is watching by name; without one there is nothing to ask it for.")
@ValidationRule(
        value =
                "!has(self.engine) || self.engine != \"AEROSPIKE\" ||"
                        + " (has(self.namespace) && self.namespace != \"\")",
        message =
                "engine is AEROSPIKE and namespace is empty. An Aerospike record is identified"
                        + " by a namespace, a set and a key; the first of those is not optional.")
public class KeydraConnectionSpec {

    @Required
    @JsonPropertyDescription(
            "The Keydra resource in this namespace that should hold this profile. Cross-namespace"
                    + " is deliberately not possible: it would let anybody who can create a resource"
                    + " in their own namespace add a target to somebody else's console.")
    public String keydraRef;

    @JsonPropertyDescription(
            "The profile's name in Keydra. Empty uses this resource's own name, which is the"
                    + " right default — two names for one thing is two names to keep in step.")
    public String profileName;

    @Required public String host;

    @Required
    @Min(1)
    @Max(65535)
    public Integer port;

    public String username;

    @JsonPropertyDescription(
            "Where the password is. A reference and never a value: a password in a custom resource"
                    + " is readable by anybody who can read the resource.")
    public SecretKeySelector passwordSecret;

    public Boolean tls = false;

    @JsonPropertyDescription(
            "The authority to trust for this target, as PEM. Empty leaves the JVM's own store"
                    + " applying.")
    public String tlsCaCert;

    @JsonPropertyDescription("The certificate to present when the target asks for one, as PEM.")
    public String tlsClientCert;

    @JsonPropertyDescription("Where its private half is. A reference, for the same reason.")
    public SecretKeySelector tlsClientKeySecret;

    @JsonPropertyDescription("Where the passphrase that opens that key is, if it has one.")
    public SecretKeySelector tlsClientKeyPassphraseSecret;

    @JsonPropertyDescription(
            "Whether an operation that could empty this target has to name it first. Off by"
                    + " default and never inferred: a target is not production because its name"
                    + " says so.")
    public Boolean guarded = false;

    @JsonPropertyDescription(
            "Whether an operation that could empty this target waits for a second person. Beside"
                    + " the naming rather than inside it: one asks which server this is and the other"
                    + " asks whether it should happen at all.")
    public Boolean requiresApproval = false;

    @JsonPropertyDescription(
            "Commands the console may run on this target that it refuses elsewhere. Only the ones"
                    + " refused because of what they do to the target can be named; the ones refused"
                    + " because of what they would do to Keydra's own connection are the same"
                    + " everywhere.")
    public List<String> consoleAllowed;

    @Min(0)
    @JsonPropertyDescription("Which numbered database, where the store has them.")
    public Integer database = 0;

    public EngineKind engine = EngineKind.RESP;

    public TopologyKind type = TopologyKind.STANDALONE;

    @JsonPropertyDescription("Required when type is SENTINEL.")
    public String sentinelMasterName;

    @JsonPropertyDescription("Aerospike's namespace. Not this resource's Kubernetes namespace.")
    public String namespace;

    public String notes;
}
