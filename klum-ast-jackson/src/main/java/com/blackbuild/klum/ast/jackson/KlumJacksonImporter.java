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

import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumException;
import com.blackbuild.klum.ast.util.KlumFactory;
import com.blackbuild.klum.ast.util.KlumModelException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Explicit, Builder-first Jackson import operations.
 *
 * <p>The importer captures one untyped {@link ObjectReader}; it never mutates a mapper or registers a module. Configure
 * Jackson, including {@link KlumAstModule}, before creating it. Each method consumes exactly one
 * {@link KlumJacksonInput}; parser lifecycle remains with the caller.</p>
 */
public final class KlumJacksonImporter {

    private final ObjectReader reader;

    private KlumJacksonImporter(ObjectReader reader) {
        this.reader = reader;
    }

    /** Captures {@link ObjectMapper#reader()} once without changing the mapper. */
    public static KlumJacksonImporter using(ObjectMapper mapper) {
        return new KlumJacksonImporter(Objects.requireNonNull(mapper, "mapper").reader());
    }

    /** Captures an untyped, non-updating reader while preserving all of its caller configuration. */
    public static KlumJacksonImporter using(ObjectReader reader) {
        Objects.requireNonNull(reader, "reader");
        if (reader.getValueType() != null || valueToUpdate(reader) != null)
            throw new IllegalArgumentException("reader must be untyped and must not update an existing value");
        return new KlumJacksonImporter(reader);
    }

    /** Reads one root DSL Object through its normal Builder lifecycle. */
    public <T> T readRoot(Class<T> type, KlumJacksonInput input) {
        return read(type, input, KlumDeserializer.ManagedMode.ROOT, null);
    }

    /** Reads a value-only Template without running lifecycle callbacks or phases. */
    public <T> T readTemplate(Class<T> type, KlumJacksonInput input) {
        return read(type, input, KlumDeserializer.ManagedMode.TEMPLATE, null);
    }

    /** Reads one owned Builder in the current Construction session without materializing it. */
    public <T, B extends KlumBuilder<T>> B readBuilder(KlumFactory.BuilderFactory<T, B> factory,
                                                        KlumJacksonInput input) {
        Objects.requireNonNull(factory, "factory");
        return read(factory.$modelTypeForImport(), input, KlumDeserializer.ManagedMode.BUILDER,
                factory.$createForImport());
    }

    /** Applies one input to the same unsealed Builder in its active Construction session. */
    public <B extends KlumBuilder<?>> B applyToBuilder(B builder, KlumJacksonInput input) {
        Objects.requireNonNull(builder, "builder");
        return read(builder.getModelType(), input, KlumDeserializer.ManagedMode.APPLY, builder);
    }

    @SuppressWarnings("unchecked")
    private <T, R> R read(Class<T> type, KlumJacksonInput input, KlumDeserializer.ManagedMode mode,
                          KlumBuilder<?> target) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(input, "input");
        KlumDeserializer.ManagedRequest request = new KlumDeserializer.ManagedRequest(mode, target);
        try (KlumJacksonInput.InputParser source = input.open(reader)) {
            JsonParser parser = source.parser();
            R result = (R) KlumDeserializer.withManagedRequest(request,
                    () -> reader.forType(type).readValue(parser));
            if (!request.wasHandled())
                throw new KlumModelException("Jackson " + mode.operation() + " import requires KlumAstModule for "
                        + type.getName());
            return result;
        } catch (KlumException exception) {
            throw exception;
        } catch (IOException exception) {
            if (!request.wasHandled())
                throw missingModule(type, exception);
            throw importFailure(mode, type, input, exception);
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof KlumException klumException)
                throw klumException;
            throw importFailure(mode, type, input, exception);
        }
    }

    private static KlumModelException missingModule(Class<?> type, Throwable cause) {
        return new KlumModelException("Managed Jackson import requires KlumAstModule for " + type.getName(), cause);
    }

    private static KlumModelException importFailure(KlumDeserializer.ManagedMode mode, Class<?> type,
                                                     KlumJacksonInput input, Throwable cause) {
        Throwable directCause = cause instanceof JsonMappingException mapping && mapping.getCause() != null
                ? mapping.getCause()
                : cause;
        return new KlumModelException("Jackson " + mode.operation() + " import of " + type.getName()
                + " failed: " + cause.getMessage() + " at " + diagnosticPath(type, mode, input), directCause);
    }

    private static String diagnosticPath(Class<?> type, KlumDeserializer.ManagedMode mode, KlumJacksonInput input) {
        return "$/" + type.getSimpleName() + "." + mode.operation() + ":jackson(" + input.sourceName() + ")";
    }

    private static Object valueToUpdate(ObjectReader reader) {
        try {
            Field field = ObjectReader.class.getDeclaredField("_valueToUpdate");
            if (!field.trySetAccessible())
                throw new IllegalArgumentException("reader value-to-update configuration cannot be inspected");
            return field.get(reader);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("reader value-to-update configuration cannot be inspected", exception);
        }
    }
}
