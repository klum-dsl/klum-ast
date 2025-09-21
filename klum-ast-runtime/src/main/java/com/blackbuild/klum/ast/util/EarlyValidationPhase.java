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
        Field field = ClusterModel.getField(element, fieldName).get();

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
        Field field = ClusterModel.getField(element, fieldName).get();

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
