package io.quarkus.bazel.bootstrap;

import java.io.IOException;
import java.io.PushbackReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal streaming JSON reader/writer for Bazel's worker protocol.
 *
 * Hand-rolled on purpose. The alternatives both put a JAR on the augmentor's system
 * classloader: Gson/Jackson for JSON, or protobuf-java for the binary worker protocol. Every
 * JAR there is one more candidate for the child-first class identity clash described in
 * ApplicationModelFactory.SYSTEM_CL_ARTIFACTS - and protobuf-java in particular is already a
 * runtime dependency of the services being augmented.
 *
 * Only what the protocol needs is implemented: objects, arrays, strings, numbers, booleans and
 * null. Parsing is streaming because Bazel writes a continuous stream of JSON objects with no
 * separator between them, so the reader must stop at the end of one value and leave the rest
 * for the next call.
 */
final class Json {

    /** Returned by {@link #parseValue} when the stream is exhausted at a value boundary. */
    static final Object EOF = new Object();

    private Json() {
    }

    /**
     * Parse exactly one JSON value, consuming nothing past it.
     * Returns {@link #EOF} if the stream ends before a value starts.
     */
    static Object parseValue(PushbackReader in) throws IOException {
        int c = skipWhitespace(in);
        if (c == -1) {
            return EOF;
        }
        in.unread(c);
        return readValue(in);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asObject(Object value) {
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("expected a JSON object, got: " + value);
        }
        return (Map<String, Object>) value;
    }

    /** Read a field as a list of strings; missing or null yields an empty list. */
    static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("expected a JSON array, got: " + value);
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object item : list) {
            out.add(String.valueOf(item));
        }
        return out;
    }

    /** Read a field as an int; missing or null yields 0, matching the protocol's defaults. */
    static int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    /** Append {@code value} to {@code out} as a quoted JSON string. */
    static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private static Object readValue(PushbackReader in) throws IOException {
        int c = skipWhitespace(in);
        return switch (c) {
            case '{' -> readObject(in);
            case '[' -> readArray(in);
            case '"' -> readString(in);
            case 't' -> readLiteral(in, "rue", Boolean.TRUE);
            case 'f' -> readLiteral(in, "alse", Boolean.FALSE);
            case 'n' -> readLiteral(in, "ull", null);
            case -1 -> throw new IOException("Unexpected end of JSON input");
            default -> readNumber(in, c);
        };
    }

    private static Map<String, Object> readObject(PushbackReader in) throws IOException {
        Map<String, Object> obj = new LinkedHashMap<>();
        int c = skipWhitespace(in);
        if (c == '}') {
            return obj;
        }
        in.unread(c);
        while (true) {
            c = skipWhitespace(in);
            if (c != '"') {
                throw new IOException("Expected a field name in JSON object, got: " + describe(c));
            }
            String name = readString(in);
            c = skipWhitespace(in);
            if (c != ':') {
                throw new IOException("Expected ':' after field " + name + ", got: " + describe(c));
            }
            obj.put(name, readValue(in));
            c = skipWhitespace(in);
            if (c == '}') {
                return obj;
            }
            if (c != ',') {
                throw new IOException("Expected ',' or '}' in JSON object, got: " + describe(c));
            }
        }
    }

    private static List<Object> readArray(PushbackReader in) throws IOException {
        List<Object> arr = new ArrayList<>();
        int c = skipWhitespace(in);
        if (c == ']') {
            return arr;
        }
        in.unread(c);
        while (true) {
            arr.add(readValue(in));
            c = skipWhitespace(in);
            if (c == ']') {
                return arr;
            }
            if (c != ',') {
                throw new IOException("Expected ',' or ']' in JSON array, got: " + describe(c));
            }
        }
    }

    /** Reads the body of a string; the opening quote has already been consumed. */
    private static String readString(PushbackReader in) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c == -1) {
                throw new IOException("Unexpected end of JSON input inside a string");
            }
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append((char) c);
                continue;
            }
            int esc = in.read();
            switch (esc) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    char[] hex = new char[4];
                    for (int i = 0; i < 4; i++) {
                        int h = in.read();
                        if (h == -1) {
                            throw new IOException("Unexpected end of JSON input inside a \\u escape");
                        }
                        hex[i] = (char) h;
                    }
                    sb.append((char) Integer.parseInt(new String(hex), 16));
                }
                case -1 -> throw new IOException("Unexpected end of JSON input after '\\'");
                default -> throw new IOException("Invalid JSON escape: \\" + (char) esc);
            }
        }
    }

    private static Object readLiteral(PushbackReader in, String rest, Object value) throws IOException {
        for (int i = 0; i < rest.length(); i++) {
            int c = in.read();
            if (c != rest.charAt(i)) {
                throw new IOException("Invalid JSON literal, expected '" + rest.charAt(i) + "', got: " + describe(c));
            }
        }
        return value;
    }

    private static Object readNumber(PushbackReader in, int first) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append((char) first);
        while (true) {
            int c = in.read();
            if (c == -1) {
                break;
            }
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                sb.append((char) c);
                continue;
            }
            in.unread(c);
            break;
        }
        String text = sb.toString();
        try {
            // Long where it fits, so requestId round-trips exactly.
            if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
                return Long.valueOf(text);
            }
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid JSON number: " + text, e);
        }
    }

    private static int skipWhitespace(PushbackReader in) throws IOException {
        while (true) {
            int c = in.read();
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                continue;
            }
            return c;
        }
    }

    private static String describe(int c) {
        return c == -1 ? "end of input" : "'" + (char) c + "'";
    }
}
