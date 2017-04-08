package com.blackbuild.groovy.configdsl.transform.mutators

import com.blackbuild.groovy.configdsl.transform.ast.mutators.MutationCheckerVisitor
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification


/**
 * Created by stephan on 07.04.2017.
 */
class MutationCheckerVisitorSpec extends Specification {

    ClassNode clazz
    ErrorCollector errorCollector = Mock(ErrorCollector)
    SourceUnit sourceUnit = Stub(SourceUnit) {
        getErrorCollector() >> errorCollector
    }
    MutationCheckerVisitor visitor = new MutationCheckerVisitor(sourceUnit)

    def withClassCode(String text) {
        def textWithImports = 'import com.blackbuild.groovy.configdsl.transform.*\n' + text

        clazz = new AstBuilder().buildFromString(CompilePhase.CANONICALIZATION, textWithImports)[1] as ClassNode
    }

    def doVisit() {
        visitor.visitClass(clazz)
    }
    
    def "debuggable visit call"() {
        given:
        withClassCode '''
            class Bla {
                String name
            
              def doIt() {
                def temp
                temp = "bla"
                name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        noExceptionThrown()

    }

    def "qualified call in non mutated method is an error"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              def doIt() {
                this.name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        1 * errorCollector.addErrorAndContinue(_)
    }

    def "unqualified call in non mutated method is an error"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              def doIt() {
                name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        1 * errorCollector.addErrorAndContinue(_)
    }

    def "unqualified assignment to a local variable is allowed"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              def doIt() {
                def name
                name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        0 * errorCollector.addErrorAndContinue(_)
    }

    def "multiassignment is also not allowed"() {
        given:
        withClassCode '''
            class Bla {
              String name
              String value
            
              def doIt() {
                (name, value) = ["blub", "bli"]
              }
            }
'''

        when:
        doVisit()

        then:
        1 * errorCollector.addErrorAndContinue(_)
    }

    def "multiassignment local and field is not allowed"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              def doIt() {
                def value = "b"
                (value, name) = ["blub", "bli"]
              }
            }
'''

        when:
        doVisit()

        then:
        1 * errorCollector.addErrorAndContinue(_)
    }

    def "multiassignment to local fields is allowed"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              def doIt() {
                def value = "b"
                def name
                (value, name) = ["blub", "bli"]
              }
            }
'''

        when:
        doVisit()

        then:
        0 * errorCollector.addErrorAndContinue(_)
    }

    def "qualified call in mutated method is no error"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              @Mutator
              def doIt() {
                this.name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        0 * errorCollector.addErrorAndContinue(_)
    }

}