package io.keydra.operator.v1alpha1;

import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn;
import io.fabric8.crd.generator.annotation.AdditionalPrinterColumn.Type;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Kind;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * A target, declared as a resource instead of typed into a form.
 *
 * <p>This is the part of the operator that is not a chart with a different syntax. A Redis or a
 * Valkey that a cluster already knows about — because something else in the cluster created it —
 * can be handed to Keydra by the same manifest that created it, and taken away by the same
 * deletion. A profile that exists because a resource says so also stops existing when the resource
 * does, which is the property a form can never have.
 *
 * <p>It reconciles against Keydra's HTTP API rather than against the API server, and that shapes
 * everything about it. There is no Deployment to own and no owner reference to rely on: what it
 * holds is a row in somebody else's database, reached by signing in as the account named in {@link
 * ApiAccountSpec}. So the reconciler is careful in ways a Kubernetes-only one does not have to be —
 * it records the id it created in the status and uses it thereafter, it treats an unreachable
 * instance as a retry rather than as a reason to create a second profile, and it will not adopt a
 * profile of the same name it did not create.
 *
 * <p>The password is a Secret reference and never a field. Everything else about a target is
 * public: which host, which port, which database, whether it is guarded. The one thing that is not
 * is the one thing that must not be inline.
 */
@Group("keydra.io")
@Version("v1alpha1")
@Kind("KeydraConnection")
@Plural("keydraconnections")
@ShortNames({"kdc"})
@AdditionalPrinterColumn(name = "Keydra", jsonPath = ".spec.keydraRef", type = Type.STRING)
@AdditionalPrinterColumn(name = "Target", jsonPath = ".spec.host", type = Type.STRING)
@AdditionalPrinterColumn(
        name = "Registered",
        jsonPath = ".status.conditions[?(@.type==\"Registered\")].status",
        type = Type.STRING)
@AdditionalPrinterColumn(name = "Flavor", jsonPath = ".status.flavor", type = Type.STRING)
@AdditionalPrinterColumn(name = "Age", jsonPath = ".metadata.creationTimestamp", type = Type.DATE)
public class KeydraConnection extends CustomResource<KeydraConnectionSpec, KeydraConnectionStatus>
        implements Namespaced {

    @Override
    protected KeydraConnectionSpec initSpec() {
        return new KeydraConnectionSpec();
    }

    @Override
    protected KeydraConnectionStatus initStatus() {
        return new KeydraConnectionStatus();
    }
}
