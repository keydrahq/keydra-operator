package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDNoGCKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.BackupsSpec;
import io.keydra.operator.v1alpha1.Keydra;
import java.util.Map;

/**
 * Where a local backup destination writes.
 *
 * <p>Deliberately not garbage collected — no owner reference, which is the whole point of the class
 * this extends. A backup that is deleted along with the thing it was taken against is not a backup,
 * and deleting the installation is exactly the moment somebody would want one. The chart says the
 * same thing with {@code helm.sh/resource-policy: keep}; here it is the absence of a reference,
 * which is stronger because nothing can override it.
 *
 * <p>The claim it leaves behind is somebody's to remove, and the operator says so when the
 * installation is deleted rather than removing it quietly.
 */
@KubernetesDependent
public class BackupClaimDependent
        extends CRUDNoGCKubernetesDependentResource<PersistentVolumeClaim, Keydra> {

    @Override
    protected PersistentVolumeClaim desired(Keydra keydra, Context<Keydra> context) {
        BackupsSpec backups = keydra.getSpec().backups;
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata()
                .withName(Names.backupClaim(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .endMetadata()
                .withNewSpec()
                .withAccessModes(backups.accessModes)
                .withStorageClassName(
                        Names.isSet(backups.storageClass) ? backups.storageClass : null)
                .withNewResources()
                .withRequests(Map.of("storage", new Quantity(backups.size)))
                .endResources()
                .endSpec()
                .build();
    }
}
