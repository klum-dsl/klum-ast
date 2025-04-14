/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import com.blackbuild.klum.ast.util.copy.OverwriteStrategy;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.FieldNode;

import java.lang.annotation.Annotation;

import static com.blackbuild.klum.common.CommonAstHelper.getNullSafeEnumMemberValue;
import static com.blackbuild.klum.common.CommonAstHelper.isCollectionOrMap;

public class OverwriteSingleCheck extends KlumCastCheck<Annotation> {

    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        FieldNode field = (FieldNode) target;
        OverwriteStrategy.Single strategy = getNullSafeEnumMemberValue(annotationToCheck, "single", OverwriteStrategy.Single.INHERIT);

        if (isCollectionOrMap(field.getType()))
            throw new IllegalArgumentException("Single overwrite strategy is not allowed for collections or maps");

        if (strategy == OverwriteStrategy.Single.MERGE && !DslAstHelper.isDSLObject(field.getType()))
            throw new IllegalArgumentException("MERGE is only allowed for DSL objects");
    }
}
