package io.quarkus.bazel.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the hand-rolled worker protocol pieces.
 *
 * These carry real risk: a protocol bug does not show up as a compile error but as a build
 * that hangs or a worker Bazel cannot talk to, so the parser is exercised directly.
 */
class WorkerProtocolTest {

    private static PushbackReader reader(String json) {
        return new PushbackReader(new StringReader(json), 8);
    }

    @Test
    @DisplayName("parses a WorkRequest and exposes arguments and requestId")
    void parsesWorkRequest() throws IOException {
        Map<String, Object> request = Json.asObject(Json.parseValue(reader(
            "{\"arguments\":[\"--output-dir\",\"out\"],\"inputs\":[{\"path\":\"a.jar\","
                + "\"digest\":\"ff\"}],\"requestId\":7,\"verbosity\":0}")));

        assertEquals(List.of("--output-dir", "out"), Json.stringList(request.get("arguments")));
        assertEquals(7, Json.intValue(request.get("requestId")));
        // Unknown-to-us fields must not break parsing; the protocol is allowed to grow.
        assertTrue(request.containsKey("inputs"));
    }

    @Test
    @DisplayName("reads consecutive requests from one unseparated stream")
    void readsConsecutiveRequests() throws IOException {
        // Bazel writes one JSON object after another with nothing in between, so the reader
        // must stop at the end of a value and leave the remainder for the next call.
        PushbackReader in = reader("{\"requestId\":1}{\"requestId\":2}\n{\"requestId\":3}");

        assertEquals(1, Json.intValue(Json.asObject(Json.parseValue(in)).get("requestId")));
        assertEquals(2, Json.intValue(Json.asObject(Json.parseValue(in)).get("requestId")));
        assertEquals(3, Json.intValue(Json.asObject(Json.parseValue(in)).get("requestId")));
        assertSame(Json.EOF, Json.parseValue(in));
    }

    @Test
    @DisplayName("tolerates pretty-printed requests")
    void tolerObjectsSpanningLines() throws IOException {
        Map<String, Object> request = Json.asObject(Json.parseValue(reader(
            "{\n  \"requestId\" : 4,\n  \"arguments\" : [\n    \"@params\"\n  ]\n}")));

        assertEquals(4, Json.intValue(request.get("requestId")));
        assertEquals(List.of("@params"), Json.stringList(request.get("arguments")));
    }

    @Test
    @DisplayName("missing fields fall back to protocol defaults")
    void missingFieldsUseDefaults() throws IOException {
        Map<String, Object> request = Json.asObject(Json.parseValue(reader("{}")));

        assertEquals(0, Json.intValue(request.get("requestId")));
        assertEquals(List.of(), Json.stringList(request.get("arguments")));
        assertFalse(Boolean.TRUE.equals(request.get("cancel")));
    }

    @Test
    @DisplayName("parses cancel requests, escapes and literals")
    void parsesRemainingValueKinds() throws IOException {
        Map<String, Object> request = Json.asObject(Json.parseValue(reader(
            "{\"cancel\":true,\"verbosity\":null,\"sandboxDir\":\"a\\\\b\\\"c\\u0041\\n\"}")));

        assertEquals(Boolean.TRUE, request.get("cancel"));
        assertEquals(null, request.get("verbosity"));
        assertEquals("a\\b\"cA\n", request.get("sandboxDir"));
    }

    @Test
    @DisplayName("rejects malformed JSON instead of silently mis-parsing")
    void rejectsMalformedJson() {
        assertThrows(IOException.class, () -> Json.parseValue(reader("{\"requestId\":")));
        assertThrows(IOException.class, () -> Json.parseValue(reader("{\"requestId\" 1}")));
        assertThrows(IOException.class, () -> Json.parseValue(reader("{\"a\":tru}")));
    }

    @Test
    @DisplayName("escapes output so a response stays one valid JSON object")
    void escapesResponseOutput() {
        StringBuilder out = new StringBuilder();
        Json.writeString(out, "line1\nquote \" backslash \\ tab\t bell\007");

        assertEquals("\"line1\\nquote \\\" backslash \\\\ tab\\t bell\\u0007\"", out.toString());
    }

    @Test
    @DisplayName("a captured log line survives the round trip through a response string")
    void outputRoundTrips() throws IOException {
        String captured = "Quarkus Bootstrap Augmentor (v2)\n  Detected 28 \"Quarkus\" extensions.\n";

        StringBuilder response = new StringBuilder("{\"exitCode\":0,\"output\":");
        Json.writeString(response, captured);
        response.append('}');

        Map<String, Object> parsed = Json.asObject(Json.parseValue(reader(response.toString())));
        assertEquals(captured, parsed.get("output"));
    }

    @Test
    @DisplayName("worker startup is only the bare flag, never the flag plus a param file")
    void detectsWorkerStartup() {
        assertTrue(WorkerLoop.isWorkerStartup(new String[] {"--persistent_worker"}));

        // Bazel appends the flag itself, so a rule that also passes it yields two copies.
        // That is still a worker; the earlier "exactly one argument" check got this wrong and
        // the worker exited on startup with "outputDir is required".
        assertTrue(WorkerLoop.isWorkerStartup(new String[] {"--persistent_worker", "--persistent_worker"}));

        // A param file means a non-worker strategy is running the action, and it must run
        // one-shot; otherwise the tool would sit waiting for WorkRequests that never arrive.
        assertFalse(WorkerLoop.isWorkerStartup(new String[] {"--persistent_worker", "@params"}));
        assertFalse(WorkerLoop.isWorkerStartup(new String[] {"@params"}));
        assertFalse(WorkerLoop.isWorkerStartup(new String[] {}));
    }

    @Test
    @DisplayName("stripping the worker flag leaves the param file as the only argument")
    void stripsWorkerFlag() {
        assertArrayEquals(new String[] {"@params"},
            WorkerLoop.stripWorkerFlag(new String[] {"--persistent_worker", "@params"}));

        String[] untouched = {"--output-dir", "out"};
        assertSame(untouched, WorkerLoop.stripWorkerFlag(untouched));
    }
}
