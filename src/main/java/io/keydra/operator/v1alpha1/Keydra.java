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
 * One Keydra installation.
 *
 * <p>The resource is the same thing the Helm chart installs, said as an object the cluster owns:
 * the Deployment, the Service, the interface's second Deployment when the shape calls for one, and
 * whichever of the Ingress, the Route, the claim, the budget and the ServiceMonitor the spec asks
 * for. What the chart expressed as values, this expresses as a spec, and the two are deliberately
 * the same vocabulary — a person moving from one to the other should be renaming fields rather than
 * learning a second model.
 *
 * <p>One difference is not cosmetic and is the reason to read {@link SecretSpec} before installing:
 * <b>the operator never writes the Secret.</b> The chart has a fallback that renders one from
 * values, which is a convenience with a cost — the key ends up in the release. Here it would end up
 * in a custom resource, which is a thing anybody holding {@code get keydra} can read and which no
 * amount of RBAC on Secrets protects. So the key and the database password are named, never
 * supplied, and a spec that names neither is refused while somebody is still looking at it rather
 * than at a CrashLoopBackOff.
 *
 * <p>The name of this resource is the name of everything it creates. There is no equivalent of the
 * chart's {@code nameOverride} pair: a release name and a chart name are two things Helm has to
 * reconcile, and a custom resource has one name already.
 */
@Group("keydra.io")
@Version("v1alpha1")
@Kind("Keydra")
@Plural("keydras")
@ShortNames({"kd"})
// What `kubectl get keydra` shows without -o yaml. Four columns and no more: the question
// somebody runs that command to answer is "is it up, and where is it", and a table wide enough
// to wrap is a table nobody reads.
@AdditionalPrinterColumn(
        name = "Ready",
        jsonPath = ".status.conditions[?(@.type==\"Available\")].status",
        type = Type.STRING)
@AdditionalPrinterColumn(name = "Replicas", jsonPath = ".status.readyReplicas", type = Type.INTEGER)
@AdditionalPrinterColumn(name = "URL", jsonPath = ".status.url", type = Type.STRING)
@AdditionalPrinterColumn(name = "Age", jsonPath = ".metadata.creationTimestamp", type = Type.DATE)
public class Keydra extends CustomResource<KeydraSpec, KeydraStatus> implements Namespaced {

    @Override
    protected KeydraSpec initSpec() {
        return new KeydraSpec();
    }

    @Override
    protected KeydraStatus initStatus() {
        return new KeydraStatus();
    }
}
