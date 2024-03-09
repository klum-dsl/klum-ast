/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
import com.blackbuild.klum.ast.util.FactoryHelper;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.deser.CreatorProperty;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.introspect.BasicBeanDescription;

import java.lang.reflect.Field;
import java.util.Optional;

public class KlumValueInstantiator extends ValueInstantiator.Base {

    KlumValueInstantiator(BasicBeanDescription beanDesc) {
        super(beanDesc.getType());
    }

    @Override
    public boolean canCreateFromObjectWith() {
        return true;
    }

    @Override
    public SettableBeanProperty[] getFromObjectArguments(DeserializationConfig config) {
        Optional<Field> field = DslHelper.getKeyField(getValueClass());
        if (field.isEmpty())
            throw new IllegalStateException("KlumValueInstantiator is only valid for keyed objects.");
        CreatorProperty prop = CreatorProperty.construct(
                new PropertyName(field.get().getName()),
                config.getTypeFactory().constructType(field.get().getType()),
                null,
                null,
                null,
                null,
                0,
                null,
                null
        );
        return new SettableBeanProperty[] {prop};
    }

    @Override
    public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) {
        return FactoryHelper.createAsStub(getValueClass(), (String) args[0]);
    }
}