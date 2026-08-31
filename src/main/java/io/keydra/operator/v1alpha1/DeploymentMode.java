package io.keydra.operator.v1alpha1;

/**
 * How the application is deployed.
 *
 * <p>The interface is proxied rather than calling the API across origins in both shapes. The
 * session is a cookie, and a cookie sent to another origin needs {@code SameSite=None} and a CORS
 * policy that allows credentials; one origin to the browser is fewer things to get wrong.
 */
public enum DeploymentMode {
    /**
     * One image serving the API and the interface it calls, so one container is the whole thing and
     * nothing has to be told where the API lives. The ordinary deployment, and the one to pick
     * unless {@link #split} applies.
     */
    standalone,

    /**
     * Two: the API, and the interface as static files behind an nginx that routes {@code /api} and
     * {@code /graphql} to it.
     *
     * <p>Worth the second Deployment when the two scale differently — several API replicas behind
     * one set of files — or when the interface belongs somewhere the API does not.
     */
    split
}
