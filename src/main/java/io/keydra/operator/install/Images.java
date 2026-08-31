package io.keydra.operator.install;

import io.keydra.operator.v1alpha1.DeploymentMode;
import io.keydra.operator.v1alpha1.ImageSpec;
import io.keydra.operator.v1alpha1.Keydra;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Which image a container gets.
 *
 * <p>A digest wins over a tag, because a digest is the only reference a rollback can rely on: a tag
 * can be moved to point somewhere else after the fact.
 *
 * <p>The default version is the operator's configuration rather than a constant, and the two move
 * independently on purpose. An operator release that only fixes a reconciler does not make a new
 * Keydra, and an installation that has pinned a version should not have it changed by an operator
 * upgrade — which is what a constant compiled into the manager would do.
 */
public final class Images {

    private Images() {}

    private static final String STANDALONE = "quay.io/keydrahq/keydra";
    private static final String BACKEND = "quay.io/keydrahq/keydra-backend";
    private static final String UI = "quay.io/keydrahq/keydra-ui";

    /** The application's image. */
    public static String application(Keydra keydra) {
        ImageSpec image = keydra.getSpec().image;
        String fallback = keydra.getSpec().mode == DeploymentMode.split ? BACKEND : STANDALONE;
        return reference(image, fallback, keydra);
    }

    /** The interface's, in the split shape. */
    public static String ui(Keydra keydra) {
        return reference(keydra.getSpec().ui.image, UI, keydra);
    }

    /**
     * The version an installation is asked for, which is what the {@code version} label says.
     *
     * <p>A digest has no version to report, so an installation pinned to one is labelled with the
     * default rather than with a truncated hash: a label is for finding things, and half a digest
     * finds nothing.
     */
    public static String version(Keydra keydra) {
        ImageSpec image = keydra.getSpec().image;
        return image != null && Names.isSet(image.tag) ? image.tag : defaultVersion();
    }

    private static String reference(ImageSpec image, String fallbackRepository, Keydra keydra) {
        String repository =
                image != null && Names.isSet(image.repository)
                        ? image.repository
                        : fallbackRepository;
        if (image != null && Names.isSet(image.digest)) {
            return repository + "@" + image.digest;
        }
        String tag = image != null && Names.isSet(image.tag) ? image.tag : defaultVersion();
        return repository + ":" + tag;
    }

    public static String pullPolicy(Keydra keydra) {
        ImageSpec image = keydra.getSpec().image;
        return image != null && Names.isSet(image.pullPolicy) ? image.pullPolicy : "IfNotPresent";
    }

    private static String defaultVersion() {
        return ConfigProvider.getConfig()
                .getOptionalValue("keydra.default-version", String.class)
                .orElse("latest");
    }
}
