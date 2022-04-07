/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBuilder;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.ValueInstantiators;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        context.addValueInstantiators(new KlumValueInstantiators());
        context.addBeanDeserializerModifier(new KlumDeserializerModifier());
        context.insertAnnotationIntrospector(new KlumAnnotationIntrospector());
    }

    public static class KlumValueInstantiators extends ValueInstantiators.Base {
        @Override
        public ValueInstantiator findValueInstantiator(DeserializationConfig config, BeanDescription beanDesc, ValueInstantiator defaultInstantiator) {
            if (!DslHelper.getKeyField(beanDesc.getBeanClass()).isPresent())
                return defaultInstantiator;

            return new KlumValueInstantiator((BasicBeanDescription) beanDesc);
        }
    }

    public static class KlumDeserializerModifier extends BeanDeserializerModifier {
        @Override
        public BeanDeserializerBuilder updateBuilder(DeserializationConfig config, BeanDescription beanDesc, BeanDeserializerBuilder builder) {

            Optional<Field> key = DslHelper.getKeyField(beanDesc.getBeanClass());
            if (!key.isPresent())
                return builder;

            Spliterator<SettableBeanProperty> spliterator = Spliterators.spliteratorUnknownSize(builder.getProperties(), Spliterator.ORDERED);

            List<SettableKlumBeanProperty> klumBeanProperties = StreamSupport.stream(spliterator, false).map(SettableKlumBeanProperty::new).collect(Collectors.toList());

            // need two steps, otherwise a concurrent modification would be thrown
            klumBeanProperties.forEach(it -> builder.addOrReplaceProperty(it, true));

            return builder;
        }
    }
}
