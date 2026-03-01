package com.example;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Main entry point for the Quarkus application.
 *
 * The @QuarkusMain annotation tells Quarkus to use this class
 * instead of the generated GeneratedMain.
 *
 * Note: In most cases, you don't need this class - Quarkus
 * generates a main class automatically. This is included
 * for demonstration purposes.
 */
@QuarkusMain
public class Main {

    public static void main(String[] args) {
        System.out.println("Starting Quarkus application...");
        Quarkus.run(args);
    }
}
