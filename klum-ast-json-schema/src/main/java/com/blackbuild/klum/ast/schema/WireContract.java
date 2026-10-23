/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast.schema;

import org.objectweb.asm.Type;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Private decoding of the current compiler ABI, not a portable descriptor protocol. */
final class WireContract {
    static final String BUILDER_SUFFIX = "_DSL$Builder";
    private static final Set<String> COLLECTIONS = Set.of("java.util.List", "java.util.Set", "java.util.SortedSet",
            "java.util.NavigableSet", "java.util.EnumSet");
    private static final Set<String> MAPS = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap");
    final List<Entry> entries = new ArrayList<>();
    final List<Rejection> rejections = new ArrayList<>();

    WireContract(String data, String model, String builder) {
        String[] lines = data.split("\n", -1);
        if (lines.length < 3 || !lines[0].equals("WIRE-PLAN/B") || !lines[lines.length - 1].isEmpty())
            throw invalid("Malformed current WIRE-PLAN/B header or terminator");
        String[] identity = lines[1].split("\\|", -1);
        if (identity.length != 3 || !identity[0].equals("model") || !model.equals(token(identity[1])) || !builder.equals(token(identity[2])))
            throw invalid("Carrier Model/Builder identity mismatch");
        for (int i = 2; i < lines.length - 1; i++) {
            String[] fields = lines[i].split("\\|", -1);
            if (fields.length == 3 && fields[0].equals("rejection")) rejections.add(new Rejection(required(fields[1]), required(fields[2])));
            else if (fields.length == 12 && fields[0].equals("entry")) entries.add(entry(fields, builder));
            else throw invalid("Malformed current twelve-token carrier record " + i);
        }
    }

    private static Entry entry(String[] fields, String builder) {
        Role role = Role.valueOf(fields[2]);
        String source = required(fields[3]);
        String owner = required(fields[4]);
        if (!owner.equals(builder) || !source.startsWith(model(owner) + "#") || source.length() == model(owner).length() + 1)
            throw invalid("Entry source does not belong to its declaring Builder: " + source);
        String operation = required(fields[5]);
        List<String> descriptors = List.of(required(fields[6]).split(",(?=" + Pattern.quote(operation) + "\\()", -1));
        if (descriptors.stream().distinct().count() != descriptors.size()) throw invalid("Duplicate selected descriptor at " + source);
        List<String> normalized = descriptors.stream().map(WireContract::normalizeDescriptor).toList();
        if (!Set.of("IDENTITY", "JSON_PROPERTY").contains(fields[7])) throw invalid("Unknown name origin at " + source);
        Parser parser = new Parser(fields[10]);
        Payload payload = parser.parse();
        if (parser.position != fields[10].length()) throw invalid("Trailing payload data at " + source);
        payload.validate(role == Role.PROPERTY);
        if ((role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP) != (payload.kind() == Kind.NESTED_OBJECT))
            throw invalid("Role/payload mismatch at " + source);
        String keyType = token(fields[8]);
        String excludedKey = token(fields[9]);
        String fixedKey = token(fields[11]);
        if (role == Role.KEYED_OWNED_MAP && (!"java.lang.String".equals(keyType) || excludedKey == null || excludedKey.isEmpty()))
            throw invalid("Keyed relationship lacks its String key at " + source);
        if (fixedKey != null && (fixedKey.isEmpty() || role != Role.OWNED_OBJECT))
            throw invalid("Fixed key requires keyed OWNED_OBJECT at " + source);
        return new Entry(required(fields[1]), role, source, owner, operation, normalized, payload, keyType, excludedKey, fixedKey);
    }

    static String model(String builder) {
        if (!builder.endsWith(BUILDER_SUFFIX)) throw invalid("Not a generated Builder identity: " + builder);
        return builder.substring(0, builder.length() - BUILDER_SUFFIX.length());
    }

    // Current carriers contain both reflected array binary names and generated array source names.
    private static String normalizeDescriptor(String descriptor) {
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf("):");
        if (start < 1 || end < start || descriptor.length() == end + 2) throw invalid("Malformed selected descriptor " + descriptor);
        String parameters = descriptor.substring(start + 1, end);
        List<String> types = parameters.isEmpty() ? List.of() : List.of(parameters.split(", ", -1));
        return descriptor.substring(0, start + 1) + String.join(", ", types.stream().map(WireContract::arrayName).toList())
                + "):" + arrayName(descriptor.substring(end + 2));
    }

    private static String arrayName(String name) { return name.startsWith("[") ? Type.getType(name.replace('.', '/')).getClassName() : name; }

    private static String required(String encoded) {
        String value = token(encoded);
        if (value == null || value.isEmpty()) throw invalid("Missing required carrier token");
        return value;
    }

    private static String token(String encoded) {
        if (encoded.equals("-")) return null;
        String result = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(result.getBytes(StandardCharsets.UTF_8)).equals(encoded))
            throw invalid("Noncanonical carrier token");
        return result;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    enum Role { PROPERTY, OPERATION, OWNED_OBJECT, KEYED_OWNED_MAP }
    enum Kind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    record Entry(String name, Role role, String source, String owner, String operation, List<String> descriptors,
                 Payload payload, String keyType, String excludedKey, String fixedKey) { }
    record Rejection(String source, String reason) { }
    record Payload(Kind kind, String type, String builder, String keyType, Payload item) {
        void validate(boolean field) {
            if ((kind == Kind.LIST || kind == Kind.MAP) != (item != null)) throw invalid("Invalid payload item");
            if (kind == Kind.LIST && !(field ? COLLECTIONS.contains(type) : type.equals("java.util.List")))
                throw invalid("Unsupported admitted collection " + type);
            if (kind == Kind.MAP && (!(field ? MAPS.contains(type) : type.equals("java.util.Map")) || !"java.lang.String".equals(keyType)))
                throw invalid("Unsupported admitted data map " + type);
            if (kind == Kind.NESTED_OBJECT && (!((type + BUILDER_SUFFIX).equals(builder)) || item != null))
                throw invalid("Invalid nested Builder identity");
            if (kind != Kind.NESTED_OBJECT && builder != null) throw invalid("Unexpected payload Builder identity");
            if (kind != Kind.MAP && kind != Kind.NESTED_OBJECT && keyType != null) throw invalid("Unexpected payload key type");
            if (item != null) {
                if (item.kind() == Kind.NESTED_OBJECT) throw invalid("Owned objects hidden in ordinary containers");
                item.validate(false);
            }
        }
    }

    private static final class Parser {
        private final String text;
        private int position;
        private Parser(String text) { this.text = text; }
        private Payload parse() {
            take('[');
            Kind kind = Kind.valueOf(atom());
            take(',');
            String type = required(atom());
            take(',');
            String builder = token(atom());
            take(',');
            String key = token(atom());
            Payload item = null;
            if (peek() == ',') { take(','); item = parse(); }
            take(']');
            return new Payload(kind, type, builder, key, item);
        }
        private String atom() {
            int start = position;
            while (position < text.length() && text.charAt(position) != ',' && text.charAt(position) != ']') position++;
            if (position == start) throw invalid("Missing payload atom");
            return text.substring(start, position);
        }
        private char peek() {
            if (position >= text.length()) throw invalid("Truncated payload");
            return text.charAt(position);
        }
        private void take(char expected) {
            if (peek() != expected) throw invalid("Malformed payload; expected " + expected);
            position++;
        }
    }
}
