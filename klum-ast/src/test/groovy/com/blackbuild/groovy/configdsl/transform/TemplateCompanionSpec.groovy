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
import com.blackbuild.klum.ast.util.KlumModelProxy
import com.blackbuild.klum.ast.util.KlumObjectCompanion
import com.blackbuild.klum.ast.util.KlumTemplateProxy
import com.blackbuild.klum.ast.util.TemplateManager

import java.lang.reflect.Modifier

class TemplateCompanionSpec extends AbstractDSLSpec {

    def "generated models use the sealed common companion path contract"() {
        given:
        createClass '''
            package pk

            @DSL
            class CompanionModel {
                String value
            }
        '''

        when:
        def model = clazz.Create.With { value "model" }
        def template = clazz.Template.Create { value "template" }
        def companionField = clazz.getDeclaredField('$proxy')
        companionField.accessible = true
        KlumObjectCompanion modelCompanion = companionField.get(model)
        KlumObjectCompanion templateCompanion = companionField.get(template)

        then:
        companionField.type == KlumObjectCompanion
        Modifier.isPrivate(companionField.modifiers)
        Modifier.isFinal(companionField.modifiers)
        companionField.synthetic

        and:
        KlumObjectCompanion.sealed
        KlumObjectCompanion.permittedSubclasses as Set == [KlumModelProxy, KlumTemplateProxy] as Set
        KlumObjectCompanion.declaredMethods*.name as Set == [
                'getObject',
                'getBreadcrumbPath',
                'getModelPath'
        ] as Set
        Modifier.isFinal(KlumModelProxy.modifiers)
        Modifier.isFinal(KlumTemplateProxy.modifiers)

        and:
        modelCompanion.class == KlumModelProxy
        modelCompanion.object.is(model)
        modelCompanion.breadcrumbPath == DslHelper.getBreadcrumbPath(model)
        modelCompanion.modelPath == DslHelper.getModelPath(model)

        and:
        templateCompanion.class == KlumTemplateProxy
        templateCompanion.object.is(template)
        templateCompanion.breadcrumbPath == DslHelper.getBreadcrumbPath(template)
        templateCompanion.modelPath == DslHelper.getModelPath(template)
    }

    def "Template identity means only a persistent Template companion"() {
        given:
        createClass '''
            package pk

            @DSL
            class IdentityModel {
                String value
            }
        '''

        when:
        def templateBuilder
        def template = clazz.Template.Create {
            templateBuilder = delegate
            value "template"
        }
        def model = clazz.Template.With(template) {
            clazz.Create.With { value "model" }
        }

        then:
        TemplateManager.isTemplate(template)
        !TemplateManager.isTemplate(model)
        !TemplateManager.isTemplate(templateBuilder)
        !TemplateManager.isTemplate(null)
        !TemplateManager.isTemplate([value: "data"])
    }

    def "Template materialization marks the complete owned graph but preserves an ordinary LINK target"() {
        given:
        createClass '''
            package pk

            @DSL
            class TemplateRoot {
                TemplateNode primary

                @Field(members = "node")
                List<TemplateNode> children

                @Field(members = "mappedNode")
                Map<String, TemplateNode> mappedNodes

                @Field(FieldType.LINK)
                TemplateNode external
            }

            @DSL
            class TemplateNode {
                @Key String name
                TemplateLeaf leaf
            }

            @DSL
            class TemplateLeaf {
                String value
            }
        '''
        def Node = getClass("pk.TemplateNode")
        def existingLink = Node.Create.With("external") {}

        when:
        def template = clazz.Template.Create {
            primary("primary") {
                leaf { value "direct" }
            }
            children {
                node("listed") {
                    leaf { value "list" }
                }
            }
            mappedNode("mapped") {
                leaf { value "map" }
            }
            external existingLink
        }
        def rehydrated = clazz.Create.With {
            copyFrom template
        }

        then:
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.primary)
        TemplateManager.isTemplate(template.primary.leaf)
        TemplateManager.isTemplate(template.children.first())
        TemplateManager.isTemplate(template.children.first().leaf)
        TemplateManager.isTemplate(template.mappedNodes.mapped)
        TemplateManager.isTemplate(template.mappedNodes.mapped.leaf)

        and:
        DslHelper.getModelPath(template) == "<root>"
        DslHelper.getModelPath(template.primary) == "<root>.primary"
        DslHelper.getModelPath(template.children.first()) == "<root>.children[0]"
        DslHelper.getModelPath(template.mappedNodes.mapped) == "<root>.mappedNodes.mapped"

