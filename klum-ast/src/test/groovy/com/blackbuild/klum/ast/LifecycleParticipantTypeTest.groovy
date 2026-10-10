/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('867')
class LifecycleParticipantTypeTest extends AbstractDSLSpec {
    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Model-Phases.md#type-mutation-lp-5')
    def 'type mutation follows parent fields and precedes own fields and callbacks in #phase (#existing)'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target([ElementType.TYPE, ElementType.FIELD])
            @LifecycleMutator(phase = $phase, handler = Configure)
            @interface Managed { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @interface TypeInfo { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface FieldInfo {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def type = KlumBuilderSupport.of(c.targetBuilder).modelType
                    if (!c.isType()) {
                        assert c.getAnnotation(FieldInfo).present
                        assert c.getAnnotation(TypeInfo).empty
                        assert KlumBuilderSupport.of(c.containingBuilder).modelType == Application
                        Child.Create.narrowBuilder(c.targetBuilder).value('parent-field')
                    } else if (type == Application) {
                        assert c.containingBuilder == null && c.fieldName == null
                        assert c.declaredType == Application && c.fieldType == null
                        assert c.getAnnotation(TypeInfo).get().value() == 'root'
                        Application.Create.narrowBuilder(c.targetBuilder).value(c.annotation.value())
                    } else {
                        assert c.fieldName == 'child'
                        assert c.declaredType == Child && c.fieldType == FieldType.DEFAULT
                        assert KlumBuilderSupport.of(c.containingBuilder).modelType == Application
                        assert c.getAnnotation(TypeInfo).get().value() == 'child-type'
                        assert c.getAnnotation(FieldInfo).empty
                        def target = Child.Create.narrowBuilder(c.targetBuilder)
                        assert target.value == 'parent-field'
                        target.value(target.value + ':' + c.annotation.value())
                    }
                }
            }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleMutator(phase = $phase, handler = Observe)
            @interface Observed {}
            class Observe implements LifecycleMutationHandler<Observed> {
                void mutate(LifecycleMutationContext<Observed> c) {
                    assert !c.isType()
                    def parent = Child.Create.narrowBuilder(c.containingBuilder)
                    assert parent.value == 'parent-field:child-type'
                    Leaf.Create.narrowBuilder(c.targetBuilder).value(parent.value + ':own-field')
                }
            }
            @DSL class Leaf { String value }
            @Managed('child-type') @TypeInfo('child-type')
            @DSL class Child {
                String value
                @Observed Leaf leaf
                @$phase void observe() { leaf.value += ':method' }
                @${phase == 'Default' ? 'Default(code = { null })' : phase}
                Closure callback = { leaf.value += ':closure' }
            }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @LifecycleCreator(phase = $phase, handler = SupplyChild)
            @interface Supplied {}
            class SupplyChild implements LifecycleCreationHandler<Supplied> {
                KlumBuilder<?> create(LifecycleFieldContext<Supplied> c) { Child.Create.AsBuilder().With { leaf {} } }
            }
            @DSL abstract class BaseApplication { @Supplied @Managed('parent') @FieldInfo Child child }
            @Managed('root-type') @TypeInfo('root')
            @DSL class Application extends BaseApplication { String value }
        """

        when:
        def childContents = { leaf {} }
        def result = Application.Create.With {
            if (existing) {
                child(childContents)
            }
        }
        def root = Application.Create.One()

        then:
        result.value == 'root-type'
        root.value == 'root-type'
        result.child.leaf.value == 'parent-field:child-type:own-field:method:closure'
        BaseApplication.getDeclaredField('child').getAnnotation(loader.loadClass('FieldInfo')) != null

        where:
        [phase, existing] << [['AutoCreate', 'AutoLink', 'Default', 'PostTree'], [true, false]].combinations()
    }

    def 'type mutations follow Java inheritance without imposing order between annotation types'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = AutoLink, handler = Configure)
            @interface Managed { String value() }
            @Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = AutoLink, handler = OtherHandler)
            @interface Other {}
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = AutoLink, handler = LocalHandler)
            @interface Local {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def b = Base.Create.narrowBuilder(c.targetBuilder)
                    b.value(c.annotation.value())
                    assert c.getAnnotation(Managed).get() == c.annotation
                }
            }
            class OtherHandler implements LifecycleMutationHandler<Other> {
                void mutate(LifecycleMutationContext<Other> c) { Base.Create.narrowBuilder(c.targetBuilder).other('other') }
            }
            class LocalHandler implements LifecycleMutationHandler<Local> {
                void mutate(LifecycleMutationContext<Local> c) { Base.Create.narrowBuilder(c.targetBuilder).local('local') }
            }
            @Managed('interface') interface Behavior {}
            @Managed('base') @Other @Local @DSL class Base { String value; String other; String local }
            @DSL class InheritedChild extends Base {}
            @Managed('override') @DSL class OverrideChild extends Base {}
            @DSL class InterfaceChild implements Behavior { String value }
        '''

        when:
        def base = Base.Create.One()
        def inherited = InheritedChild.Create.One()
        def overridden = OverrideChild.Create.One()
        def fromInterface = InterfaceChild.Create.One()

        then:
        base.local == 'local'
        inherited.value == 'base'
        inherited.other == 'other'
        inherited.local == null
        overridden.value == 'override'
        overridden.other == 'other'
        overridden.local == null
        fromInterface.value == null
    }

    def 'type participants run before AutoCreate clusters and retain fresh state across Template and map sessions'() {
        given:
        createSecondaryClass '''
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = AutoCreate, handler = Configure)
            @interface Managed {}
            class Configure implements LifecycleMutationHandler<Managed> {
                int calls
                void mutate(LifecycleMutationContext<Managed> c) {
                    assert ++calls == 1
                    def target = Application.Create.narrowBuilder(c.targetBuilder)
                    assert target.service == null
                    target.service { value 'type' }
                }
            }
            @DSL class Service { String value; @Owner Application owner }
            @DSL abstract class Group { @Cluster @AutoCreate({ [value: 'cluster'] }) Map<String, Service> services }
            @Managed @DSL class Application extends Group { Service service; Service fallback }
        '''
        def template = Application.Create.Template.With {}

        when:
        def first = Application.Create.With { copyFrom template }
        def second = Application.Create.FromMap([:])

        then:
        template.service == null
        first.service.value == 'type'
        second.service.value == 'type'
        first.fallback.value == 'cluster'
        first.service.owner.is(first)
        second.service.owner.is(second)
        !first.service.is(second.service)
    }

    def 'sealed aggregation targets retain their existing traversal skip in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = $phase, handler = Configure)
            @interface Managed {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    def b = Service.Create.narrowBuilder(c.targetBuilder)
                    b.calls(b.calls + 1)
                }
            }
            @Managed @DSL class Service { int calls }
            @DSL class Application { @Field(FieldType.LINK) Service service }
        """
        def completed = Service.Create.One()

