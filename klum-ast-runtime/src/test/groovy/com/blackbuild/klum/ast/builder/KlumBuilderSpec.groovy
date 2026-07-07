package com.blackbuild.klum.ast.builder

import spock.lang.Specification
import com.blackbuild.klum.ast.KlumRwObject

class KlumBuilderSpec extends Specification {

    def "adapter returns underlying RW instance"() {
        given:
        KlumRwObject rw = new KlumRwObject() {}
        def adapter = new KlumBuilderAdapter(rw)

        expect:
        adapter.build() == rw
    }
}
