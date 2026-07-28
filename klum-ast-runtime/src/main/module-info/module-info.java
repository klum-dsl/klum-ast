module com.blackbuild.klum.ast.runtime {
    requires transitive com.blackbuild.klum.ast.annotations;
    requires transitive org.apache.groovy;
    requires static com.blackbuild.annodocimal.annotations;
    exports com.blackbuild.klum.ast.runtime;
    exports com.blackbuild.klum.ast.runtime.validation;
    exports com.blackbuild.klum.ast.runtime.internal to
            com.blackbuild.klum.ast.compiler,
            com.blackbuild.klum.ast.jackson;
    exports com.blackbuild.klum.ast.runtime.internal.layer3 to
            com.blackbuild.klum.ast.compiler;
    exports com.blackbuild.klum.ast.runtime.internal.process to
            com.blackbuild.klum.ast.compiler,
            com.blackbuild.klum.ast.jackson;

    uses com.blackbuild.klum.ast.runtime.PhaseAction;
    uses com.blackbuild.klum.ast.runtime.validation.InstanceValidator;

    provides com.blackbuild.klum.ast.runtime.PhaseAction with
            com.blackbuild.klum.ast.runtime.internal.validation.EarlyValidationPhase,
            com.blackbuild.klum.ast.runtime.internal.OwnerPhase,
            com.blackbuild.klum.ast.runtime.internal.DefaultPhase,
            com.blackbuild.klum.ast.runtime.internal.layer3.AutoCreationPhase,
            com.blackbuild.klum.ast.runtime.internal.layer3.AutoLinkPhase,
            com.blackbuild.klum.ast.runtime.internal.PostTreePhase,
            com.blackbuild.klum.ast.runtime.internal.InstantiatePhase,
            com.blackbuild.klum.ast.runtime.internal.validation.ValidationPhase,
            com.blackbuild.klum.ast.runtime.internal.VerifyPhase,
            com.blackbuild.klum.ast.runtime.internal.CleanupPhase;
    provides com.blackbuild.klum.ast.runtime.validation.InstanceValidator with
            com.blackbuild.klum.ast.runtime.internal.validation.KlumFieldAnnotationsValidator,
            com.blackbuild.klum.ast.runtime.internal.validation.KlumMethodAnnotationsValidator,
            com.blackbuild.klum.ast.runtime.internal.validation.KlumInnerClassValidator;
}
