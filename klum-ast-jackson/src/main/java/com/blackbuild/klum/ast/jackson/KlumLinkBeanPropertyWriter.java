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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

/** Enforces the reference-only output contract of one Klum LINK property. */
final class KlumLinkBeanPropertyWriter extends BeanPropertyWriter {

    private final String schemaProperty;
    private final boolean hasReferenceStrategy;

    KlumLinkBeanPropertyWriter(BeanPropertyWriter delegate, String schemaProperty, boolean hasReferenceStrategy) {
        super(delegate);
        this.schemaProperty = schemaProperty;
        this.hasReferenceStrategy = hasReferenceStrategy;
    }

    @Override
    public void serializeAsField(Object bean, JsonGenerator generator, SerializerProvider serializers) throws Exception {
        assertReferenceStrategy(bean, generator);
        super.serializeAsField(bean, generator, serializers);
    }

    @Override
    public void serializeAsElement(Object bean, JsonGenerator generator, SerializerProvider serializers) throws Exception {
        assertReferenceStrategy(bean, generator);
        super.serializeAsElement(bean, generator, serializers);
    }

    private void assertReferenceStrategy(Object bean, JsonGenerator generator) throws Exception {
        if (get(bean) == null || hasReferenceStrategy)
            return;
        throw JsonMappingException.from(generator,
                KlumLinkDiagnostics.missingReferenceStrategy(schemaProperty, "serializer"));
    }
}
