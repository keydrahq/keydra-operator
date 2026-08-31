package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import io.javaoperatorsdk.operator.processing.dependent.workflow.CRDPresentActivationCondition;
import io.javaoperatorsdk.operator.processing.dependent.workflow.Condition;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.DeploymentMode;
import io.keydra.operator.v1alpha1.Keydra;

/**
 * Which parts of an installation this particular spec asked for.
 *
 * <p>A deactivated dependent is not a dependent that produces nothing — it is one the workflow does
 * not have, which is the difference that matters when something is turned off after it was on: the
 * framework deletes what a deactivated dependent used to own. Turning {@code ingress.enabled} back
 * to false removes the Ingress, rather than leaving an orphan nobody is reconciling.
 *
 * <p>The two that ask the cluster rather than the spec — {@link RouteWanted} and {@link
 * ServiceMonitorWanted} — are the reason this file exists at all. Asking for a Route on a cluster
 * that has no Routes is not an error worth failing an installation over; it is a thing that is
 * true, and the answer to it is to install everything else and say so.
 */
public final class Activations {

    private Activations() {}

    /** The account, unless the spec says somebody else's is to be used. */
    public static class ServiceAccountWanted implements Condition<HasMetadata, Keydra> {
        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var spec = keydra.getSpec().serviceAccount;
            return spec == null || Boolean.TRUE.equals(spec.create);
        }
    }

    /** The interface's own Deployment and Service. */
    public static class SplitShape implements Condition<HasMetadata, Keydra> {
        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            return keydra.getSpec().mode == DeploymentMode.split;
        }
    }

    public static class IngressWanted implements Condition<HasMetadata, Keydra> {
        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var ingress = keydra.getSpec().ingress;
            return ingress != null && Boolean.TRUE.equals(ingress.enabled);
        }
    }

    /**
     * A Route, and only where there is such a thing.
     *
     * <p>The CRD check is the framework's, which caches its answer and re-asks on an interval — a
     * cluster does not usually grow an API group while an operator is watching, and asking the
     * discovery endpoint on every reconciliation would be a request per Keydra per event to
     * establish a fact that changes once a year.
     */
    public static class RouteWanted implements Condition<HasMetadata, Keydra> {

        private final CRDPresentActivationCondition<HasMetadata, Keydra> present =
                new CRDPresentActivationCondition<>();

        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var route = keydra.getSpec().route;
            return route != null
                    && Boolean.TRUE.equals(route.enabled)
                    && present.isMet(dependent, keydra, context);
        }
    }

    /** A ServiceMonitor, and only where the Prometheus operator's CRD is installed. */
    public static class ServiceMonitorWanted implements Condition<HasMetadata, Keydra> {

        private final CRDPresentActivationCondition<HasMetadata, Keydra> present =
                new CRDPresentActivationCondition<>();

        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var monitor = keydra.getSpec().serviceMonitor;
            return monitor != null
                    && Boolean.TRUE.equals(monitor.enabled)
                    && present.isMet(dependent, keydra, context);
        }
    }

    /** A claim, unless one was named — in which case it is somebody else's to make. */
    public static class BackupClaimWanted implements Condition<HasMetadata, Keydra> {
        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var backups = keydra.getSpec().backups;
            return backups != null
                    && Boolean.TRUE.equals(backups.enabled)
                    && !Names.isSet(backups.existingClaim);
        }
    }

    public static class DisruptionBudgetWanted implements Condition<HasMetadata, Keydra> {
        @Override
        public boolean isMet(
                DependentResource<HasMetadata, Keydra> dependent,
                Keydra keydra,
                Context<Keydra> context) {
            var budget = keydra.getSpec().podDisruptionBudget;
            return budget != null && Boolean.TRUE.equals(budget.enabled);
        }
    }
}
