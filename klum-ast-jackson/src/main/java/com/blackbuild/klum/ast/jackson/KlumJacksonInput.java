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
package com.blackbuild.klum.ast.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * One caller-owned Jackson input for a {@link KlumJacksonImporter} operation.
 *
 * <p>A parser remains owned by its caller and is neither rewound nor closed. Tree and map inputs are read without
 * changing the supplied value. Call {@link #named(String)} to attach an opaque source name to diagnostics.</p>
 */
public final class KlumJacksonInput {

    private final JsonParser parser;
    private final JsonNode tree;
    private final Map<?, ?> map;
    private final String source;

    private KlumJacksonInput(JsonParser parser, JsonNode tree, Map<?, ?> map, String source) {
        this.parser = parser;
        this.tree = tree;
        this.map = map;
        this.source = source;
    }

    /** Borrows one single-pass parser. The importer never closes it. */
    public static KlumJacksonInput parser(JsonParser parser) {
        return new KlumJacksonInput(Objects.requireNonNull(parser, "parser"), null, null, null);
    }

    /** Uses an immutable view of a caller-owned Jackson tree. */
    public static KlumJacksonInput tree(JsonNode node) {
        return new KlumJacksonInput(null, Objects.requireNonNull(node, "node"), null, null);
    }

    /** Uses a caller-owned Map without mutating it. */
    public static KlumJacksonInput map(Map<?, ?> values) {
        return new KlumJacksonInput(null, null, Objects.requireNonNull(values, "values"), null);
    }

    /** Returns this input with an opaque source identity used only for diagnostics. */
    public KlumJacksonInput named(String source) {
        return new KlumJacksonInput(parser, tree, map, Objects.requireNonNull(source, "source"));
    }

    InputParser open(ObjectReader reader) throws IOException {
        if (parser != null)
            return new InputParser(parser, false);
        if (tree != null)
            return new InputParser(tree.traverse(reader.getFactory().getCodec()), true);
        JsonNode mapTree = new ObjectMapper().valueToTree(map);
        return new InputParser(mapTree.traverse(reader.getFactory().getCodec()), true);
    }

    String sourceName() {
        if (source != null)
            return source;
        if (parser != null)
            return "parser";
        if (tree != null)
            return "tree";
        return "map";
    }

    record InputParser(JsonParser parser, boolean closeWhenDone) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            if (closeWhenDone)
                parser.close();
        }
    }
}
