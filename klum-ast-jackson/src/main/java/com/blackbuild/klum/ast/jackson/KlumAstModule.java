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

import com.blackbuild.klum.ast.util.DslHelper;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

public class KlumAstModule extends Module {

    public static final String MODULE_NAME = "KlumAST";

    @Override
    public String getModuleName() {
        return MODULE_NAME;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
        context.addBeanDeserializerModifier(new KlumDeserializerModifier());
        context.addBeanSerializerModifier(new KlumSerializerModifier());
        context.insertAnnotationIntrospector(new KlumAnnotationIntrospector());
    }

    public static class KlumDeserializerModifier extends BeanDeserializerModifier {
        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config, BeanDescription beanDesc,
                                                       JsonDeserializer<?> deserializer) {
            Class<?> handledType = deserializer.handledType();
            Class<?> modelType = DslHelper.isDslType(beanDesc.getBeanClass())
                    ? beanDesc.getBeanClass()
                    : builderTargetType(beanDesc, handledType);
            if (modelType == null || !DslHelper.isDslType(modelType))
                return deserializer;
            return new KlumDeserializer(modelType, deserializer);
        }

        private static Class<?> builderTargetType(BeanDescription beanDesc, Class<?> fallback) {
            JsonPOJOBuilder.Value builderConfig = beanDesc.findPOJOBuilderConfig();
            String buildMethodName = builderConfig == null
                    ? JsonPOJOBuilder.DEFAULT_BUILD_METHOD
                    : builderConfig.buildMethodName;
            var buildMethod = beanDesc.findMethod(buildMethodName, null);
            return buildMethod == null ? fallback : buildMethod.getRawReturnType();
        }
    }

    public static class KlumSerializerModifier extends BeanSerializerModifier {
        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc,
                                                         List<BeanPropertyWriter> beanProperties) {
            if (!DslHelper.isDslType(beanDesc.getBeanClass()))
                return beanProperties;
            return beanProperties.stream()
                    .map(writer -> linkWriter(config, beanDesc, writer))
                    .collect(Collectors.toList());
        }

        private static BeanPropertyWriter linkWriter(SerializationConfig config, BeanDescription beanDesc,
                                                     BeanPropertyWriter writer) {
            AnnotatedMember member = writer.getMember();
            if (member == null)
                return writer;
            BeanPropertyDefinition property = beanDesc.findProperties().stream()
                    .filter(candidate -> candidate.getName().equals(writer.getName()))
                    .findFirst()
                    .orElse(null);
            if (property == null)
                return writer;
            Field schemaField = DslHelper.getField(beanDesc.getBeanClass(), property.getInternalName()).orElse(null);
            if (schemaField == null || !DslHelper.isLink(schemaField))
                return writer;
            var introspector = config.getAnnotationIntrospector();
            var referenceInfo = introspector.findObjectReferenceInfo(member, null);
            boolean alwaysAsId = referenceInfo != null && referenceInfo.getAlwaysAsId();
            var propertyType = config.getTypeFactory().constructType(schemaField.getGenericType());
            var targetType = propertyType.isContainerType() ? propertyType.getContentType() : propertyType;
            boolean targetHasIdentity = targetType != null
                    && config.introspect(targetType).getObjectIdInfo() != null;
            boolean customSerializer = introspector.findSerializer(member) != null;
            return new KlumLinkBeanPropertyWriter(
                    writer,
                    schemaField.getDeclaringClass().getName() + "." + schemaField.getName(),
                    customSerializer || alwaysAsId && targetHasIdentity
            );
        }

        @Override
        public JsonSerializer<?> modifySerializer(SerializationConfig config, BeanDescription beanDesc,
                                                   JsonSerializer<?> serializer) {
            if (!DslHelper.isDslType(beanDesc.getBeanClass()))
                return serializer;
            return new KlumTemplateRejectingSerializer(serializer);
        }
    }
}
