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

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.OwnerProvidedDefaults
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.Unroll

@Issue("494")
class OwnerProvidedDefaultsCheckTest extends AbstractDSLSpec {

    def "accepts an inherited non-empty JavaBean contract and ignores non-property methods"() {
        when:
        createNonDslClass '''
            interface RegionalDetails {
                String getRegion()
            }

            interface ShipmentDetails extends RegionalDetails {
                String getRepository()
                List<Customer> getCustomers()
                void refresh()
            }

            @DSL
            class Customer {
                @Key String name
            }

            @DSL
            class Product implements ShipmentDetails {
                String region
                String repository
                List<Customer> customers

                void refresh() { }
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                String region
                String repository
                List<Customer> customers

                void refresh() { }
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "deduplicates compatible properties from repeated contracts"() {
        when:
        createNonDslClass '''
            interface GeneralDetails {
                CharSequence getRepository()
            }

            interface ReleaseDetails {
                String getRepository()
                boolean isPublished()
            }

            @DSL
            class Product implements GeneralDetails, ReleaseDetails {
                String repository
                boolean published
            }

            @DSL
            @OwnerProvidedDefaults(GeneralDetails)
            @OwnerProvidedDefaults(ReleaseDetails)
            class ProductRelease implements GeneralDetails, ReleaseDetails {
                @Owner Product product
                String repository
                boolean published
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
        getClass('ProductRelease').getAnnotationsByType(OwnerProvidedDefaults)*.value()*.simpleName ==
                ['GeneralDetails', 'ReleaseDetails']
    }

    def "accepts donor generics within contract wildcard bounds"() {
        when:
        createNonDslClass '''
            interface BoundedDetails {
                List<? extends CharSequence> getNames()
                List<? super String> getAliases()
            }

            @DSL
            class Product implements BoundedDetails {
                List<String> names
                List<Object> aliases
            }

            @DSL
            @OwnerProvidedDefaults(BoundedDetails)
            class ProductRelease implements BoundedDetails {
                @Owner Product product
                List<String> names
                List<Object> aliases
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "accepts contained donor wildcards"() {
        when:
        createNonDslClass '''
            interface BoundedDetails {
                List<? extends CharSequence> getNames()
                List<? super String> getAliases()
            }

            @DSL
            class Product implements BoundedDetails {
                List<? extends String> names
                List<? super CharSequence> aliases
            }

            @DSL
            @OwnerProvidedDefaults(BoundedDetails)
            class ProductRelease implements BoundedDetails {
                @Owner Product product
                List<? extends CharSequence> names
                List<? super String> aliases
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "rejects donor generics outside contract wildcard bounds"() {
        expect:
        def error = compilationError '''
            interface BoundedDetails {
                List<? extends CharSequence> getNames()
                List<? super String> getAliases()
                List<?> getAnything()
                List<? extends CharSequence> getTitles()
                List<? extends CharSequence> getDescriptions()
            }

            @DSL
            class Product implements BoundedDetails {
                List<Integer> names
                List<Integer> aliases
                List<String> anything
                List<? super String> titles
                List<? extends Object> descriptions
            }

            @DSL
            @OwnerProvidedDefaults(BoundedDetails)
            class ProductRelease implements BoundedDetails {
                @Owner Product product
                List<String> names
                List<Object> aliases
                List<String> anything
                List<? extends CharSequence> titles
                List<? extends CharSequence> descriptions
            }
        '''
        error.message.contains("cannot read property 'names'")
        error.message.contains("cannot read property 'aliases'")
        error.message.contains("cannot read property 'titles'")
        error.message.contains("cannot read property 'descriptions'")
    }

    def "rejects incompatible getters inherited by one contract when Groovy allows the contract"() {
        given: "Groovy accepts the conflicting interface hierarchy by itself"
        createNonDslClass '''
            interface BareTextDetails {
                List<String> getValues()
            }

            interface BareNumericDetails {
                List<Integer> getValues()
            }

            interface BareConflictingDetails extends BareTextDetails, BareNumericDetails { }
        '''

        when: "the accepted hierarchy is used as an owner-provided-defaults contract"
        def error = compilationError '''
            interface TextDetails {
                List<String> getValues()
            }

            interface NumericDetails {
                List<Integer> getValues()
            }

            interface ConflictingDetails extends TextDetails, NumericDetails { }

            @DSL
            class Product implements ConflictingDetails {
                List values
            }

            @DSL
            @OwnerProvidedDefaults(ConflictingDetails)
            class ProductRelease implements ConflictingDetails {
                @Owner Product product
                List values
            }
        '''

        then: "the owner-provided-defaults validator rejects the ambiguity"
        error.message.contains("OwnerProvidedDefaults contract ConflictingDetails inherits incompatible types")
        error.message.contains("property 'values'")
        error.message.contains("String")
        error.message.contains("Integer")
    }

    @Unroll
    def "rejects #description"() {
        expect:
        def error = compilationError(source)
        error.message.contains(expectedMessage)
        error.message.contains("OwnerProvidedDefaults")

        where:
        description                                  | expectedMessage                              | source
        "a contract without a JavaBean getter"       | "does not declare a JavaBean getter"         | '''
            interface EmptyContract {
                String repository()
                void refresh()
            }

            @DSL
            class Product implements EmptyContract {
                String repository() { 'central' }
                void refresh() { }
            }

            @DSL
            @OwnerProvidedDefaults(EmptyContract)
            class ProductRelease implements EmptyContract {
                @Owner Product product
                String repository() { 'release' }
                void refresh() { }
            }
        '''
        "a recipient that does not implement its contract" | "must implement contract"                  | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease {
                @Owner Product product
                String repository
            }
        '''
        "a recipient without a compatible owner"     | "requires exactly one compatible declared @Owner field" | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product { }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                String repository
            }
        '''
        "a recipient with two compatible owners"     | "found 2 compatible declared @Owner fields"  | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product primaryProduct
                @Owner Product fallbackProduct
                String repository
            }
        '''
        "a donor without a readable property"        | "donor field 'product' cannot read property 'repository'" | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails { }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                String repository
            }
        '''
        "a recipient without a configurable property" | "recipient cannot configure property 'repository'" | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product

                String getRepository() { 'computed' }
            }
        '''
        "a recipient with a final contract property" | "recipient cannot configure property 'repository'" | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                final String repository = 'fixed'
            }
        '''
        "an incompatible recipient property type"    | "recipient property 'repository' has type java.lang.Integer" | '''
            interface ShipmentDetails {
                String getRepository()
            }

            @DSL
            class Product implements ShipmentDetails {
                String repository
            }

            @DSL
            @OwnerProvidedDefaults(ShipmentDetails)
            class ProductRelease implements ShipmentDetails {
                @Owner Product product
                Integer repository

                String getRepository() { repository?.toString() }
            }
        '''
    }

    def "rejects incompatible repeated-contract declarations for one property"() {
        expect:
        def error = compilationError '''
            interface TextDetails {
                List<String> getValues()
            }

            interface NumericDetails {
                List<Integer> getValues()
            }

            @DSL
            class TextOwner implements TextDetails {
                List<String> values
            }

            @DSL
            class NumericOwner implements NumericDetails {
                List<Integer> values
            }

            @DSL
            @OwnerProvidedDefaults(TextDetails)
            @OwnerProvidedDefaults(NumericDetails)
            class Recipient implements TextDetails, NumericDetails {
                @Owner TextOwner textOwner
                @Owner NumericOwner numericOwner
                List values
            }
        '''
        error.message.contains("OwnerProvidedDefaults contracts")
        error.message.contains("property 'values'")
        error.message.contains("String")
        error.message.contains("Integer")
    }

    private MultipleCompilationErrorsException compilationError(String source) {
        try {
            createClass(source)
        } catch (MultipleCompilationErrorsException error) {
            return error
        }
        throw new AssertionError("Expected compilation to fail")
    }
}
