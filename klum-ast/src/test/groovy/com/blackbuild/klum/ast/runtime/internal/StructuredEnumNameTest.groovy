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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import static java.nio.charset.StandardCharsets.ISO_8859_1

@Issue('1064')
class StructuredEnumNameTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#enum-value-names')
    def 'renames an enum value for parsed YAML and keeps ordinary Groovy enum calls'() {
        given:
        createClass('''
            package enuminput
            import com.blackbuild.klum.ast.*
            @DSL class Delivery {
                ShippingTier tier
            }
            enum ShippingTier {
                @Struct.Name('next-day') NEXT_DAY,
                ECONOMY

                static ShippingTier fromString(String text) {
                    if (text == 'overnight') return NEXT_DAY
                    throw new IllegalArgumentException(text)
                }
            }
        ''')
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class delivery = artifacts.loadClass('enuminput.Delivery')
        Class shippingTier = artifacts.loadClass('enuminput.ShippingTier')
        Map parsed = (Map) parseYaml('tier: next-day')

        when:
        def renamed = delivery.Create.FromStruct(parsed, 'delivery.yaml')
        def applied = delivery.Create.With { applyStruct(parsed) }
        def nativeName = delivery.Create.FromStruct([tier: 'ECONOMY'])
        def typedDsl = delivery.Create.With { tier(shippingTier.NEXT_DAY) }
        def converterDsl = delivery.Create.With { tier('overnight') }

        then:
        renamed.tier == shippingTier.NEXT_DAY
        applied.tier == shippingTier.NEXT_DAY
        nativeName.tier == shippingTier.ECONOMY
        typedDsl.tier == shippingTier.NEXT_DAY
        converterDsl.tier == shippingTier.NEXT_DAY

        when:
        delivery.Create.FromStruct([tier: spelling], 'delivery.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains("delivery.yaml at '/tier'")
        failure.message.contains('exact structured enum token of enuminput.ShippingTier')

        where:
        spelling << ['NEXT_DAY', 'overnight']
    }

    def 'rejects an enum resource from outside the loaded declaration artifact'() {
        given:
        createClass('''
            package shadowenuminput
            import com.blackbuild.klum.ast.*
            @DSL class Delivery {
                ShippingTier tier
            }
            enum ShippingTier {
                @Struct.Name('next-day') NEXT_DAY
            }
        ''')
        File classes = compilerConfiguration.targetDirectory
        String enumResource = 'shadowenuminput/ShippingTier.class'
        File shadow = new File(tempFolder.root, enumResource)
        shadow.parentFile.mkdirs()
        shadow.bytes = new String(new File(classes, enumResource).bytes, ISO_8859_1)
                .replace('next-day', 'fake-day').getBytes(ISO_8859_1)
        URL shadowResource = shadow.toURI().toURL()
        artifacts = new URLClassLoader([classes.toURI().toURL()] as URL[], getClass().classLoader) {
            @Override URL getResource(String name) {
                name == enumResource ? shadowResource : super.getResource(name)
            }

            @Override protected URL findResource(String moduleName, String name) {
                name == enumResource ? shadowResource : super.findResource(moduleName, name)
            }
        }
        Class delivery = artifacts.loadClass('shadowenuminput.Delivery')

        when:
        delivery.Create.FromStruct([tier: 'fake-day'])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('valid packaged enum declarations for shadowenuminput.ShippingTier')
        failure.cause.message.contains('resource origin disagrees with loaded type')
    }

    def 'invalid enum value names fail Schema compilation for #constantDeclarations'() {
        when:
        createClass("""
            package invalidenuminput
            import com.blackbuild.klum.ast.*
            @DSL class Delivery {
                List<ShippingTier> tiers
            }
            enum ShippingTier {
                $constantDeclarations
            }
        """)

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains(expectedMessage)

        where:
        constantDeclarations                                       | expectedMessage
        "@Struct.Name('') NEXT_DAY, ECONOMY"                         | '@Struct.Name requires a nonempty exact String on invalidenuminput.ShippingTier#NEXT_DAY'
        "@Struct.Name('ECONOMY') NEXT_DAY, ECONOMY"                   | "Duplicate structured enum token 'ECONOMY' in invalidenuminput.ShippingTier for constants NEXT_DAY and ECONOMY"
        "@Struct.Name('fast') NEXT_DAY, @Struct.Name('fast') ECONOMY" | "Duplicate structured enum token 'fast' in invalidenuminput.ShippingTier for constants NEXT_DAY and ECONOMY"
    }

    def cleanup() {
        artifacts?.close()
    }
}
