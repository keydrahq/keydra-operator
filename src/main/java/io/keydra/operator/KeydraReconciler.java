package io.keydra.operator;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.ErrorStatusUpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Workflow;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.keydra.operator.dependent.Activations;
import io.keydra.operator.dependent.BackupClaimDependent;
import io.keydra.operator.dependent.DeploymentDependent;
import io.keydra.operator.dependent.DisruptionBudgetDependent;
import io.keydra.operator.dependent.IngressDependent;
import io.keydra.operator.dependent.RouteDependent;
import io.keydra.operator.dependent.ServiceAccountDependent;
import io.keydra.operator.dependent.ServiceDependent;
import io.keydra.operator.dependent.ServiceMonitorDependent;
import io.keydra.operator.dependent.UiDeploymentDependent;
import io.keydra.operator.dependent.UiServiceDependent;
import io.keydra.operator.install.Environment;
import io.keydra.operator.install.Names;
import io.keydra.operator.status.Conditions;
import io.keydra.operator.v1alpha1.Keydra;
import io.keydra.operator.v1alpha1.KeydraStatus;
import io.keydra.operator.v1alpha1.SecretSpec;
import io.quarkiverse.operatorsdk.annotations.AdditionalRBACRules;
import io.quarkiverse.operatorsdk.annotations.CSVMetadata;
import io.quarkiverse.operatorsdk.annotations.RBACRule;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An installation, kept as the resource describes it.
 *
 * <p>Almost all of the work is declared rather than written: the dependents below produce the same
 * objects the Helm chart's templates do, and the framework creates, updates and deletes them. What
 * is left here is the two things a set of templates cannot do.
 *
 * <p><b>The first is the refusal.</b> The chart answers a missing secret key with {@code fail} and
 * a paragraph, because there is a person watching {@code helm install}. Most of that moved to
 * validation rules on the CRD, where the API server can refuse an apply. One check cannot go there,
 * because it is about the cluster rather than about the spec: whether the Secret the spec names
 * actually exists, and carries the keys it was said to carry. That is checked here, before anything
 * is created, and a failure is a {@code Degraded} condition naming the missing key rather than a
 * Deployment that comes up and cannot read a single stored credential.
 *
 * <p><b>The second is the status.</b> A chart installs and stops having an opinion; an operator is
 * still there afterwards, and what it is for is answering "is this up, and where is it" without
 * anybody having to read three objects to work it out.
 */
@ControllerConfiguration(name = "keydra")
@CSVMetadata(name = "keydra-operator")
// Read, and only read. Everything else an installation owns is generated from the dependents
// below; this is the one thing the reconciler reaches for itself, and it reaches for it to
// answer a question rather than to change anything. An operator that could write Secrets in
// every namespace it watches would be a much more interesting thing to compromise than one
// that can check whether a key is present.
@AdditionalRBACRules(
        @RBACRule(
                apiGroups = "",
                resources = "secrets",
                verbs = {"get"}))
@Workflow(
        dependents = {
            @Dependent(
                    name = "serviceaccount",
                    type = ServiceAccountDependent.class,
                    activationCondition = Activations.ServiceAccountWanted.class),
            @Dependent(
                    name = "backups",
                    type = BackupClaimDependent.class,
                    activationCondition = Activations.BackupClaimWanted.class),
            @Dependent(name = "deployment", type = DeploymentDependent.class),
            @Dependent(name = "service", type = ServiceDependent.class),
            @Dependent(
                    name = "ui-deployment",
                    type = UiDeploymentDependent.class,
                    activationCondition = Activations.SplitShape.class),
            @Dependent(
                    name = "ui-service",
                    type = UiServiceDependent.class,
                    activationCondition = Activations.SplitShape.class),
            @Dependent(
                    name = "ingress",
                    type = IngressDependent.class,
                    activationCondition = Activations.IngressWanted.class),
            @Dependent(
                    name = "route",
                    type = RouteDependent.class,
                    activationCondition = Activations.RouteWanted.class),
            @Dependent(
                    name = "pdb",
                    type = DisruptionBudgetDependent.class,
                    activationCondition = Activations.DisruptionBudgetWanted.class),
            @Dependent(
                    name = "servicemonitor",
                    type = ServiceMonitorDependent.class,
                    activationCondition = Activations.ServiceMonitorWanted.class)
        })
public class KeydraReconciler implements Reconciler<Keydra> {

    private final KubernetesClient client;

