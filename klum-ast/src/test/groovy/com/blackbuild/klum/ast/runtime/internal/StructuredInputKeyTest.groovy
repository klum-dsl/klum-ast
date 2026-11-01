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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue

@Issue('954')
class StructuredInputKeyTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader

    private Class schema(String source, String name = 'keyinput.Deployment') {
        createClass(source)
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName(name, false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    def 'self-contained root identity is available before PostCreate and input remains unchanged'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                String observed
                @PostCreate void created() { observed = name }
            }
        ''')
        def input = [name: 'production']

        when:
        def model = KlumStructuredInput.readRoot(deployment, input, 'deployment.yaml')

        then:
        model.name == 'production'
        model.observed == 'production'
        input == [name: 'production']
    }
    def 'actual inherited key naming is resolved from its declaration: #annotation'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
            @interface JsonIgnore { boolean value() default true }
        '''
        def deployment = schema("""
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL abstract class Base { @Key $annotation String name }
            @DSL class Deployment extends Base { String key }
        """)

        when:
        def model = KlumStructuredInput.readRoot(deployment, [(spelling): '', key: 'ordinary'], 'keys.yaml')

        then:
        model.name == ''
        model.key == 'ordinary'

        where:
        annotation                                   | spelling
        ''                                           | 'name'
        "@JsonProperty('deployment/id~')"             | 'deployment/id~'
        "@JsonProperty('')"                           | 'name'
        "@JsonIgnore(false) @JsonProperty('identity')" | 'identity'
    }

    def 'missing null and non-String root keys fail before initialization: #payload'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                static { throw new AssertionError('Schema initialization') }
                @Key @JsonProperty('id/~') String name
            }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, payload, 'bad-identity.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('bad-identity.yaml')
        failure.message.contains(pointer)
        failure.message.contains('keyinput.Deployment#name')
        failure.message.contains('required construction key')
        failure.message.contains('Deployment_DSL$Builder')
        failure.message.contains(detail)
        failure.cause == null

        where:
        payload                         | pointer  | detail
        [:]                             | '/id~1~0'| 'absent'
        ['id/~': null]                  | '/id~1~0'| 'actual null'
        ['id/~': 17]                    | '/id~1~0'| 'Integer'
        ['id/~': true]                  | '/id~1~0'| 'Boolean'
        ['id/~': []]                    | '/id~1~0'| 'ArrayList'
        ['id/~': [:]]                   | '/id~1~0'| 'LinkedHashMap'
        ['id/~': "${'text'}"]           | '/id~1~0'| 'GString'
    }

    def 'ignored key rejects self-contained roots with explicit-root guidance while explicit roots remain usable'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonIgnore { boolean value() default true }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonIgnore
            @DSL class Deployment { @Key @JsonIgnore String name; String value }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, [:], 'ignored.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('@JsonIgnore(true)')
        failure.message.contains('keyinput.Deployment#name')
        failure.message.contains('explicit rootKey')

        when:
        def model = KlumStructuredInput.readRoot(deployment, 'external', [value: 'ok'], 'explicit.yaml')

        then:
        model.name == 'external'
        model.value == 'ok'
    }

    def 'key name collisions reject only member-key positions and identify both declarations'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                @Key @JsonProperty('value') String name
                String value
            }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, [value: 'collision'], 'collision.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('external-name collision')
        failure.message.contains('keyinput.Deployment#name')
        failure.message.contains('keyinput.Deployment#value')
        failure.message.contains('/value')

        when:
        def model = KlumStructuredInput.readRoot(deployment, 'external', [value: 'ordinary'], 'external.yaml')

        then:
        model.name == 'external'
        model.value == 'ordinary'
    }

    def 'direct keyed children extract their own identities at any depth before lifecycle'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment { Service service }
            @DSL class Service {
                @Key String name
                Service nested
                String observed
                @PostCreate void created() { observed = name }
            }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(deployment,
                [service: [name: 'catalog', nested: [name: 'storage']]], 'nested.yaml')
        def absent = KlumStructuredInput.readRoot(deployment, [:], 'absent.yaml')

        then:
        model.service.name == 'catalog'
        model.service.observed == 'catalog'
        model.service.nested.name == 'storage'
        model.service.nested.observed == 'storage'
        absent.service == null
    }

    @Issue('952')
    def 'missing child identity stops before its allocation after earlier siblings: #payload'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                static List<String> events = []
                String value = 'before'
                Service service
                @Builder.Method void record(String text) { value = text; events << text }
            }
            @DSL class Service {
                @Key String name
                String allocation = { Deployment.events << 'allocated'; 'allocated' }()
                @PostCreate void created() { Deployment.events << name }
                Service nested
            }
        ''')
        def failure

        when:
        def model = deployment.Create.With('fixed') {
            try { KlumStructuredInput.applyToBuilder(delegate, payload, 'active.yaml') }
            catch (KlumModelException caught) { failure = caught }
        }

        then:
        failure != null
        failure.message.contains(pointer)
        failure.message.contains('required construction key')
        model.name == 'fixed'
        model.value == 'after'
        model.service == null
        deployment.events == events

        where:
        payload                                                       | pointer | events
        [record: 'after', service: [:]]                                | '/service/name' | ['after']
        [record: 'after', service: [name: 'ok', nested: [:]]]            | '/service/nested/name' | ['after', 'allocated', 'ok']
        [record: 'after', service: [name: null]]                        | '/service/name' | ['after']
    }

    def 'external identity sources forbid inner keys even when equal: #position #inner'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                @Field(key = Field.FieldName) Service fixed
                @Field(key = { 'derived' }) Service derived
                Map<String, Service> services
                String value
            }
            @DSL class Service { @Key String name }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, 'root', payload, 'external.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(pointer)
        failure.message.contains(authority)
        failure.message.contains('forbidden even if equal')

        where:
        position  | inner       | payload                              | pointer            | authority
        'root'    | 'root'      | [name: 'root']                       | '/name'            | 'explicit rootKey'
        'root'    | 'different' | [name: 'different']                  | '/name'            | 'explicit rootKey'
        'fixed'   | 'fixed'     | [fixed: [name: 'fixed']]             | '/fixed/name'      | 'Schema key provider'
        'fixed'   | 'different' | [fixed: [name: 'different']]         | '/fixed/name'      | 'Schema key provider'
        'derived' | 'derived'   | [derived: [name: 'derived']]         | '/derived/name'    | 'Schema key provider'
        'map'     | 'outer'     | [services: [outer: [name: 'outer']]]  | '/services/outer/name' | 'outer Map entry key'
        'map'     | 'different' | [services: [outer: [name: 'different']]] | '/services/outer/name' | 'outer Map entry key'
    }

    @Issue('952')
    def 'active Builder identity cannot be confirmed or changed after earlier operations: #inner'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment { @Key String name; String value = 'before' }
        ''')
        def failure

        when:
        def model = deployment.Create.With('fixed') {
            try { KlumStructuredInput.applyToBuilder(delegate, [value: 'after', name: inner], 'active.yaml') }
            catch (KlumModelException caught) { failure = caught }
        }

        then:
        failure.message.contains('already allocated active Builder')
        model.name == 'fixed'
        model.value == 'after'

        where:
        inner << ['fixed', 'different']
    }

    @Issue('952')
    def 'provider executes when its child is reached before later payload failures'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                static List<String> events = []
                @Field(key = { events << 'provider'; 'derived' }) Service service
            }
            @DSL class Service { @Key String name }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, [service: [:], unknown: true], 'bad.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/unknown')
        deployment.events == ['provider']

        when:
        def model = KlumStructuredInput.readRoot(deployment, [service: [:]], 'ok.yaml')

        then:
        model.service.name == 'derived'
        deployment.events == ['provider', 'provider']
    }

    def 'key placement preserves ordered operations while nested builders share lifecycle and ownership: #order'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                static List<String> events = []
                Service service
                @PostCreate void created() { events << "root:create:$name".toString() }
                @Builder.Method void first(String value) { events << value }
                @Builder.Method void second(String value) { events << value }
                @PostApply void applied() { events << 'root:apply' }
                @PostTree void tree() { events << 'root:tree' }
            }
            @DSL class Service {
                @Key String name
                @Owner Deployment owner
                @PostCreate void created() { Deployment.events << "child:create:$name".toString() }
                @PostApply void applied() { Deployment.events << 'child:apply' }
            }
        ''')
        def all = [name: 'production', first: 'one', second: 'two', service: [name: 'catalog']]
        def input = order.collectEntries { [(it): all[it]] }

        when:
        def model = KlumStructuredInput.readRoot(deployment, input, 'ordered.yaml')

        then:
        model.service.owner.is(model)
        deployment.events == ['root:create:production', 'one', 'two', 'child:create:catalog',
                              'child:apply', 'root:apply', 'root:tree']
        input == order.collectEntries { [(it): all[it]] }

        where:
        order << [['name', 'first', 'second', 'service'], ['first', 'name', 'second', 'service'], ['first', 'second', 'service', 'name']]
    }

    def 'ordinary same-key reuse survives earlier operation population and mismatches remain execution errors: #key'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                Service service
                @Builder.Method void prepare(String value) { service('catalog') { image value } }
            }
            @DSL class Service { @Key String name; String image }
        ''')
        def model
        def failure

        when:
        try { model = KlumStructuredInput.readRoot(deployment,
                [prepare: 'before', service: [name: key, image: 'after']], 'reuse.yaml') }
        catch (KlumModelException caught) { failure = caught }

        then:
        if (key == 'catalog') {
            assert failure == null
            assert model.service.name == 'catalog'
            assert model.service.image == 'after'
        } else {
            assert failure.message.contains('Key mismatch')
            assert !failure.message.contains('/service/name')
        }

        where:
        key << ['catalog', 'different']
    }

    def 'ignored child key blocks only supplied member-key relationships'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonIgnore { boolean value() default true }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonIgnore
            @DSL class Deployment {
                Service direct
                @Field(key = Field.FieldName) Service fixed
                Map<String, Service> services
            }
            @DSL class Service { @Key @JsonIgnore String name; String value }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(deployment,
                [fixed: [value: 'fixed'], services: [outer: [value: 'map']]], 'external.yaml')

        then:
        model.direct == null
        model.fixed.name == 'fixed'
        model.services.outer.name == 'outer'

        when:
        KlumStructuredInput.readRoot(deployment, [direct: [:]], 'ignored.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/direct/name')
        failure.message.contains('@JsonIgnore(true)')
        failure.message.contains('Schema derivation/keyed Map')
    }

    def 'four-argument null contract remains explicit and unkeyed overload admits ordinary key properties'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment { String key }
            @DSL class Service { @Key String name }
        ''')
        def service = Class.forName('keyinput.Service', false, artifactLoader)

        when:
        def model = KlumStructuredInput.readRoot(deployment, [key: 'ordinary'], 'unkeyed.yaml')

        then:
        model.key == 'ordinary'

        when:
        KlumStructuredInput.readRoot(deployment, 'unexpected', [:], 'unkeyed.yaml')

        then:
        thrown(KlumModelException)

        when:
        KlumStructuredInput.readRoot(service, null, [:], 'keyed.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('supply an explicit rootKey')
    }

    def 'key collisions with authored operations include rejected collision groups'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                @Key @JsonProperty('identity') String name
                @Builder.Method @JsonProperty('identity') void first(String value) { }
                @Builder.Method @JsonProperty('identity') void second(String value) { }
            }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, [identity: 'ambiguous'], 'collision.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('external-name collision')
        failure.message.contains('Deployment#name')
        failure.message.contains('Deployment#first(')
        failure.message.contains('Deployment#second(')
    }

    def 'native renamed-key spelling may independently name an ordinary property'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                @Key @JsonProperty('identity') String name
                @JsonProperty('name') String label
            }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(deployment, [identity: 'root', name: 'ordinary'], 'alias.yaml')
        def external = KlumStructuredInput.readRoot(deployment, 'outer', [name: 'ordinary'], 'external.yaml')

        then:
        model.name == 'root'
        model.label == 'ordinary'
        external.name == 'outer'
        external.label == 'ordinary'
    }

    @Issue('952')
    def 'required construction identity precedes unrelated member errors'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Deployment {
                static { throw new AssertionError('Schema initialized without required identity') }
                @Key @JsonProperty('identity') String name
            }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, [name: 'not-an-alias'], 'native.yaml')

        then:
        def nativeFailure = thrown(KlumModelException)
        nativeFailure.message.contains('/identity')
        nativeFailure.message.contains('member is absent')

        when:
        KlumStructuredInput.readRoot(deployment, [unknown: true], 'missing.yaml')

        then:
        def missingFailure = thrown(KlumModelException)
        missingFailure.message.contains('/identity')
        missingFailure.message.contains('member is absent')
    }

    def 'actual key named key supports the existing suppressed-getter shape with external naming'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL abstract class Base { @Key @JsonProperty('identity') String key }
            @DSL class Deployment extends Base { String value }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(deployment, [identity: 'production'], 'fallback.yaml')

        then:
        model.key == 'production'
        new InputContractResolver().read(deployment).key().source() == 'keyinput.Base#key'
        !Class.forName('keyinput.Base_DSL$Builder', false, artifactLoader).declaredMethods.any { it.name == 'getKey' }
    }

    def 'key extraction preserves Template defaults and normal validation cleanup'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment { @Key String name; Service service }
            @DSL class Service {
                @Key String name
                String value
                String observed
                @PostCreate void created() { observed = name + ':' + value }
                @Validate void checked() { assert value != 'invalid' }
            }
        ''')
        def service = Class.forName('keyinput.Service', false, artifactLoader)
        def template = service.Create.Template.With(value: 'templated')

        when:
        def model = TemplateManager.withTemplate(service, template) {
            KlumStructuredInput.readRoot(deployment, [name: 'root', service: [name: 'child']], 'template.yaml')
        }

        then:
        model.service.name == 'child'
        model.service.value == 'templated'
        model.service.observed == 'child:templated'

        when:
        KlumStructuredInput.readRoot(deployment,
                [name: 'root', service: [name: 'child', value: 'invalid']], 'validation.yaml')

        then:
        def failure = thrown(KlumValidationException)
        !failure.message.contains('validation.yaml')
        !failure.message.contains('/service/name')

        when:
        def fresh = KlumStructuredInput.readRoot(deployment, [name: 'fresh'], 'fresh.yaml')

        then:
        fresh.name == 'fresh'
    }

    def 'ignored key never suppresses an independent ordinary property in an externally keyed body'() {
        given:
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonIgnore { boolean value() default true }
            @interface JsonProperty { String value() default '' }
        '''
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL class Deployment {
                @Key @JsonIgnore @JsonProperty('identity') String name
                @JsonProperty('name') String label
            }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(deployment, 'external', [name: 'ordinary'], 'ignored-body.yaml')

        then:
        model.name == 'external'
        model.label == 'ordinary'
    }

    def 'inherited key diagnostics identify the selected concrete contract and original declaration: #input'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL abstract class Base { @Key String name }
            @DSL class Deployment extends Base { }
        ''')

        when:
        KlumStructuredInput.readRoot(deployment, input, 'inherited-error.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('public Builder keyinput.Deployment_DSL$Builder')
        failure.message.contains('keyinput.Base#name')
        failure.message.contains('/name')
        failure.message.contains('required construction key')

        where:
        input << [[:], [name: null], [name: 42]]
    }

    @Issue('952')
    def 'late keys precede allocation while unrelated body members wait for traversal: child=#child'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                static List<String> events = []
                Service service
                @PostCreate void created() { events << "root:$name".toString() }
                @Builder.Method void first(String value) { events << value }
                @Builder.Method void last(String value) { events << value }
            }
            @DSL class Service {
                @Key String name
                @PostCreate void created() { Deployment.events << "child:$name".toString() }
                @Builder.Method void first(String value) { Deployment.events << value }
                @Builder.Method void last(String value) { Deployment.events << value }
            }
        ''')
        def body = [first: 'first', unknown: true, last: 'never', name: 'late']
        def input = child ? [first: 'sibling', service: body, name: 'root'] : body

        when:
        KlumStructuredInput.readRoot(deployment, input, 'late.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains(child ? '/service/unknown' : '/unknown')
        deployment.events == (child ? ['root:root', 'sibling', 'child:late', 'first'] : ['root:late', 'first'])

        where:
        child << [false, true]
    }

    @Issue('952')
    def 'construction keys use exact names regardless of Map lookup semantics: #mapKind child=#child'() {
        given:
        def deployment = schema('''
            package keyinput
            import com.blackbuild.klum.ast.*
            @DSL class Deployment {
                @Key String name
                static List<String> events = []
                Service service
                @PostCreate void created() { events << 'root' }
                @Builder.Method void first(String value) { events << value }
            }
            @DSL class Service {
                @Key String name
                String allocation = { Deployment.events << 'child'; 'allocated' }()
            }
        ''')
        Map body = mapKind == 'identity' ? new IdentityHashMap() : new TreeMap(String.CASE_INSENSITIVE_ORDER)
        body.put(new String(mapKind == 'identity' ? 'name' : 'NAME'), 'catalog')
        Map input = child ? [name: 'root', first: 'sibling', service: body] : body
        def result
        def failure

        when:
        try { result = KlumStructuredInput.readRoot(deployment, input, 'map-key.yaml') }
        catch (KlumModelException caught) { failure = caught }

        then:
        if (mapKind == 'identity') {
            assert failure == null
            assert (child ? result.service.name : result.name) == 'catalog'
            assert deployment.events == (child ? ['root', 'sibling', 'child'] : ['root'])
        } else {
            assert result == null
            assert failure.message.contains(child ? '/service/name' : '/name')
            assert failure.message.contains('member is absent')
            assert deployment.events == (child ? ['root', 'sibling'] : [])
        }

        where:
        [mapKind, child] << [['identity', 'case-insensitive'], [false, true]].combinations()
    }

}
