package io.keydra.operator.dependent;

import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPath;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.keydra.operator.install.Names;
import io.keydra.operator.v1alpha1.IngressSpec;
import io.keydra.operator.v1alpha1.Keydra;
import java.util.List;

/**
 * The front door, where the cluster's front door is an Ingress.
 *
 * <p>It sends people to whichever Service serves the interface — the application's own port when
 * standalone, nginx when split — and never to the management port. Putting {@code /q} behind this
 * would publish health and metrics to whoever can reach the hostname, and those two describe the
 * installation to anybody who asks.
 */
@KubernetesDependent
public class IngressDependent extends CRUDKubernetesDependentResource<Ingress, Keydra> {

    @Override
    protected Ingress desired(Keydra keydra, Context<Keydra> context) {
        IngressSpec ingress = keydra.getSpec().ingress;
        return new IngressBuilder()
                .withNewMetadata()
                .withName(Names.of(keydra))
                .withNamespace(keydra.getMetadata().getNamespace())
                .withLabels(Names.labels(keydra))
                .withAnnotations(ingress.annotations)
                .endMetadata()
                .withNewSpec()
                .withIngressClassName(Names.isSet(ingress.className) ? ingress.className : null)
                .withTls(ingress.tls)
                .withRules(rules(keydra))
                .endSpec()
                .build();
    }

    private static List<IngressRule> rules(Keydra keydra) {
        IngressSpec ingress = keydra.getSpec().ingress;
        String service = Names.frontDoorService(keydra);
        int port = Names.frontDoorPort(keydra);
        return ingress.hosts.stream()
                .map(
                        host ->
                                (IngressRule)
                                        new IngressRuleBuilder()
                                                .withHost(host.host)
                                                .withNewHttp()
                                                .withPaths(
                                                        host.paths.stream()
                                                                .map(
                                                                        path ->
                                                                                path(
                                                                                        path.path,
                                                                                        path.pathType,
                                                                                        service,
                                                                                        port))
                                                                .toList())
                                                .endHttp()
                                                .build())
                .toList();
    }

    private static HTTPIngressPath path(String path, String pathType, String service, int port) {
        return new HTTPIngressPathBuilder()
                .withPath(path)
                .withPathType(pathType)
                .withNewBackend()
                .withNewService()
                .withName(service)
                .withNewPort()
                .withNumber(port)
                .endPort()
                .endService()
                .endBackend()
                .build();
    }
}
