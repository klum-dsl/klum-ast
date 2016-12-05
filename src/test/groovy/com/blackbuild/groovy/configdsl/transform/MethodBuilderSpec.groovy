package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.objectweb.asm.Opcodes
import spock.lang.Specification


class MethodBuilderSpec extends Specification {

    def emptyClass = new ClassNode("AClass", Opcodes.ACC_PUBLIC, ClassHelper.OBJECT_TYPE)

    def "Method without body throws exception"() {
        when:
        MethodBuilder.createPublicMethod("aMethod").addTo(emptyClass)

        then:
        thrown(IllegalStateException)
    }


}