        and:
        template.external.is(existingLink)
        rehydrated.external.is(existingLink)
        !TemplateManager.isTemplate(existingLink)
        KlumModelProxy.getProxyFor(existingLink).object.is(existingLink)
    }

    def "completed Templates are rejected from the #field relationship"() {
        given:
        createClass '''
            package pk

            @DSL
            class RelationshipRoot {
                RelationshipNode direct

                @Field(members = "node")
                List<RelationshipNode> nodes

                Map<String, RelationshipNode> mappedNodes

                @Field(FieldType.LINK)
                RelationshipNode linked
            }

            @DSL
            class RelationshipNode {
                @Key String name
            }
        '''
        def Node = getClass("pk.RelationshipNode")
        def template = Node.Template.Create {}

        when:
        clazz.Create.With {
            KlumBuilder builder = delegate
            switch (field) {
                case "direct":
                    builder.setInstanceAttribute(field, template)
                    break
                case "nodes":
                    builder.addElementToCollection(field, template)
                    break
                case "mappedNodes":
                    builder.addElementToMap(field, "template", template)
                    break
                case "linked":
                    builder.setInstanceAttribute(field, template)
                    break
            }
        }

        then:
        KlumModelException failure = thrown()
        failure.message.startsWith("Cannot use a Template as relationship value pk.RelationshipRoot.$field. " +
                "Rehydrate it with Template.With, copyFrom, or another Template/copy API so a fresh Builder graph is created.")

        where:
        field << ["direct", "nodes", "mappedNodes", "linked"]
    }

    def "owned Template cycles rehydrate with ordinary identity ownership lifecycle and validation"() {
        given:
        createClass '''
            package pk

            @DSL
            class CycleRoot {
                CycleNode node
            }

            @DSL
            class CycleNode {
                static int postCreateCalls
                static int postApplyCalls
                static int validationCalls

                String name
                @Owner CycleRoot owner

                @Field(FieldType.LINK)
                CycleNode peer

                @PostCreate
                void afterCreate() {
                    postCreateCalls++
                }

                @PostApply
                void afterApply() {
                    postApplyCalls++
                }

                @Validate
                void validateCompletedNode() {
                    assert owner != null
                    assert peer.is(this)
                    validationCalls++
                }
            }
        '''
        def Node = getClass("pk.CycleNode")

        when:
        def template = clazz.Template.Create {
            node {
                KlumBuilder nodeBuilder = delegate
                name "cyclic"
                nodeBuilder.setInstanceAttribute("peer", nodeBuilder)
            }
        }

        then:
        TemplateManager.isTemplate(template)
        TemplateManager.isTemplate(template.node)
        template.node.peer.is(template.node)
        template.node.owner == null
        Node.postCreateCalls == 0
        Node.postApplyCalls == 0
        Node.validationCalls == 0
        DslHelper.getModelPath(template.node) == "<root>.node"

        when:
        instance = clazz.Create.With {
            copyFrom template
        }

        then:
        !TemplateManager.isTemplate(instance)
        !TemplateManager.isTemplate(instance.node)
        instance.node.peer.is(instance.node)
        instance.node.owner.is(instance)
        Node.postCreateCalls == 1
        Node.postApplyCalls == 1
        Node.validationCalls == 1
        DslHelper.getModelPath(instance.node) == "<root>.node"
        !instance.node.is(template.node)
    }

    def "Java serialization preserves Template graph identity and recipe replay without construction state"() {
        given:
        createClass '''
            package pk

            @DSL
            class SerializableTemplate {
                String name
                String result
                SerializableTemplateChild child
            }

            @DSL
            class SerializableTemplateChild {
                String value
                String result
            }
        '''
        def suffix = "!"
        def template = clazz.Template.Create {
            name "root"
            applyLater {
                result = name.toUpperCase() + suffix
            }
            child {
                value "child"
                applyLater {
                    result = value.toUpperCase()
                }
            }
        }

        when:
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(template) }
        def dynamicLoader = loader
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass descriptor) {
                try {
                    return dynamicLoader.loadClass(descriptor.name)
                } catch (ClassNotFoundException ignored) {
                    return super.resolveClass(descriptor)
                }
            }
        }.withCloseable { input ->
            input.objectInputFilter = { info ->
                Class<?> serializedType = info.serialClass()
                if (serializedType != null && (KlumBuilder.isAssignableFrom(serializedType)
                        || ConstructionSession.isAssignableFrom(serializedType)
                        || serializedType == TreeMap
                        || serializedType == ArrayList)) {
                    return ObjectInputFilter.Status.REJECTED
                }
                return ObjectInputFilter.Status.UNDECIDED
            } as ObjectInputFilter
            input.readObject()
        }

        then:
        TemplateManager.isTemplate(restored)
        TemplateManager.isTemplate(restored.child)
        restored.name == "root"
        restored.child.value == "child"

        when:
        def result = clazz.Template.With(restored) {
            clazz.Create.One()
        }

        then:
        result.name == "root"
        result.result == "ROOT!"
        result.child.value == "child"
        result.child.result == "CHILD"
        !result.child.is(restored.child)
        !TemplateManager.isTemplate(result)
        !TemplateManager.isTemplate(result.child)
    }
}