    @Inject
    public KeydraReconciler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public UpdateControl<Keydra> reconcile(Keydra keydra, Context<Keydra> context) {
        KeydraStatus status = keydra.getStatus() == null ? new KeydraStatus() : keydra.getStatus();
        if (status.conditions == null) {
            status.conditions = new ArrayList<>();
        }
        Long generation = keydra.getMetadata().getGeneration();
        status.observedGeneration = generation;

        List<String> missing = missingSecretKeys(keydra);
        boolean secretsPresent = missing.isEmpty();

        Optional<Deployment> deployment =
                context.getSecondaryResource(Deployment.class, "deployment");
        int ready =
                deployment
                        .map(Deployment::getStatus)
                        .map(
                                deploymentStatus ->
                                        deploymentStatus.getReadyReplicas() == null
                                                ? 0
                                                : deploymentStatus.getReadyReplicas())
                        .orElse(0);
        status.readyReplicas = ready;
        status.image =
                deployment
                        .map(Deployment::getSpec)
                        .map(spec -> spec.getTemplate().getSpec().getContainers())
                        .filter(containers -> !containers.isEmpty())
                        .map(containers -> containers.get(0).getImage())
                        .orElse(null);
        status.url = Environment.publicUrl(keydra, context).orElse(null);

        Conditions.set(
                status.conditions,
                KeydraStatus.DEGRADED,
                !secretsPresent,
                secretsPresent ? "AsConfigured" : "SecretIncomplete",
                secretsPresent
                        ? "Nothing is wrong that the operator can see."
                        : degradedMessage(keydra, missing),
                generation);

        boolean available = secretsPresent && ready > 0;
        Conditions.set(
                status.conditions,
                KeydraStatus.AVAILABLE,
                available,
                available ? "InstanceReady" : "NoReadyInstance",
                available
                        ? ready + " of " + keydra.getSpec().replicas + " instances are ready."
                        : "No instance has passed its readiness check yet.",
                generation);

        boolean progressing = secretsPresent && ready < keydra.getSpec().replicas;
        Conditions.set(
                status.conditions,
                KeydraStatus.PROGRESSING,
                progressing,
                progressing ? "RollingOut" : "Settled",
                progressing
                        ? "Waiting for "
                                + keydra.getSpec().replicas
                                + " instances; "
                                + ready
                                + " are ready."
                        : "The installation matches what the resource asks for.",
                generation);

        keydra.setStatus(status);
        return UpdateControl.patchStatus(keydra);
    }

    /**
     * A reconciliation that threw still has to say so on the resource.
     *
     * <p>Without this the only record of a failure is a line in the manager's log, which is in a
     * different namespace from the person who applied the resource and is frequently in a different
     * organisation.
     */
    @Override
    public ErrorStatusUpdateControl<Keydra> updateErrorStatus(
            Keydra keydra, Context<Keydra> context, Exception exception) {
        KeydraStatus status = keydra.getStatus() == null ? new KeydraStatus() : keydra.getStatus();
        if (status.conditions == null) {
            status.conditions = new ArrayList<>();
        }
        Conditions.set(
                status.conditions,
                KeydraStatus.DEGRADED,
                true,
                "ReconciliationFailed",
                String.valueOf(exception.getMessage()),
                keydra.getMetadata().getGeneration());
        keydra.setStatus(status);
        return ErrorStatusUpdateControl.patchStatus(keydra);
    }

    /**
     * Which of the keys the spec names are not in the Secret it names.
     *
     * <p>Read directly rather than through an informer: the operator has no business watching every
     * Secret in every namespace it manages, and what it needs is one answer at one moment. A cache
     * of other people's Secrets is a thing to be careful with; a get is not.
     */
    private List<String> missingSecretKeys(Keydra keydra) {
        SecretSpec spec = keydra.getSpec().secret;
        List<String> wanted = new ArrayList<>();
        wanted.add(spec.secretKeyKey);
        wanted.add(spec.databasePasswordKey);
        if (keydra.getSpec().identityProvider != null
                && Names.isSet(keydra.getSpec().identityProvider.url)) {
            wanted.add(spec.oidcSecretKey);
        }
        if (keydra.getSpec().metricsHistory != null
                && Names.isSet(keydra.getSpec().metricsHistory.passwordSecretKey)) {
            wanted.add(keydra.getSpec().metricsHistory.passwordSecretKey);
        }
        if (keydra.getSpec().mail != null && Names.isSet(keydra.getSpec().mail.apiKeySecretKey)) {
            wanted.add(keydra.getSpec().mail.apiKeySecretKey);
        }

        Secret secret =
                client.secrets()
                        .inNamespace(keydra.getMetadata().getNamespace())
                        .withName(spec.name)
                        .get();
        if (secret == null) {
            return wanted;
        }
        var data = secret.getData() == null ? java.util.Map.<String, String>of() : secret.getData();
        var stringData =
                secret.getStringData() == null
                        ? java.util.Map.<String, String>of()
                        : secret.getStringData();
        return wanted.stream()
                .filter(key -> !data.containsKey(key) && !stringData.containsKey(key))
                .toList();
    }

    private static String degradedMessage(Keydra keydra, List<String> missing) {
        return "Secret \""
                + keydra.getSpec().secret.name
                + "\" is missing "
                + String.join(", ", missing)
                + ". The operator does not create it: the key it holds encrypts every stored"
                + " target credential, and a generated one would be regenerated on the next"
                + " reconciliation — leaving an instance that starts and cannot read any of them.";
    }
}
