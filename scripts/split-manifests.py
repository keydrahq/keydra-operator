#!/usr/bin/env python3
"""Everything the manager needs, without the CRDs.

`quarkus package` writes one kubernetes.yml holding the namespace, the account, the roles, the
bindings and the Deployment — and, in a separate pair of files, the CRDs. The release attaches
those two things separately because they are installed at different times by different people:
CRDs are cluster-wide and applied once by whoever owns the cluster, and the manager is a
Deployment somebody upgrades. Handing over one file makes the second operation look like the
first.

This exists only to be sure of the split. If a future Quarkus writes CRDs into the combined
file, the release would quietly start shipping them in the wrong half.
"""

import sys
from pathlib import Path

import yaml

source = Path(sys.argv[1])
destination = Path(sys.argv[2])

kept = [
    document
    for document in yaml.safe_load_all(source.read_text())
    if document and document.get("kind") != "CustomResourceDefinition"
]

if not kept:
    print(f"{source} held nothing but CRDs, which cannot be right.", file=sys.stderr)
    raise SystemExit(1)

kinds = sorted({document["kind"] for document in kept})
for required in ("Deployment", "ServiceAccount", "ClusterRole", "ClusterRoleBinding"):
    if required not in kinds:
        print(f"{source} has no {required}; the manager would not run.", file=sys.stderr)
        raise SystemExit(1)

destination.write_text(yaml.dump_all(kept, default_flow_style=False, sort_keys=False))
print(f"{destination}: {', '.join(kinds)}")
