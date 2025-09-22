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
package com.blackbuild.klum.ast.util;

import com.blackbuild.annodocimal.annotations.AnnoDoc;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
import com.blackbuild.klum.ast.util.layer3.ClusterModel;
import com.blackbuild.klum.ast.util.layer3.annotations.Notify;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;

public class EarlyValidationPhase extends VisitingPhaseAction {

    public EarlyValidationPhase() {
        super(DefaultKlumPhase.EARLY_VALIDATE);
    }

    @Override
    public void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        markDeprecateUnsetFields(element);
        markFieldsWithNotify(element);
    }

    private void markFieldsWithNotify(@NotNull Object element) {
        ClusterModel.getFieldsAnnotatedWith(element, Notify.class)
                .forEach((key, value) -> checkNotifyAnnotation(element, key, value));
    }

    private void checkNotifyAnnotation(@NotNull Object element, String fieldName, Object value) {
        Field field = ClusterModel.getField(element, fieldName).orElseThrow();

        Notify notify = field.getAnnotation(Notify.class);

        boolean valueIsEmpty = isEmpty(value);
        boolean shouldBeEmpty = !isEmpty(notify.ifSet());

        if (valueIsEmpty && !shouldBeEmpty) {
            Validator.addIssueToMember(fieldName, notify.ifUnset(), notify.level());
        } else if (!valueIsEmpty && shouldBeEmpty) {
            Validator.addIssueToMember(fieldName, notify.ifSet(), notify.level());
        }
    }

    private void markDeprecateUnsetFields(@NotNull Object element) {
        ClusterModel.getFieldsAnnotatedWith(element, Deprecated.class)
                .entrySet()
                .stream()
                .filter(this::isSet)
                .forEach(entry -> raiseDeprecationIssue(element, entry.getKey()));
    }

    private void raiseDeprecationIssue(@NotNull Object element, String fieldName) {
        Field field = ClusterModel.getField(element, fieldName).orElseThrow();

        if (field.isAnnotationPresent(Notify.class)) return;

        String message = getDeprecationMessage(field);

        Validator.addIssueToMember(fieldName, message, Validate.Level.DEPRECATION);
    }

    private String getDeprecationMessage(Field field) {
        AnnoDoc annoDoc = field.getAnnotation(AnnoDoc.class);

        if (annoDoc == null)
            return String.format("Field '%s' is deprecated", field.getName());

        return Arrays.stream(annoDoc.value().split("\\R"))
                .filter(l -> l.startsWith("@deprecated "))
                .map(l -> l.replaceFirst("@deprecated ", "").trim())
                .findAny()
                .orElse(String.format("Field '%s' is deprecated", field.getName()));
    }
}
