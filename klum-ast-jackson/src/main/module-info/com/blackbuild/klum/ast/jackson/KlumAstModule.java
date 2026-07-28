package com.blackbuild.klum.ast.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;

public class KlumAstModule extends Module {
    @Override
    public String getModuleName() {
        return "KlumAST";
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }

    @Override
    public void setupModule(SetupContext context) {
    }
}
