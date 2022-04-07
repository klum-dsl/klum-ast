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

import java.io.IOException;
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
        if (!field.isPresent())
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
    public Object createFromObjectWith(DeserializationContext ctxt, Object[] args) throws IOException {
        return FactoryHelper.create(getValueClass(), null, (String) args[0], null);
    }
}