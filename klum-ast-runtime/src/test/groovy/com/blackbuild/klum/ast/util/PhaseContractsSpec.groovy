package com.blackbuild.klum.ast.util

import spock.lang.Specification
import com.blackbuild.klum.ast.process.DefaultKlumPhase
import com.blackbuild.klum.ast.process.PhaseDriver
import com.blackbuild.klum.ast.util.KlumModelException

class PhaseContractsSpec extends Specification {

    def setup() {
        System.setProperty("klum.strictPhaseContracts", "true")
    }

    def cleanup() {
        System.clearProperty("klum.strictPhaseContracts")
        PhaseDriver.getContext().setPhase(DefaultKlumPhase.CREATE)
    }

    def "throws when mutating after postTree"() {
        given:
        PhaseDriver.getContext().setPhase(DefaultKlumPhase.POST_TREE)

        when:
        PhaseContracts.checkMutable("someField")

        then:
        thrown(KlumModelException)
    }

    def "does not throw when before postTree"() {
        given:
        PhaseDriver.getContext().setPhase(DefaultKlumPhase.DEFAULT)

        when:
        PhaseContracts.checkMutable("someField")

        then:
        notThrown(KlumModelException)
    }
}