        when:
        def result = Application.Create.With { service completed }

        then:
        result.service.is(completed)
        completed.calls == 1

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'type failures retain annotation handler phase Schema and cause in #phase'() {
        given:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            @LifecycleMutator(phase = $phase, handler = Configure)
            @interface Managed {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {
                    assert c.isType() && c.fieldName == null
                    assert Application.Create.narrowBuilder(c.targetBuilder).value != 'fail' : 'type probe'
                }
            }
            @Managed @DSL class Application { String value }
        """

        when:
        Application.Create.With { value 'fail' }

        then:
        RuntimeException failure = thrown()
        failure.message.contains('Participant Managed handler Configure during ' + phase + ' on Application')
        def causes = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            causes.add(cause)
        }
        causes.any { it instanceof AssertionError && it.message.contains('type probe') }

        when:
        def result = Application.Create.With { value 'pass' }

        then:
        result.value == 'pass'

        where:
        phase << ['AutoCreate', 'AutoLink', 'Default', 'PostTree']
    }

    def 'rejects creators and malformed mutators on Schema types (#declaration)'() {
        when:
        createSecondaryClass """
            import com.blackbuild.klum.ast.runtime.*
            import com.blackbuild.klum.ast.layer3.*
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
            $declaration
            @interface Managed {}
            class Configure implements LifecycleMutationHandler<Managed> {
                void mutate(LifecycleMutationContext<Managed> c) {}
            }
            class Supply implements LifecycleCreationHandler<Managed> {
                KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { null }
            }
            @Managed @DSL class Application {}
        """

        then:
        MultipleCompilationErrorsException failure = thrown()
        failure.message.contains(message)

        where:
        declaration                                               | message
        '@LifecycleCreator(phase = AutoLink, handler = Supply)'    | 'Creating lifecycle participants require direct Schema field placement'
        '@LifecycleMutator(phase = Validate, handler = Configure)' | 'support only AutoCreate, AutoLink, Default and PostTree'
        '@LifecycleMutator(phase = AutoLink, handler = Supply)'     | 'annotation parameter must resolve exactly'
    }
}
