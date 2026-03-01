package com.example;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Simple CDI bean that provides greeting messages.
 *
 * This bean is discovered by ArC (Quarkus CDI) during augmentation
 * and will have a proxy generated for it.
 */
@ApplicationScoped
public class GreetingService {

    public String greet(String name) {
        return "Hello, " + name + "!";
    }

    public String getDefaultGreeting() {
        return "Hello from Quarkus (built with Bazel)!";
    }
}
