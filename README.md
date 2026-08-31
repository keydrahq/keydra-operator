# keydra-operator

The Kubernetes operator for [Keydra](https://github.com/keydrahq/keydra) — a web-based management
console for Redis, Valkey, KeyDB, Dragonfly, Garnet, Aerospike and TiKV.

```bash
kubectl create namespace keydra-operator
kubectl apply -f https://github.com/keydrahq/keydra-operator/releases/latest/download/crds.yaml
kubectl apply -f https://github.com/keydrahq/keydra-operator/releases/latest/download/operator.yaml
```

On OpenShift, install it from OperatorHub instead and skip both of those.

Then an installation is a resource:

```yaml
apiVersion: keydra.io/v1alpha1
kind: Keydra
metadata:
  name: keydra
spec:
  database:
    url: postgresql://keydra-db:5432/keydra
  secret:
    name: keydra
  route:
    enabled: true
  proxy:
    enabled: true
    trusted: 10.0.0.0/8
```

```
$ kubectl get keydra
NAME     READY   REPLICAS   URL                                    AGE
keydra   True    1          https://keydra-apps.example.com        2m
```

## What this is for

There is already [a Helm chart](https://github.com/keydrahq/keydra-helm), and for installing
Keydra the two do the same thing — deliberately so, down to the names and labels the objects
carry, so that an estate can move from one to the other without a delete and a recreate. If all
you want is to install the console, the chart is one command and needs nothing in the cluster.

The operator exists for the two things a chart cannot do.

**It is still there afterwards.** A chart installs and stops having an opinion. The operator
answers "is this up, and where is it" on the resource itself, keeps the installation matching what
the resource says when somebody edits a Deployment by hand, and notices a Secret that gains its
missing key without anybody re-running anything.

**A target can be a resource.** This is the part that is not a chart with a different syntax:

```yaml
apiVersion: keydra.io/v1alpha1
kind: KeydraConnection
metadata:
  name: orders-cache
spec:
  keydraRef: keydra
  host: orders-redis
  port: 6379
  guarded: true
  passwordSecret:
    name: orders-redis
    key: password
```

A Redis that something else in the cluster created can be handed to the console by the same
manifest that created it, and taken away by the same deletion. A profile that exists because a
resource says so also stops existing when the resource does, which is a property a form can never
have.

## Before installing

Two things the operator will not invent, and refuses to install without.

**A PostgreSQL of its own.** Not one of the servers Keydra manages — this is where the connection
profiles, the accounts, the grants, the audit log, the schedules and the rules live. What is in
there is everything Keydra knows, and a database an application's operator brings up beside itself
is a database nobody is backing up, running on a pod whose replacement is what an upgrade is.

For a look at it and nothing more, any throwaway will do:

```bash
kubectl run keydra-db --image=docker.io/library/postgres:17-alpine \
  --env=POSTGRES_DB=keydra --env=POSTGRES_USER=keydra --env=POSTGRES_PASSWORD=keydra \
  --port=5432 --expose
```

**A Secret you made.** The chart has a fallback that renders one from values; there is no
equivalent here, and the difference is not an omission. A key written into a custom resource is
readable by anybody who can `get keydra` in the namespace — a wider audience than anybody who can
read Secrets, and the audience least likely to have been thought about.

```bash
kubectl create secret generic keydra \
  --from-literal=secret-key="$(openssl rand -base64 32)" \
  --from-literal=database-password='...'
```

That key encrypts every stored target password and tunnel key. Losing it means losing them;
sharing it means sharing them. The operator will not generate one, because a generated key is
regenerated on the next reconciliation unless something remembers it — and the failure that
produces is not an error. It is an instance that starts, finds it cannot read a single stored
credential, and reports it one target at a time.

## Registering targets

`KeydraConnection` needs one more thing than an installation does: an account to sign in as.

Keydra has no notion of a machine caller today — no token, no service account — so the operator
signs in the way a browser does, posting a username and a password to `/api/v1/auth/login` and
keeping the session cookie. That works, and it has consequences worth agreeing to before turning it
on:

- The account appears in the audit log as the author of every profile the operator writes. Give it
  its own account rather than a person's, so the log says which changes were somebody typing and
  which were a resource being applied.
- It has to be an administrator. Writing a connection profile is `CONNECTION_CREATE`, which is an
  administrator's permission — so an operator that can declare targets can do everything an
  administrator can.
- Its sign-ins are counted by the same throttle as a person's. The operator holds one session per
  instance and reuses it for every resource pointing at that instance, which is why.

A first-class API credential is the right answer and is a change to Keydra rather than to this
operator. Until there is one, this is the honest arrangement rather than a hidden one.

```bash
kubectl create secret generic keydra-api \
  --from-literal=api-username=operator \
  --from-literal=api-password='...'
```

```yaml
spec:
  apiAccount:
    secretName: keydra-api
```

Leave `apiAccount` out entirely on an installation that declares no targets. An operator holding an
administrator's password on a cluster where nobody asked it to is holding it for no reason.

## The two shapes

`spec.mode` is `standalone` or `split`, and means what it means in the chart.

`standalone` is one image serving the API and the interface it calls, so one container is the whole
thing and nothing has to be told where the API lives. It is the one to pick unless something below
applies.

`split` is two: the API, and the interface as static files behind an nginx that routes `/api` and
`/graphql` to it. Worth the second Deployment when the two scale differently — several API replicas
behind one set of files — or when the interface belongs somewhere the API does not.

The interface is proxied rather than calling the API across origins in both shapes. The session is
a cookie, and a cookie sent to another origin needs `SameSite=None` and a CORS policy that allows
credentials; one origin to the browser is fewer things to get wrong.

## Two ports, and why only one is published

The Service carries `http` (8181) and `management` (9001). What answers on the management port
answers without a session — which is what a scraper and the platform's probes need — and what it
says is which instances are running, how many targets there are and how much work is moving: a map
of the installation, handed to anybody who can reach the port.

So the Ingress and the Route publish the first and never the second, and the ServiceMonitor scrapes
the second from inside the cluster where it already is. That is the whole reason there are two.

## What it refuses

Three of the chart's refusals moved to validation rules on the CRD, where the API server makes
them — so a spec that breaks one is rejected at `kubectl apply` with the sentence attached, rather
than becoming a condition somebody has to go and read:

- `proxy.enabled` on with `proxy.trusted` naming nobody. With the switch on and no proxies named,
  any client can claim any address — worse than not trusting the header at all, because the sign-in
  checks and the attempt limit both believe what they are told.
- `ingress` and `route` both enabled. Two objects publishing one service under two hostnames is a
  way of ending up with a `publicUrl` that is right for one of them.
- `identityProvider.url` set with no `clientId`. A provider is turned on by being named, so a
  half-named one is an instance that starts and then cannot complete a sign-in.

One check cannot go there, because it is about the cluster rather than about the spec: whether the
Secret the resource names exists and carries the keys it was said to carry. That is a `Degraded`
condition naming the missing key.

## Status

```
$ kubectl describe keydra keydra
Status:
  Conditions:
    Type: Available    Status: True   Reason: InstanceReady
    Type: Degraded     Status: False  Reason: AsConfigured
    Type: Progressing  Status: False  Reason: Settled
  Image:           quay.io/keydrahq/keydra:0.0.1
  Ready Replicas:  1
  Url:             https://keydra-apps.example.com
```

`image` is what is running rather than what was asked for. A spec naming a moving tag and a cluster
running what that tag meant last week are two different facts, and this is the second one.

## Development

```bash
./mvnw quarkus:dev          # applies the CRDs to whatever kubectl points at, then watches
./mvnw verify               # the tests, including the chart-agreement one
./mvnw package              # CRDs into target/kubernetes, OLM bundle into target/bundle
```

The chart-agreement test reads `../keydra-helm/charts/keydra/templates/deployment.yaml` and checks
that the operator and the chart set the same environment variables. Without that checkout it skips;
CI checks both out side by side, which is where it matters. A variable one of them sets and the
other does not is a deployment that behaves differently depending on how it was installed, and it
is the kind of difference nobody finds until somebody migrates.

## Licence

Apache 2.0. See [LICENSE](LICENSE).
