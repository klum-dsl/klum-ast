package com.blackbuild.klum.ast.jackson;

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class KlumAnnotationIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        if (m.hasAnnotation(Owner.class))
            return true;

        if (m.getName().contains("$"))
            return true;

        return super.hasIgnoreMarker(m);
    }
}
