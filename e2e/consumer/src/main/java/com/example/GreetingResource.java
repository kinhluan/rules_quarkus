package com.example;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
public class GreetingResource {

    @ApplicationScoped
    public static class Greeter {
        String greet() {
            return "Hello from a rules_quarkus consumer!";
        }
    }

    @Inject
    Greeter greeter;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return greeter.greet();
    }
}
