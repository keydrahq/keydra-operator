# Moving a Helm release to the operator

The two produce the same objects with the same names and the same labels, and that is deliberate:
it makes this an adoption rather than a delete and a recreate. An installation with saved profiles
and a database behind it is not a thing anybody wants to recreate to change how it is managed.

The one property that makes it possible is that a Deployment's `spec.selector` is immutable. If the
operator labelled pods differently from the chart, adopting an existing Deployment would be
impossible — the API server refuses the change, and the only way through is to delete the
Deployment first. So `app.kubernetes.io/name` and `app.kubernetes.io/instance` are exactly the
chart's, and the label that differs, `app.kubernetes.io/managed-by`, is deliberately not part of
the selector.

## The mapping

Almost every value has the same name in the spec. The differences:

| chart value | spec field | why |
|---|---|---|
| `nameOverride`, `fullnameOverride` | — | The resource's own name names everything. A release name and a chart name are two things Helm has to reconcile; a custom resource has one name already. |
| `secretKey`, `database.password` | — | The operator never writes the Secret. Both are `existingSecret` only. |
| `existingSecret.name` | `secret.name` | Required. |
| `existingSecret.secretKeyKey` | `secret.secretKeyKey` | |
| `replicaCount` | `replicas` | |
| `ui.replicaCount` | `ui.replicas` | |
| `ui.service.port` | `ui.servicePort` | |
| `metricsHistory.password` | `metricsHistory.passwordSecretKey` | A key in the Secret, not a value in the resource. |
| `mail.apiKeySecretKey` | `mail.apiKeySecretKey` | Unchanged; the chart already did this one right. |
| — | `route` | The chart has no Route. |
| — | `apiAccount` | The chart has no equivalent, because a chart cannot register targets. |

`podSecurityContext` and `securityContext` are Kubernetes types in both, and both replace the
default wholesale rather than merging with it.

## Doing it

**1. Take the release's values and write the resource.** `helm get values keydra` prints what the
release was installed with. Everything that is not in the table above keeps its name.

**2. Make sure the Secret is a real one.** If the release let the chart render the Secret from
`secretKey` and `database.password`, the operator cannot adopt that arrangement — it will not write
a Secret, and the one Helm wrote is owned by the release. Take a copy that is not:

```bash
kubectl get secret keydra -o json \
  | jq 'del(.metadata.ownerReferences, .metadata.labels, .metadata.annotations,
             .metadata.resourceVersion, .metadata.uid, .metadata.creationTimestamp)
        | .metadata.name = "keydra-credentials"' \
  | kubectl apply -f -
```

Losing that key means losing every stored target credential. Copy it before anything else, and
check the copy is right before continuing.

**3. Let Helm go without taking the objects with it.**

```bash
helm uninstall keydra --keep-history
```

does not do this — `helm uninstall` deletes the release's objects. What is wanted is for Helm to
forget them:

```bash
for kind in deployment service serviceaccount ingress poddisruptionbudget servicemonitor; do
  kubectl annotate "$kind" keydra helm.sh/resource-policy=keep --overwrite 2>/dev/null || true
done
helm uninstall keydra
```

`resource-policy: keep` tells Helm to leave the object behind. What is left is a Deployment nobody
is managing, which is exactly the state the operator wants to find.

**4. Apply the resource.** The operator adopts the existing objects — same names, same selector —
and updates them in place. The pods roll once, because the environment it computes differs from the
release's in at least the `managed-by` label.

```bash
kubectl apply -f keydra.yaml
kubectl get keydra keydra -w
```

**5. Check the claim.** If backups were enabled, the chart's PVC carried
`helm.sh/resource-policy: keep` already and survives. The operator creates its claim with no owner
reference, so nothing deletes it either — but the two use the same name (`<name>-backups`), so
there is nothing to move.

## What does not migrate

**Nothing about the database.** The operator does not touch it, does not migrate it, and has no
opinion about it. The installation comes back pointing at the same PostgreSQL and finds everything
where it was.

**Existing connection profiles do not become `KeydraConnection` resources.** They stay profiles in
the database, edited in the console. Declaring an existing target as a resource is possible but is
a deliberate act: the operator refuses to adopt a profile it did not create, so it means deleting
the profile in the console and letting the resource create it again. The profile's id changes when you do
that, and two things are held against that id rather than against the name: a grant whose scope is
`CONNECTION`, and membership of a server group.

Which is the reason to think about this rather than do it as tidying. Converting a target somebody
has access to will drop that access, silently, and the person finds out by not being able to see a
server they could see yesterday.
