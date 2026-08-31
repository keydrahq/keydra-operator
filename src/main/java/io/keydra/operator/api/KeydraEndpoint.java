package io.keydra.operator.api;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * The six calls the operator makes against an instance.
 *
 * <p>The first is not a JAX-RS resource on the other side and it shows: {@code /api/v1/auth/login}
 * is form authentication, handled by the security layer before anything of the application's runs.
 * So it takes form parameters rather than a body, and what it answers with that matters is the
 * {@code Set-Cookie} headers rather than the entity.
 */
@Path("/api/v1")
public interface KeydraEndpoint {

    @POST
    @Path("/auth/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Response signIn(@FormParam("username") String username, @FormParam("password") String password);

    @GET
    @Path("/connections")
    @Produces(MediaType.APPLICATION_JSON)
    List<ConnectionResponse> list(@HeaderParam("Cookie") String cookie);

    @POST
    @Path("/connections")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ConnectionResponse create(@HeaderParam("Cookie") String cookie, ConnectionRequest request);

    @PUT
    @Path("/connections/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    ConnectionResponse update(
            @HeaderParam("Cookie") String cookie,
            @PathParam("id") long id,
            ConnectionRequest request);

    @DELETE
    @Path("/connections/{id}")
    Response delete(@HeaderParam("Cookie") String cookie, @PathParam("id") long id);

    /** Probes a saved profile and returns what answered. */
    @POST
    @Path("/connections/{id}/test")
    @Produces(MediaType.APPLICATION_JSON)
    ConnectionStatus probe(@HeaderParam("Cookie") String cookie, @PathParam("id") long id);
}
