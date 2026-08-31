package io.keydra.operator.olm;

import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.SharedCSVMetadata;

/**
 * What OperatorHub shows, and what OLM installs against.
 *
 * <p>Everything here ends up in the ClusterServiceVersion the build writes to {@code
 * target/bundle}, which is what a pull request to community-operators carries. It lives in one
 * place, shared by both controllers, because a bundle describes an operator rather than a
 * controller: two copies of this would be two descriptions of the same thing, drifting.
 *
 * <p><b>The install modes are the interesting part.</b> {@code AllNamespaces} is supported and is
 * what the default deployment does. {@code OwnNamespace} and {@code SingleNamespace} are supported
 * too, and they are the honest answer for an estate where one team's console should not be
 * installable by another's. {@code MultiNamespace} is not: OLM's version of it hands the operator a
 * list of namespaces at install time and the framework watches them, but the {@link
 * io.keydra.operator.connection.KeydraConnectionReconciler} looks up its Keydra in the resource's
 * own namespace and would be quietly right for the wrong reason. Claiming support for a mode that
 * has never been run is how a catalogue entry becomes a bug report.
 *
 * <p>The maturity is {@code alpha} and the API is {@code v1alpha1}, and both are the truth rather
 * than modesty: the two custom resources have not been through a release cycle in anybody else's
 * cluster yet.
 */
@CSVMetadata(
        name = "keydra-operator",
        bundleName = "keydra-operator",
        displayName = "Keydra",
        description =
                "Keydra is a web-based management console for Redis, Valkey, KeyDB, Dragonfly,"
                        + " Garnet, Aerospike and TiKV: browse keys with cursor-based scanning, read"
                        + " and write values, run a policed console, watch what a server is doing, take"
                        + " backups to seven kinds of destination, and give people access to the"
                        + " targets they should have and no others.\n\nThe operator installs an"
                        + " instance from a Keydra resource, and — this is the part a chart cannot"
                        + " do — lets a target be declared as a KeydraConnection resource, so a Redis"
                        + " that something else in the cluster created can be handed to the console by"
                        + " the same manifest that created it, and taken away by the same"
                        + " deletion.\n\nTwo things it deliberately does not do. It does not install a"
                        + " PostgreSQL: what lives there is everything Keydra knows, and a database"
                        + " packaged inside an application's operator is a database nobody is backing"
                        + " up. And it does not generate the key that encrypts stored target"
                        + " credentials: a generated key is regenerated on the next reconciliation, and"
                        + " what that produces is an instance that starts and cannot read a single"
                        + " stored credential. Both are named in the resource and supplied by whoever"
                        + " installs it.",
        keywords = {
            "redis",
            "valkey",
            "keydb",
            "dragonfly",
            "garnet",
            "aerospike",
            "tikv",
            "key-value",
            "console",
            "database"
        },
        maturity = "alpha",
        provider =
                @CSVMetadata.Provider(
                        name = "The Keydra Authors",
                        url = "https://github.com/keydrahq"),
        maintainers = @CSVMetadata.Maintainer(name = "keydrahq", email = "keydra@keydra.io"),
        icon = @CSVMetadata.Icon(fileName = "keydra.svg", mediatype = "image/svg+xml"),
        installModes = {
            @CSVMetadata.InstallMode(type = "AllNamespaces", supported = true),
            @CSVMetadata.InstallMode(type = "OwnNamespace", supported = true),
            @CSVMetadata.InstallMode(type = "SingleNamespace", supported = true),
            @CSVMetadata.InstallMode(type = "MultiNamespace", supported = false)
        },
        links = {
            @CSVMetadata.Link(
                    name = "Documentation",
                    url = "https://keydrahq.github.io/keydra/docs/en/latest/"),
            @CSVMetadata.Link(name = "Source", url = "https://github.com/keydrahq/keydra"),
            @CSVMetadata.Link(
                    name = "Operator",
                    url = "https://github.com/keydrahq/keydra-operator"),
            @CSVMetadata.Link(
                    name = "Container image",
                    url = "https://quay.io/repository/keydrahq/keydra")
        },
        annotations =
                @CSVMetadata.Annotations(
                        containerImage = "quay.io/keydrahq/keydra-operator:0.0.1",
                        repository = "https://github.com/keydrahq/keydra-operator",
                        capabilities = "Basic Install",
                        categories = "Database,Developer Tools",
                        certified = false,
                        almExamples =
                                """
                                [
                                  {
                                    "apiVersion": "keydra.io/v1alpha1",
                                    "kind": "Keydra",
                                    "metadata": { "name": "keydra" },
                                    "spec": {
                                      "database": { "url": "postgresql://keydra-db:5432/keydra" },
                                      "secret": { "name": "keydra" },
                                      "route": { "enabled": true },
                                      "proxy": { "enabled": true, "trusted": "10.0.0.0/8" }
                                    }
                                  },
                                  {
                                    "apiVersion": "keydra.io/v1alpha1",
                                    "kind": "KeydraConnection",
                                    "metadata": { "name": "orders-cache" },
                                    "spec": {
                                      "keydraRef": "keydra",
                                      "host": "orders-redis",
                                      "port": 6379,
                                      "engine": "RESP",
                                      "type": "STANDALONE",
                                      "guarded": true,
                                      "passwordSecret": { "name": "orders-redis", "key": "password" }
                                    }
                                  }
                                ]
                                """))
public class KeydraBundle implements SharedCSVMetadata {}
