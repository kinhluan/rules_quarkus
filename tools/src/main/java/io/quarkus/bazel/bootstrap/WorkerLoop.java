package io.quarkus.bazel.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Bazel persistent worker loop (JSON protocol).
 *
 * Why: a large part of each augmentation is JVM startup and loading the Quarkus bootstrap
 * classes, a fixed cost paid once per application. A build that touches every application in a
 * repo pays it once per application. A worker JVM pays it once and keeps the JIT warm.
 *
 * What this requires of the rest of the tool: {@link BootstrapAugmentor#runOnce} must leave no
 * JVM-wide state behind, since the next request belongs to a different service. See the note on
 * that method.
 *
 * Protocol: Bazel writes a stream of WorkRequest JSON objects to stdin and reads WorkResponse
 * JSON objects from stdout. That makes stdout unusable for logging, so the loop redirects
 * System.out/System.err into a buffer and ships the buffer back in the response's "output"
 * field, which is what Bazel prints to the user.
 */
final class WorkerLoop {

    /** Fraction of max heap above which the worker retires rather than serve another request. */
    private static final double HEAP_WATERMARK = 0.8;

    private final PrintStream protocolOut;
    private final ByteArrayOutputStream capturedBytes;

    WorkerLoop() {
        this.protocolOut = System.out;
        this.capturedBytes = new ByteArrayOutputStream();

        // Installed once, for the worker's whole lifetime, and reset per request rather than
        // replaced. Anything that captures System.out at initialisation - a log manager, say -
        // then keeps a reference to a stream that stays live instead of a stale buffer from
        // whichever request happened to be first.
        PrintStream capture = new PrintStream(capturedBytes, true, StandardCharsets.UTF_8);
        System.setOut(capture);
        System.setErr(capture);
    }

    /**
     * True when Bazel started this process as a worker.
     *
     * Bazel appends {@code --persistent_worker} to the worker's startup command itself, and the
     * real arguments arrive per request because it expands the action's param file into
     * WorkRequest.arguments. So a worker starts with nothing but that flag, whereas the same
     * action run by the local or sandboxed strategy starts with the param file and no flag -
     * which keeps one-shot invocation working under {@code --strategy=QuarkusBootstrap=local}.
     *
     * The check tolerates the flag appearing more than once, because a rule that also passes it
     * explicitly is a realistic mistake and hanging on a WorkRequest that never comes is a
     * miserable way to find out.
     */
    static boolean isWorkerStartup(String[] args) {
        if (args.length == 0) {
            return false;
        }
        for (String arg : args) {
            if (!"--persistent_worker".equals(arg)) {
                return false;
            }
        }
        return true;
    }

    /** Drop {@code --persistent_worker} so one-shot mode sees only the real arguments. */
    static String[] stripWorkerFlag(String[] args) {
        int kept = 0;
        String[] out = new String[args.length];
        for (String arg : args) {
            if (!"--persistent_worker".equals(arg)) {
                out[kept++] = arg;
            }
        }
        if (kept == args.length) {
            return args;
        }
        String[] trimmed = new String[kept];
        System.arraycopy(out, 0, trimmed, 0, kept);
        return trimmed;
    }

    void run() throws IOException {
        PushbackReader in = new PushbackReader(new InputStreamReader(System.in, StandardCharsets.UTF_8), 8);

        while (true) {
            Object parsed = Json.parseValue(in);
            if (parsed == Json.EOF) {
                // Bazel closed stdin: the build is over, or this worker is being retired.
                return;
            }

            Map<String, Object> request = Json.asObject(parsed);
            int requestId = Json.intValue(request.get("requestId"));

            if (Boolean.TRUE.equals(request.get("cancel"))) {
                // Cancellation is only sent to workers that advertise support for it, which
                // this one does not. Answer correctly anyway rather than treat it as a request.
                respondCancelled(requestId);
                continue;
            }

            capturedBytes.reset();
            int exitCode = 0;
            try {
                List<String> arguments = Json.stringList(request.get("arguments"));
                BootstrapAugmentor.runOnce(arguments.toArray(String[]::new));
            } catch (Throwable t) {
                // Any failure is this request's failure, not the worker's: report a non-zero
                // exit code and stay alive for the next service. System.out is the captured
                // buffer here, so the stack trace reaches the user through the response.
                exitCode = 1;
                t.printStackTrace(System.out);
            }

            // print() without a newline does not auto-flush, so drain the PrintStream before
            // reading the bytes behind it - otherwise a trailing partial line goes missing.
            System.out.flush();
            respond(requestId, exitCode, capturedBytes.toString(StandardCharsets.UTF_8));

            if (heapAboveWatermark()) {
                // Augmentation holds a lot of short-lived state, and this JVM has already
                // answered. Retire instead of risking an OOM mid-request; Bazel starts a
                // replacement worker for the next one.
                return;
            }
        }
    }

    private void respond(int requestId, int exitCode, String output) {
        StringBuilder json = new StringBuilder(output.length() + 64);
        json.append("{\"exitCode\":").append(exitCode).append(",\"requestId\":").append(requestId)
            .append(",\"output\":");
        Json.writeString(json, output);
        json.append('}');
        protocolOut.print(json);
        protocolOut.flush();
    }

    private void respondCancelled(int requestId) {
        protocolOut.print("{\"requestId\":" + requestId + ",\"wasCancelled\":true}");
        protocolOut.flush();
    }

    private static boolean heapAboveWatermark() {
        Runtime runtime = Runtime.getRuntime();
        if (used(runtime) <= runtime.maxMemory() * HEAP_WATERMARK) {
            return false;
        }
        // Most of that is likely garbage from the augmentation that just finished. Only retire
        // if the heap is still full once it has been collected.
        System.gc();
        return used(runtime) > runtime.maxMemory() * HEAP_WATERMARK;
    }

    private static long used(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
