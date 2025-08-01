package com.blackbuild.groovy.configdsl.transform

import spock.lang.Specification


class ValidateLevelTest extends Specification {

    def "test validate level"() {
        expect:
        left.combine(right) == expected

        where:
        left                | right               || expected
        Validate.Level.NONE  | Validate.Level.NONE  || Validate.Level.NONE
        Validate.Level.NONE  | Validate.Level.ERROR || Validate.Level.ERROR
        Validate.Level.NONE  | Validate.Level.WARNING  || Validate.Level.WARNING
        Validate.Level.NONE  | Validate.Level.INFO  || Validate.Level.INFO
        Validate.Level.ERROR | Validate.Level.NONE  || Validate.Level.ERROR
    }

    def "from String matches numbers, label (upper and lower)"() {
        expect:
        Validate.Level.fromString(input) == expected

        where:
        input          || expected
        "0"            || Validate.Level.NONE
        "1"            || Validate.Level.INFO
        "2"            || Validate.Level.WARNING
        "3"            || Validate.Level.DEPRECATION
        "4"            || Validate.Level.ERROR
        "none"         || Validate.Level.NONE
        "info"         || Validate.Level.INFO
        "warning"      || Validate.Level.WARNING
        "deprecation"  || Validate.Level.DEPRECATION
        "error"        || Validate.Level.ERROR
        "ERROR"        || Validate.Level.ERROR
        "WARNING"      || Validate.Level.WARNING
        "INFO"         || Validate.Level.INFO
        "NONE"         || Validate.Level.NONE
        "DEPRECATION"  || Validate.Level.DEPRECATION
    }

}