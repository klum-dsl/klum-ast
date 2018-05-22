package com.blackbuild.groovy.configdsl.transform


import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Ignore

class MockTest extends AbstractDSLSpec {

    @Ignore("only for manual debugging")
    def "test compilation against real models in mock folder"() {
        given:
        CompilationUnit unit = new CompilationUnit(loader)
        unit.addSources(new File("src/mock").listFiles())

        when:
        unit.compile()

        then:
        noExceptionThrown()
    }


}