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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Role;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class KlumAnnotationIntrospector extends JacksonAnnotationIntrospector {
    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        if (m.hasAnnotation(Owner.class))
            return true;

        if (m.hasAnnotation(Role.class))
            return true;

        com.blackbuild.groovy.configdsl.transform.Field field =
                m.getAnnotation(com.blackbuild.groovy.configdsl.transform.Field.class);
        if (field != null && (field.value() == FieldType.IGNORED || field.value() == FieldType.BUILDER))
            return true;

        if (m.getName().contains("$"))
            return true;

        return super.hasIgnoreMarker(m);
    }

    @Override
    public JsonProperty.Access findPropertyAccess(Annotated annotated) {
        com.blackbuild.groovy.configdsl.transform.Field field =
                annotated.getAnnotation(com.blackbuild.groovy.configdsl.transform.Field.class);
        if (field != null && field.value() == FieldType.PROTECTED)
            return JsonProperty.Access.READ_ONLY;
        return super.findPropertyAccess(annotated);
    }
}
