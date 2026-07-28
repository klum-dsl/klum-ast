module com.blackbuild.klum.ast.compiler {
    requires java.desktop;
    requires com.blackbuild.annodocimal.global.ast;
    requires com.blackbuild.klum.ast.annotations;
    requires com.blackbuild.klum.ast.runtime;
    requires com.blackbuild.klum.cast.annotations;
    requires com.blackbuild.klum.cast.compiler;
    requires com.blackbuild.klum.cast.spi;
    requires org.apache.groovy;
    opens com.blackbuild.klum.ast.compiler.internal.ast to
            org.apache.groovy,
            com.blackbuild.klum.cast.compiler;
    opens com.blackbuild.klum.ast.compiler.internal.ast.converters to org.apache.groovy;
    opens com.blackbuild.klum.ast.compiler.internal.ast.mutators to
            org.apache.groovy,
            com.blackbuild.klum.cast.compiler;
    opens com.blackbuild.klum.ast.compiler.internal.layer3 to
            org.apache.groovy,
            com.blackbuild.klum.cast.compiler;
    opens com.blackbuild.klum.ast.compiler.internal.validation to com.blackbuild.klum.cast.compiler;
}
