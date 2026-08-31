package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * Where a local backup destination writes.
 *
 * <p>Without a volume this is a directory in a container, which is a backup that goes when the pod
 * does — the one thing a backup exists not to do. A destination that leaves the cluster entirely is
 * still the better answer, and Keydra has seven kinds.
 *
 * <p>The claim the operator creates is not deleted with the Keydra resource. A backup that goes
 * when the thing it was taken against goes is not a backup, and deleting the installation is
 * exactly the moment somebody would want one.
 */
public class BackupsSpec {

    public Boolean enabled = false;

    public String size = "10Gi";

    @JsonPropertyDescription("Empty uses the cluster's default StorageClass.")
    public String storageClass;

    public List<String> accessModes = List.of("ReadWriteOnce");

    @JsonPropertyDescription("Use a claim somebody else made instead of creating one.")
    public String existingClaim;

    @JsonPropertyDescription("Where it is mounted in the container.")
    public String mountPath = "/var/lib/keydra/backups";
}
