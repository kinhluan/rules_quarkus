package com.example.resource;

import com.example.model.ApiResponse;
import com.example.model.User;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tier 1 Demo: REST + Jackson + Mutiny
 *
 * Endpoints:
 * - GET  /api/users          - List all users (JSON)
 * - GET  /api/users/{id}     - Get user by ID
 * - POST /api/users          - Create user (JSON body)
 * - GET  /api/users/reactive - Reactive response with Mutiny
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserResource {

    private static final String TIER = "Tier 1: REST + Jackson + Mutiny";
    private static final Map<Long, User> users = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(1);

    static {
        // Sample data
        users.put(1L, new User(1L, "Alice", "alice@example.com"));
        users.put(2L, new User(2L, "Bob", "bob@example.com"));
        idGenerator.set(3);
    }

    @GET
    public ApiResponse<List<User>> listUsers() {
        return ApiResponse.success(List.copyOf(users.values()), TIER);
    }

    @GET
    @Path("/{id}")
    public ApiResponse<User> getUser(@PathParam("id") Long id) {
        User user = users.get(id);
        if (user == null) {
            return ApiResponse.error("User not found: " + id, TIER);
        }
        return ApiResponse.success(user, TIER);
    }

    @POST
    public ApiResponse<User> createUser(User user) {
        Long id = idGenerator.getAndIncrement();
        user.setId(id);
        users.put(id, user);
        return ApiResponse.success(user, TIER);
    }

    @DELETE
    @Path("/{id}")
    public ApiResponse<String> deleteUser(@PathParam("id") Long id) {
        User removed = users.remove(id);
        if (removed == null) {
            return ApiResponse.error("User not found: " + id, TIER);
        }
        return ApiResponse.success("Deleted user: " + id, TIER);
    }

    /**
     * Demo Mutiny reactive response
     * Simulates async operation with delay
     */
    @GET
    @Path("/reactive")
    public Uni<ApiResponse<List<User>>> listUsersReactive() {
        return Uni.createFrom().item(() -> List.copyOf(users.values()))
                .onItem().delayIt().by(Duration.ofMillis(100))
                .onItem().transform(list -> ApiResponse.success(list, TIER + " (Reactive)"));
    }

    /**
     * Demo Mutiny with transformation
     */
    @GET
    @Path("/reactive/{id}")
    public Uni<ApiResponse<User>> getUserReactive(@PathParam("id") Long id) {
        return Uni.createFrom().item(() -> users.get(id))
                .onItem().ifNull().failWith(() -> new NotFoundException("User not found: " + id))
                .onItem().transform(user -> ApiResponse.success(user, TIER + " (Reactive)"));
    }
}
