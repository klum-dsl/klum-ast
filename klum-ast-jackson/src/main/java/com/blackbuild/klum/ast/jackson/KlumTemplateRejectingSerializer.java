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

import com.blackbuild.klum.ast.util.TemplateManager;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.ResolvableSerializer;

import java.io.IOException;

/** Preserves ordinary value serialization while preventing silent loss of Template recipe actions. */
final class KlumTemplateRejectingSerializer extends JsonSerializer<Object>
        implements ContextualSerializer, ResolvableSerializer {

    private final JsonSerializer<Object> delegate;

    @SuppressWarnings("unchecked")
    KlumTemplateRejectingSerializer(JsonSerializer<?> delegate) {
        this.delegate = (JsonSerializer<Object>) delegate;
    }

    @Override
    public void serialize(Object value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        rejectTemplate(value, generator);
        delegate.serialize(value, generator, serializers);
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator generator, SerializerProvider serializers,
                                  TypeSerializer typeSerializer) throws IOException {
        rejectTemplate(value, generator);
        delegate.serializeWithType(value, generator, serializers, typeSerializer);
    }

    private static void rejectTemplate(Object value, JsonGenerator generator) throws JsonMappingException {
        if (TemplateManager.isTemplate(value))
            throw JsonMappingException.from(generator, "Cannot serialize marked Template " + value.getClass().getName()
                    + " as JSON because JSON preserves values but not Template recipe actions. "
                    + "Rehydrate it through Template.With or another Template/copy API and serialize the completed model instead.");
    }

    @Override
    public JsonSerializer<?> createContextual(SerializerProvider provider, BeanProperty property)
            throws JsonMappingException {
        JsonSerializer<?> contextualDelegate = provider.handlePrimaryContextualization(delegate, property);
        if (contextualDelegate == delegate)
            return this;
        return new KlumTemplateRejectingSerializer(contextualDelegate);
    }

    @Override
    public void resolve(SerializerProvider provider) throws JsonMappingException {
        if (delegate instanceof ResolvableSerializer resolvableSerializer)
            resolvableSerializer.resolve(provider);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Object value) {
        return delegate.isEmpty(provider, value);
    }

    @Override
    public Class<Object> handledType() {
        return delegate.handledType();
    }

    @Override
    public JsonSerializer<?> getDelegatee() {
        return delegate;
    }
}
