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

import com.blackbuild.klum.ast.util.FactoryHelper;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Restores JSON object state through the generated Builder lifecycle.
 *
 * <p>The map-shaped binding policy is provisional pending issue #428. It keeps
 * construction state out of completed models while that issue decides the
 * eventual interaction with advanced Jackson property customization.</p>
 */
final class KlumDeserializer extends StdDeserializer<Object> {

    private static final TypeReference<LinkedHashMap<String, Object>> STATE_TYPE = new TypeReference<>() { };

    private final Class<?> modelType;

    KlumDeserializer(Class<?> modelType) {
        super(modelType);
        this.modelType = modelType;
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL)
            return null;
        if (!parser.isExpectedStartObjectToken())
            return context.handleUnexpectedToken(modelType, parser);

        Map<String, Object> state = parser.readValueAs(STATE_TYPE);
        try {
            return FactoryHelper.createFromSerializedState(modelType, state);
        } catch (RuntimeException exception) {
            throw JsonMappingException.from(parser, "Could not restore " + modelType.getName() + " through its Builder lifecycle", exception);
        }
    }
}
