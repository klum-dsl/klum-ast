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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.process.ConstructionSession
import com.blackbuild.klum.ast.util.DslHelper
import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.KlumModelException
import com.blackbuild.klum.ast.util.KlumObjectSupport

import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AsBuilderSpec extends AbstractDSLSpec {

    def "AsBuilder child joins the owning root graph"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child child
            }

            @DSL
            class Child {
                String name
            }
        '''
        def Child = getClass('pk.Child')
        KlumBuilder childBuilder

        when:
        instance = clazz.Create.With {
            childBuilder = Child.Create.AsBuilder.With(name: 'owned')
            ((KlumBuilder) delegate).setSingleField('child', childBuilder)
        }

        then:
        instance.child.name == 'owned'
        childBuilder.completedModel.is(instance.child)
    }

    def "AsBuilder prepares an owned child exactly once in the active Template scope"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child child
            }

            @DSL
            class Child {
                static int postTreeCalls
                static int validationCalls

                @Owner Root owner
                String fromTemplate
                String configured
                int postCreateCalls
                int postApplyCalls

                @PostCreate
                void initialize() {
                    postCreateCalls++
                }

                @PostApply
                void finishConfiguration() {
                    postApplyCalls++
                }

                @PostTree
                void finishGraph() {
                    postTreeCalls++
                }

                @Validate
                void validateCompletedModel() {
                    validationCalls++
                }
            }
        '''
        def Child = getClass('pk.Child')
        def template = Child.Template.Create(fromTemplate: 'active')
        def configurationCalls = new AtomicInteger()

        when:
        Child.Template.With(template) {
            instance = clazz.Create.With {
                KlumBuilder child = Child.Create.AsBuilder.With {
                    configurationCalls.incrementAndGet()
                    configured 'explicit'
                }
                ((KlumBuilder) delegate).setSingleField('child', child)
            }
        }

        then:
        instance.child.fromTemplate == 'active'
        instance.child.configured == 'explicit'
        instance.child.postCreateCalls == 1
        instance.child.postApplyCalls == 1
        configurationCalls.get() == 1
        Child.postTreeCalls == 1
        Child.validationCalls == 1
        instance.child.owner.is(instance)
        KlumObjectSupport.of(instance.child).modelPath == '<root>.child'
        DslHelper.getBreadcrumbPath(instance.child) == '$/p.Root.With/p.Child.AsBuilder.With'
    }

    def "AsBuilder rejects calls outside a Construction session"() {
        given:
        createClass '''
            package pk

            @DSL
            class Child {
                String name
            }
        '''

        when:
        clazz.Create.AsBuilder.One()

        then:
        KlumModelException error = thrown()
        error.message.contains('requires an active Construction session')
        error.message.contains('inside the owning root Builder lifecycle')
        error.message.contains('Create.With, Create.One, or Create.From')
    }

    def "AsBuilder FromMap creates an owned child without a nested lifecycle"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child child
            }

            @DSL
            class Child {
                String name
            }
        '''
        def Child = getClass('pk.Child')

        when:
        instance = clazz.Create.With {
            KlumBuilder child = Child.Create.AsBuilder.FromMap(name: 'mapped')
            ((KlumBuilder) delegate).setSingleField('child', child)
        }

        then:
        instance.child.name == 'mapped'
    }

    def "AsBuilder With and One preserve keyed child configuration"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child configured
                Child empty
            }

            @DSL
            class Child {
                @Key String id
                String name
            }
        '''
        def Child = getClass('pk.Child')

        when:
        instance = clazz.Create.With {
            KlumBuilder configuredChild = Child.Create.AsBuilder.With([name: 'configured'], 'configured-id')
            KlumBuilder emptyChild = Child.Create.AsBuilder.One('empty-id')
            ((KlumBuilder) delegate).setSingleField('configured', configuredChild)
            ((KlumBuilder) delegate).setSingleField('empty', emptyChild)
        }

        then:
        instance.configured.id == 'configured-id'
        instance.configured.name == 'configured'
        instance.empty.id == 'empty-id'
    }

    def "AsBuilder From applies a DelegatingScript to an owned child"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child child
            }

            @DSL
            class Child {
                String name
            }
        '''
        def Child = getClass('pk.Child')
        def configurationScript = createSecondaryClass '''
            package pk

            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            name 'scripted'
        '''

        when:
        instance = clazz.Create.With {
            KlumBuilder child = Child.Create.AsBuilder.From(configurationScript)
            ((KlumBuilder) delegate).setSingleField('child', child)
        }

        then:
        instance.child.name == 'scripted'
    }

    def "AsBuilder result is invalid after its root lifecycle completes"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
            }

            @DSL
            class Child {
                String name
            }
        '''
        def Child = getClass('pk.Child')
        KlumBuilder child

        and:
        clazz.Create.With {
            child = Child.Create.AsBuilder.One()
        }

        when:
        child.apply(name: 'too late')

        then:
        KlumModelException error = thrown()
        error.message.contains('after its Construction session has completed')
        error.message.contains('Create.AsBuilder inside the owning root Builder lifecycle')
    }

    def "owned Builders cannot cross Construction sessions"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
                Child child
            }

            @DSL
            class Child {
                String name
            }
        '''
        def Child = getClass('pk.Child')
        def childFromOtherSession = new AtomicReference<KlumBuilder>()
        def firstSessionReady = new CountDownLatch(1)
        def releaseFirstSession = new CountDownLatch(1)
        def firstSessionFailure = new AtomicReference<Throwable>()
        Thread firstSession = Thread.start {
            try {
                clazz.Create.With {
                    childFromOtherSession.set(Child.Create.AsBuilder.One())
                    firstSessionReady.countDown()
                    releaseFirstSession.await(10, TimeUnit.SECONDS)
                }
            } catch (Throwable failure) {
                firstSessionFailure.set(failure)
                firstSessionReady.countDown()
            }
        }
        assert firstSessionReady.await(10, TimeUnit.SECONDS)

        when:
        clazz.Create.With {
            ((KlumBuilder) delegate).setSingleField('child', childFromOtherSession.get())
        }

        then:
        KlumModelException error = thrown()
        error.message.contains('different Construction session')
        error.message.contains('Builders cannot cross root lifecycles')

        cleanup:
        releaseFirstSession.countDown()
        firstSession?.join(10_000)
        assert !firstSession?.alive
        assert firstSessionFailure.get() == null
    }

    def "AsBuilder rejects ordinary materializing Scripts without running them"() {
        given:
        createClass '''
            package pk

            @DSL
            class Root {
            }

            @DSL
            class Child {
                String name
            }

            class MaterializingChildScript extends Script {
                static boolean ran

                @Override
                Object run() {
                    ran = true
                    return Child.Create.With(name: 'materialized')
                }
            }
        '''
        def Child = getClass('pk.Child')
        def MaterializingChildScript = getClass('pk.MaterializingChildScript')

        when:
        clazz.Create.With {
            Child.Create.AsBuilder.From(MaterializingChildScript)
        }

        then:
        KlumModelException error = thrown()
        error.message.contains('only accepts DelegatingScript configuration recipes')
        error.message.contains('cannot join owned composition')
        error.message.contains('run the Script as a root with Create.From')
        !MaterializingChildScript.ran
    }

    def "root factories retain completed-model return behavior"() {
        given:
        createClass '''
            package pk

            @DSL
            class Child {
                String name
            }
        '''
        def configurationScript = createSecondaryClass '''
            package pk

            @groovy.transform.BaseScript(DelegatingScript)
            import groovy.util.DelegatingScript

            name 'from script'
        '''

        when:
        def withResult = clazz.Create.With(name: 'with')
        def oneResult = clazz.Create.One()
        def fromResult = clazz.Create.From(configurationScript)
        def fromMapResult = clazz.Create.FromMap(name: 'from map')

        then:
        [withResult, oneResult, fromResult, fromMapResult].every {
            clazz.isInstance(it) && !(it instanceof KlumBuilder)
        }
        withResult.name == 'with'
        fromResult.name == 'from script'
        fromMapResult.name == 'from map'
    }

    def "ConstructionSession is only an opaque identity token"() {
        expect:
        ConstructionSession.declaredFields.findAll { !it.synthetic }.empty
        ConstructionSession.declaredMethods.findAll { !it.synthetic }.empty
        ConstructionSession.declaredConstructors.size() == 1
        !Modifier.isPublic(ConstructionSession.declaredConstructors.first().modifiers)
        !KlumBuilder.methods*.name.contains('getConstructionSession')
    }
}
