package com.blackbuild.klum.ast.util;

public class LanguageHelper {

    private LanguageHelper() {
    }

    public static String getSingularForPlural(String plural) {
        if (plural.endsWith("ies")) {
            return plural.substring(0, plural.length() - 3) + "y";
        } else if (plural.endsWith("s")) {
            return plural.substring(0, plural.length() - 1);
        } else {
            return plural;
        }
    }

}
