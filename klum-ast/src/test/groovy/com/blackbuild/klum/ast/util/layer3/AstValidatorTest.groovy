//file:noinspection GrPackage
package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import org.codehaus.groovy.control.MultipleCompilationErrorsException

class AstValidatorTest extends AbstractDSLSpec {

    def "Fail if DSL annotation is missing"() {
        given:
        createNonDslClass '''
            package validation

            import com.blackbuild.klum.ast.validation.NeedsDslClass
            import org.codehaus.groovy.transform.GroovyASTTransformationClass
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @NeedsDslClass
            @GroovyASTTransformationClass("com.blackbuild.klum.ast.util.layer3.AstValidator")
            @interface AWithDslNeeded {}
'''

        when:
        createSecondaryClass '''
        package tmp
        
        import validation.AWithDslNeeded
        
        @AWithDslNeeded
        class OnClass {}
'''
        then:
        thrown(MultipleCompilationErrorsException)

       when:
        createSecondaryClass '''
        package tmp

        import com.blackbuild.groovy.configdsl.transform.DSL
        import validation.AWithDslNeeded
        
        @AWithDslNeeded
        @DSL class OnClass {}
'''
        then:
        noExceptionThrown()
    }

    def "check for allowed members"() {
        given:
        createNonDslClass '''
            package validation

            import com.blackbuild.klum.ast.validation.AllowedMembersForClass
            import com.blackbuild.klum.ast.validation.AllowedMembersForField
            import com.blackbuild.klum.ast.validation.AllowedMembersForMethod
            import com.blackbuild.klum.ast.validation.NeedsDslClass
            import org.codehaus.groovy.transform.GroovyASTTransformationClass
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @AllowedMembersForClass("classMember")
            @AllowedMembersForMethod("methodMember")
            @AllowedMembersForField("fieldMember")
            @GroovyASTTransformationClass("com.blackbuild.klum.ast.util.layer3.AstValidator")
            @interface MemberCheck {
                String classMember() default ""
                String methodMember() default ""
                String fieldMember() default ""
            }
'''

        when: "working"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        @MemberCheck(classMember = "a")
        class OnClass {
            @MemberCheck(fieldMember = "b") String field
            @MemberCheck(methodMember = "c") void method() {}
        }
'''
        then:
        noExceptionThrown()

        when: "wrong member on class"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        @MemberCheck(fieldMember = "b")
        class OnClass {
            String field
            void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)

        when: "wrong member on field"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        class OnClass {
            @MemberCheck(classMember = "a") String field
            void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)

        when: "wrong member on method"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        class OnClass {
            String field
            @MemberCheck(fieldMember = "b") void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "check for forbidden members"() {
        given:
        createNonDslClass '''
            package validation

            import com.blackbuild.klum.ast.validation.AllowedMembersForClass
            import com.blackbuild.klum.ast.validation.AllowedMembersForField
            import com.blackbuild.klum.ast.validation.AllowedMembersForMethod
            import com.blackbuild.klum.ast.validation.NeedsDslClass
            import org.codehaus.groovy.transform.GroovyASTTransformationClass
            import java.lang.annotation.*

            @Retention(RetentionPolicy.RUNTIME)
            @AllowedMembersForClass(value = ["fieldMember", "methodMember"], invert = true)
            @AllowedMembersForMethod(value = ["classMember", "fieldMember"], invert = true)
            @AllowedMembersForField(value = ["classMember", "methodMember"], invert = true)
            @GroovyASTTransformationClass("com.blackbuild.klum.ast.util.layer3.AstValidator")
            @interface MemberCheck {
                String classMember() default ""
                String methodMember() default ""
                String fieldMember() default ""
            }
'''

        when: "working"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        @MemberCheck(classMember = "a")
        class OnClass {
            @MemberCheck(fieldMember = "b") String field
            @MemberCheck(methodMember = "c") void method() {}
        }
'''
        then:
        noExceptionThrown()

        when: "wrong member on class"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        @MemberCheck(fieldMember = "b")
        class OnClass {
            String field
            void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)

        when: "wrong member on field"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        class OnClass {
            @MemberCheck(classMember = "a") String field
            void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)

        when: "wrong member on method"
        createSecondaryClass '''
        package tmp
        
        import validation.MemberCheck
        
        class OnClass {
            String field
            @MemberCheck(fieldMember = "b") void method() {}
        }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }


}
