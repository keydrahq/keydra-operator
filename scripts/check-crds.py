#!/usr/bin/env python3
"""What the generated CRDs actually say.

The CRDs are generated from the spec classes, which is the right way round — a field that exists
in Java and not in the schema is a thing that cannot happen. The cost of generating them is that
nothing fails when a rule stops being emitted: the classes still compile, the build still passes,
and the API server quietly stops refusing the thing it used to refuse.

So this reads the output rather than the source, and asserts the handful of properties that are
load-bearing. It is run by CI after `package`, which is when the files exist; surefire runs before
they do, which is why this is a script and not a test.
"""

import sys
from pathlib import Path

import yaml

GENERATED = Path("target/kubernetes")

FAILURES: list[str] = []


def check(condition: bool, message: str) -> None:
    if not condition:
        FAILURES.append(message)


def load(name: str) -> dict:
    path = GENERATED / name
    if not path.exists():
        print(f"{path} does not exist. Run ./mvnw package first.", file=sys.stderr)
        raise SystemExit(2)
    return yaml.safe_load(path.read_text())


def spec_schema(crd: dict) -> dict:
    version = crd["spec"]["versions"][0]
    return version["schema"]["openAPIV3Schema"]["properties"]["spec"]


def rules(schema: dict) -> list[str]:
    return [rule["rule"] for rule in schema.get("x-kubernetes-validations", [])]


keydra = load("keydras.keydra.io-v1.yml")
connection = load("keydraconnections.keydra.io-v1.yml")

# --- Keydra ----------------------------------------------------------------------------
schema = spec_schema(keydra)

check(
    set(schema.get("required", [])) == {"database", "secret"},
    "A Keydra must require both a database and a Secret. Neither can be defaulted: one is an"
    " address only this deployment knows, and the other must not be generated.",
)
check(
    "url" in keydra["spec"]["versions"][0]["schema"]["openAPIV3Schema"]["properties"]["spec"][
        "properties"
    ]["database"].get("required", []),
    "database.url must be required.",
)
check(
    "name" in schema["properties"]["secret"].get("required", []),
    "secret.name must be required: the operator never creates the Secret.",
)

emitted = rules(schema)
check(
    any("proxy.trusted" in rule for rule in emitted),
    "The rule refusing proxy.enabled with no trusted proxies is not in the CRD. With the switch"
    " on and nobody named, any client can claim any address.",
)
check(
    any("self.route.enabled" in rule and "self.ingress.enabled" in rule for rule in emitted),
    "The rule refusing both an Ingress and a Route is not in the CRD.",
)
check(
    any("identityProvider.clientId" in rule for rule in emitted),
    "The rule refusing a half-named identity provider is not in the CRD.",
)

columns = {column["name"] for column in keydra["spec"]["versions"][0].get(
    "additionalPrinterColumns", []
)}
check(
    {"Ready", "URL"} <= columns,
    "`kubectl get keydra` must show whether it is up and where it is; got " + str(sorted(columns)),
)

# The status is what the operator writes back, and a subresource is what lets it write only that.
check(
    "status" in keydra["spec"]["versions"][0].get("subresources", {}),
    "The status subresource is missing, so the operator would have to patch the whole resource"
    " to report a condition.",
)

# --- KeydraConnection ------------------------------------------------------------------
schema = spec_schema(connection)

check(
    {"keydraRef", "host", "port"} <= set(schema.get("required", [])),
    "A KeydraConnection must name the installation, the host and the port.",
)
check(
    schema["properties"]["port"].get("maximum") == 65535,
    "The port must be bounded by the schema rather than by the application refusing it later.",
)
check(
    "passwordSecret" in schema["properties"]
    and "password" not in schema["properties"],
    "A password must be a Secret reference and never a field: a password in a custom resource is"
    " readable by anybody who can read the resource.",
)

emitted = rules(schema)
check(
    any("sentinelMasterName" in rule for rule in emitted),
    "The rule refusing a SENTINEL with no master name is not in the CRD.",
)

# ---------------------------------------------------------------------------------------
if FAILURES:
    print("The generated CRDs are not what they are supposed to be:\n", file=sys.stderr)
    for failure in FAILURES:
        print(f"  - {failure}\n", file=sys.stderr)
    raise SystemExit(1)

print("The generated CRDs say what they are supposed to say.")
