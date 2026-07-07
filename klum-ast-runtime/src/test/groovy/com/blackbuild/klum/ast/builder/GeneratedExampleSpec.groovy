package com.blackbuild.klum.ast.builder

import spock.lang.Specification

class GeneratedExampleSpec extends Specification {

    def "builder returns typed RW instance"() {
        when:
        def rw = com.blackbuild.klum.ast.builder.GeneratedExample.builder().build()

        then:
        rw instanceof com.blackbuild.klum.ast.builder.GeneratedExample.RW
    }
}
