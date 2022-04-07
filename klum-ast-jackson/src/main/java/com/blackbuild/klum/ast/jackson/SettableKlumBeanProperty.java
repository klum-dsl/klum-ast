package com.blackbuild.klum.ast.jackson;

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;

import java.io.IOException;

public class SettableKlumBeanProperty extends SettableBeanProperty.Delegating {

    protected SettableKlumBeanProperty(SettableBeanProperty delegate) {
        super(delegate);
    }

    @Override
    protected SettableBeanProperty withDelegate(SettableBeanProperty d) {
        return new SettableKlumBeanProperty(d);
    }

    @Override
    public void deserializeAndSet(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
        deserializeSetAndReturn(p, ctxt, instance);
    }

    @Override
    public Object deserializeSetAndReturn(JsonParser p, DeserializationContext ctxt, Object instance) throws IOException {
        Object value;
        if (p.hasToken(JsonToken.VALUE_NULL)) {
            return null;
        } else if (_valueTypeDeserializer == null) {
            value = _valueDeserializer.deserialize(p, ctxt);
            if (value == null)
                return null;
        } else {
            value = _valueDeserializer.deserializeWithType(p, ctxt, _valueTypeDeserializer);
        }
        set(instance, value);
        return value;
    }

    @Override
    public void set(Object instance, Object value) throws IOException {
        setAndReturn(instance, value);
    }

    @Override
    public Object setAndReturn(Object instance, Object value) throws IOException {
        try {
            KlumInstanceProxy.getProxyFor(instance).setSingleField(getName(), value);
        } catch (Exception e) {
            _throwAsIOE(e, value);
        }
        return instance;
    }
}
