package io.keydra.operator.v1alpha1;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Which image, and how it is referred to.
 *
 * <p>A digest wins over a tag, because a digest is the only reference a rollback can rely on: a tag
 * can be moved to point somewhere else after the fact.
 */
public class ImageSpec {

    @JsonPropertyDescription(
            "Empty means the right one for the mode: quay.io/keydrahq/keydra when standalone,"
                    + " quay.io/keydrahq/keydra-backend when split.")
    public String repository;

    @JsonPropertyDescription("Empty means the version this operator was released alongside.")
    public String tag;

    @JsonPropertyDescription(
            "sha256:… — wins over tag. Name one for a deployment that has to be able to roll back"
                    + " to something that does not move.")
    public String digest;

    @JsonPropertyDescription("IfNotPresent, Always or Never.")
    public String pullPolicy = "IfNotPresent";
}
