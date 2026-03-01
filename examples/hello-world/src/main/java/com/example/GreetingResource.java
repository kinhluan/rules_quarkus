package com.example;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * JAX-RS resource for greeting endpoints.
 *
 * This class demonstrates:
 * - CDI injection (@Inject)
 * - JAX-RS endpoints (@Path, @GET)
 * - Path parameters (@PathParam)
 */
@Path("/hello")
public class GreetingResource {

    @Inject
    GreetingService greetingService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return greetingService.getDefaultGreeting();
    }

    @GET
    @Path("/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public String helloName(@PathParam("name") String name) {
        return greetingService.greet(name);
    }
}
