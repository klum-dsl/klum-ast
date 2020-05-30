package com.blackbuild.klum.ast.internal.model;

import org.codehaus.groovy.ast.FieldNode;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.blackbuild.klum.ast.internal.model.ModelHelper.compilerError;
import static java.lang.String.format;

public class FieldFactory {

    private static FieldFactory instance = new FieldFactory();

    private FieldFactory() {
    }

    static FieldFactory getInstance() {
        return instance;
    }

    public static KlumField toKlumField(FieldNode node) {
        FieldContainer container = node.getNodeMetaData(FieldContainer.class);
        if (container != null) return container;

        return Stream.of(Type.values())
                .map(it -> it.create(node))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() ->
                        compilerError(
                                format("Unknown field type (%s#%s)", node.getOwner().getName(), node.getName()),
                                node)
                );
    }

    enum Type {
        KEY_FIELD(KeyField::from),
        COLLECTION_FIELD(CollectionField::from);

        private final Function<FieldNode, KlumField> factory;

        Type(Function<FieldNode, KlumField> factory) {
            this.factory = factory;
        }

        public KlumField create(FieldNode node) {
            return factory.apply(node);
        }
    }
}
