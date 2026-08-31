# The operator's manager.
#
#   podman build -t keydra-operator:dev -f Containerfile .
#
# Small on purpose and for a reason beyond taste: this runs in every cluster that installs
# Keydra, whether or not anybody uses the parts of it they installed it for, and it holds
# cluster-wide permissions on Deployments and Services. What is in it is what an attacker
# who reaches it finds waiting, and there is no argument for anything being in it that is
# not needed to reconcile two custom resources.
#
# Which is also why there is no OpenShift client and no Prometheus model on the classpath:
# the Route and the ServiceMonitor are written as generic resources. Two model trees for two
# objects would be two model trees in this image.

# --- Stage 1: build -----------------------------------------------------------
# UBI 10's OpenJDK 21 image, which carries Maven 3.9 already — so this is a Maven image and a
# JDK image at once, and the version is pinned by the tag. That pin is the same guarantee the
# project's wrapper gives a developer, without a download at build time; and the download is
# what fails here, since this base image carries neither curl nor wget for the wrapper to use.
FROM registry.access.redhat.com/ubi10/openjdk-21:1.24 AS build

USER root
WORKDIR /build

# The descriptor first, then dependencies, then source: dependencies change far less often
# than code, so that layer survives almost every rebuild.
COPY .mvn/ .mvn/
COPY pom.xml ./
RUN mvn -B -ntp dependency:resolve dependency:resolve-plugins

COPY src/ src/
# The tests here need no containers — they build objects and assert on them — but they do need
# the chart checked out beside this repository, which a build container has no business
# fetching. They run in CI, where both are.
RUN mvn -B -ntp package -DskipTests

# --- Stage 2: what actually ships ---------------------------------------------
# The runtime variant: a JRE without the compiler, which is a few hundred megabytes a running
# manager has no use for and one more tool an attacker would find waiting.
FROM registry.access.redhat.com/ubi10/openjdk-21-runtime:1.24

USER root
WORKDIR /app

# The errata stream moves faster than the base tag does, and a weekly rebuild that republished
# the same unpatched packages would be a weekly rebuild doing nothing.
RUN microdnf -y update && microdnf -y clean all && rm -rf /var/cache/yum

COPY --from=build --chown=185:0 /build/target/quarkus-app/lib/ lib/
COPY --from=build --chown=185:0 /build/target/quarkus-app/*.jar ./
COPY --from=build --chown=185:0 /build/target/quarkus-app/app/ app/
COPY --from=build --chown=185:0 /build/target/quarkus-app/quarkus/ quarkus/

# No user is created here: the image already runs as uid 185, and files are given to group 0 so
# the container still works when a platform assigns it some arbitrary uid instead — which is
# what OpenShift does, and the reason group 0 rather than 185:185.
USER 185

# 8080 is health and metrics. 8081 is the application port and has nothing on it: the manager
# serves no traffic, and the port exists because the framework wants one.
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s \
    CMD ["sh", "-c", "curl -sf http://localhost:8080/q/health/ready || exit 1"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
