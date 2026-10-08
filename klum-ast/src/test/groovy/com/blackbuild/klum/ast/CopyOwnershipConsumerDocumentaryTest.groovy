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

import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('856')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Completed-Object-Support.md#copied-providers-and-absent-authority')
class CopyOwnershipConsumerDocumentaryTest extends AbstractDSLSpec {
    def 'inherited AUTO_LINK skips providers without authoritative declarations and preserves selected identity'() {
        given:
        createNonDslClass '''
            package consumer
            import java.lang.annotation.*
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
            import com.blackbuild.klum.ast.runtime.KlumObjectSupport

            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Source { String value() }
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Binding { String value() }
            @DSL class Facts { String topic }
            @DSL class ProviderBase {
                @Source('primary') List<Facts> primary
                @Source('secondary') @Field(keyMapping = { it.topic }) Map<String, Facts> secondary
            }
            @DSL class Provider extends ProviderBase {}
            @DSL class ReceiverBase {
                @Field(FieldType.LINK) Facts facts
                @AutoLink void bind() {
                    if (facts != null) return
                    def binding = KlumBuilderSupport.of(this).structure
                        .getOwningRelationshipAnnotation(Binding).map { it.value() }.orElse('secondary')
                    def selected = Policy.choose(binding)
                    if (selected != null) facts selected
                }
            }
            @DSL class Receiver extends ReceiverBase {}
            @DSL class Application { @Binding('secondary') Receiver receiver }
            // Consumer policy handles absent metadata explicitly; no KlumAST selection rule is implied.
            class Policy {
                static List<Facts> candidates
                static List<Facts> withoutAuthority = []
                static Facts choose(String binding) {
                    def eligible = candidates.findAll { candidate ->
                        def source = KlumObjectSupport.of(candidate).structure.getOwningRelationshipAnnotation(Source)
                        if (source.empty) {
                            withoutAuthority << candidate
                            return false
                        }
                        source.get().value() == binding
                    }
                    eligible ? eligible.first() : null
                }
            }
        '''
        def providerType = getClass('Provider')
        def factsType = getClass('Facts')
        def policy = getClass('Policy')
        def provider = providerType.Create.With {
            primary = [factsType.Create.AsBuilder().With { topic 'primary' }]
            secondary = [chosen: factsType.Create.AsBuilder().With { topic 'secondary' }]
        }
        def copied = providerType.Create.With { copyFrom provider }
        def unqualified = [copied.primary[0], copied.secondary.chosen]
        policy.candidates = unqualified + (authoritativeCandidates ? [provider.primary[0], provider.secondary.chosen] : [])

        when:
        def result = getClass('Application').Create.With { receiver {} }

        then:
        authoritativeCandidates ? result.receiver.facts.is(provider.secondary.chosen) : result.receiver.facts == null
        policy.withoutAuthority.size() == 2
        policy.withoutAuthority[0].is(unqualified[0])
        policy.withoutAuthority[1].is(unqualified[1])
        unqualified.every { KlumObjectSupport.of(it).structure.owningRelationship.empty }
        !copied.secondary.chosen.is(provider.secondary.chosen)
        KlumObjectSupport.of(provider.primary[0]).structure.getOwningRelationshipAnnotation(getClass('Source')).orElseThrow().value() == 'primary'
        KlumObjectSupport.of(provider.secondary.chosen).structure.getOwningRelationshipAnnotation(getClass('Source')).orElseThrow().value() == 'secondary'
        KlumObjectSupport.of(provider.secondary.chosen).structure.owningRelationship.orElseThrow().declaringClass.is(getClass('ProviderBase'))
        KlumObjectSupport.of(provider.secondary.chosen).structure.owningRelationship.orElseThrow().name == 'secondary'

        where:
        authoritativeCandidates << [true, false]
    }
}
