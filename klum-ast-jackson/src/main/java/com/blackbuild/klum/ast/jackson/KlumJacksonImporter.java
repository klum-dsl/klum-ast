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

import com.blackbuild.klum.ast.util.InternalKlumBuilder;
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumException;
import com.blackbuild.klum.ast.util.KlumFactory;
import com.blackbuild.klum.ast.util.KlumModelException;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
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

    /**
     * Captures {@link ObjectMapper#reader()} once without changing the mapper.
     *
     * @param mapper caller-configured mapper
     * @return immutable importer using the mapper's current reader configuration
     */
    public static KlumJacksonImporter using(ObjectMapper mapper) {
        return new KlumJacksonImporter(Objects.requireNonNull(mapper, "mapper").reader());
    }

    /**
     * Captures an untyped, non-updating reader while preserving all of its caller configuration.
     *
     * @param reader caller-configured untyped reader
     * @return immutable importer retaining that reader
     * @throws IllegalArgumentException if the reader is typed or updates an existing value
     */
    public static KlumJacksonImporter using(ObjectReader reader) {
        Objects.requireNonNull(reader, "reader");
        if (reader.getValueType() != null || valueToUpdate(reader) != null)
            throw new IllegalArgumentException("reader must be untyped and must not update an existing value");
        return new KlumJacksonImporter(reader);
    }

    /**
     * Reads one root DSL Object through its normal Builder lifecycle.
     *
     * @param type DSL model type
     * @param input one caller-owned Jackson input
     * @param <T> completed DSL model type
     * @return the completed root model
     */
    public <T> T readRoot(Class<T> type, KlumJacksonInput input) {
        return read(type, input, KlumDeserializer.ManagedMode.ROOT, null);
    }

    /**
     * Reads a value-only Template without running lifecycle callbacks or phases.
     *
     * @param type DSL model type
     * @param input one caller-owned Jackson input
     * @param <T> completed DSL model type
     * @return marked value-only Template
     */
    public <T> T readTemplate(Class<T> type, KlumJacksonInput input) {
        return read(type, input, KlumDeserializer.ManagedMode.TEMPLATE, null);
    }

    /**
     * Reads one owned Builder in the current Construction session without materializing it.
     *
     * @param factory generated Builder-producing factory
     * @param input one caller-owned Jackson input
     * @param <T> DSL model type
     * @param <B> precise generated Builder type
     * @return unsealed Builder from the active session
     */
    public <T, B extends KlumBuilder<T>> B readBuilder(KlumFactory.BuilderFactory<T, B> factory,
                                                        KlumJacksonInput input) {
        Objects.requireNonNull(factory, "factory");
        B builder = factory.FromMap(Collections.emptyMap());
        return read(internalBuilder(builder).getModelType(), input, KlumDeserializer.ManagedMode.BUILDER, builder);
    }

    /**
     * Applies one input to the same unsealed Builder in its active Construction session.
     *
     * @param builder active unsealed Builder
     * @param input one caller-owned Jackson input
     * @param <B> precise Builder type
     * @return the identical Builder instance
     */
    public <B extends KlumBuilder<?>> B applyToBuilder(B builder, KlumJacksonInput input) {
        Objects.requireNonNull(builder, "builder");
        InternalKlumBuilder<?> internalBuilder = internalBuilder(builder);
        PhaseDriver.requireCurrentConstructionSession(internalBuilder);
        return read(internalBuilder.getModelType(), input, KlumDeserializer.ManagedMode.APPLY, builder);
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
            if (!request.wasHandled() && mode == KlumDeserializer.ManagedMode.ROOT && hasTypeLevelCustomDeserializer(type))
                return result;
            if (!request.wasHandled())
                if (hasTypeLevelCustomDeserializer(type))
                    throw new KlumModelException("Jackson " + mode.operation()
                            + " import does not support a type-level custom deserializer for " + type.getName());
                else
                throw new KlumModelException("Jackson " + mode.operation() + " import requires KlumAstModule for "
                        + type.getName());
            return result;
        } catch (KlumException exception) {
            throw exception;
        } catch (IOException exception) {
            rethrowKlumException(exception);
            if (!request.wasHandled())
                throw missingModule(type, exception);
            throw importFailure(mode, type, input, exception);
        } catch (RuntimeException exception) {
            rethrowKlumException(exception);
            if (exception.getCause() instanceof KlumException klumException)
                throw klumException;
            throw importFailure(mode, type, input, exception);
        }
    }

    private static KlumModelException missingModule(Class<?> type, Throwable cause) {
        return new KlumModelException("Managed Jackson import requires KlumAstModule for " + type.getName(), cause);
    }

    private static InternalKlumBuilder<?> internalBuilder(KlumBuilder<?> builder) {
        if (builder instanceof InternalKlumBuilder<?> internalBuilder)
            return internalBuilder;
        throw new KlumModelException("Jackson Builder import requires an active generated Builder");
    }

    private static boolean hasTypeLevelCustomDeserializer(Class<?> type) {
        JsonDeserialize annotation = type.getAnnotation(JsonDeserialize.class);
        return annotation != null && annotation.using() != JsonDeserializer.None.class;
    }

    private static KlumModelException importFailure(KlumDeserializer.ManagedMode mode, Class<?> type,
                                                     KlumJacksonInput input, Throwable cause) {
        rethrowKlumException(cause);
        return new KlumModelException("Jackson " + mode.operation() + " import of " + type.getName()
                + " failed: " + conciseCause(cause) + " at " + diagnosticPath(type, mode, input, cause), cause);
    }

    private static void rethrowKlumException(Throwable failure) {
        if (failure instanceof KlumException exception)
            throw exception;
        if (failure.getCause() instanceof KlumException exception)
            throw exception;
    }

    private static String conciseCause(Throwable cause) {
        if (cause instanceof JsonProcessingException processingException)
            return processingException.getOriginalMessage();
        return cause.getMessage();
    }

    private static String diagnosticPath(Class<?> type, KlumDeserializer.ManagedMode mode, KlumJacksonInput input,
                                         Throwable cause) {
        return "$/" + type.getSimpleName() + "." + mode.operation() + ":jackson(" + input.sourceName() + ")"
                + inputPointer(cause) + syntaxLocation(cause);
    }

    private static String inputPointer(Throwable cause) {
        if (!(cause instanceof JsonMappingException mappingException) || mappingException.getPath().isEmpty())
            return "";
        String pointer = mappingException.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? escapePointer(reference.getFieldName())
                        : Integer.toString(reference.getIndex()))
                .reduce("", (path, segment) -> path + "/" + segment);
        return "/input(#" + pointer + ")";
    }

    private static String escapePointer(String value) {
        return value.replace("~", "~0").replace("/", "~1");
    }

    private static String syntaxLocation(Throwable cause) {
        if (!(cause instanceof JsonProcessingException processingException) || processingException.getLocation() == null)
            return "";
        var location = processingException.getLocation();
        if (location.getLineNr() < 1 || location.getColumnNr() < 1)
            return "";
        return ":line " + location.getLineNr() + ", column " + location.getColumnNr();
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
