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
package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.DslHelper;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

public abstract class KlumAnnotationsValidator implements InstanceValidator {
    protected Object instance;
    protected String breadcrumbPath;
    protected boolean classHasValidateAnnotation;
    protected Class<?> currentType;
    protected KlumValidationResult validationResult;

    @Override
    public void validateInstance(Object instance, KlumValidationResult validationResult) {
        this.instance = instance;
        this.breadcrumbPath = validationResult.getBreadcrumbPath();
        this.validationResult = validationResult;

        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(this::validateType);
    }

    private void validateType(Class<?> type) {
        currentType = type;
        classHasValidateAnnotation = type.isAnnotationPresent(Validate.class);
        doValidateLayer();
    }

    protected abstract void doValidateLayer();


    protected Optional<KlumValidationIssue> withExceptionCheck(String memberName, Validate.Level level, Runnable runnable) {
        try {
            PhaseDriver.getContext().setMember(memberName);
            runnable.run();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new KlumValidationIssue(breadcrumbPath, memberName, e.getMessage(), e, level));
        } catch (AssertionError e) {
            return Optional.of(new KlumValidationIssue(breadcrumbPath, memberName, e.getMessage(), null, level));
        } finally {
            PhaseDriver.getContext().setMember(null);
        }
    }

    protected Validate getValidateAnnotationOrDefault(AnnotatedElement member) {
        Validate validate = member.getAnnotation(Validate.class);
        if (validate != null) return validate;
        return Validate.DefaultImpl.INSTANCE;
    }
}
