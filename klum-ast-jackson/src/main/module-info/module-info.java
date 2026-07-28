module com.blackbuild.klum.ast.jackson {
    requires transitive com.blackbuild.klum.ast.runtime;
    requires transitive com.fasterxml.jackson.databind;
    exports com.blackbuild.klum.ast.jackson;

    provides com.fasterxml.jackson.databind.Module with
            com.blackbuild.klum.ast.jackson.KlumAstModule;
}
