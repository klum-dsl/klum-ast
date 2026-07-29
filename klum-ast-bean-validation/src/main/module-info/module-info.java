module com.blackbuild.klum.ast.validation.bean {
    requires transitive com.blackbuild.klum.ast.runtime;
    requires transitive jakarta.validation;
    requires com.fasterxml.classmate;
    requires org.hibernate.validator;
    exports com.blackbuild.klum.ast.validation.bean;

    provides com.blackbuild.klum.ast.runtime.validation.InstanceValidator with
            com.blackbuild.klum.ast.validation.bean.internal.JSR380Validator;
}
