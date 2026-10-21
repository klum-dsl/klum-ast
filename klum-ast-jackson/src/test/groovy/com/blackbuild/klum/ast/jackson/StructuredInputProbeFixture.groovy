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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import org.codehaus.groovy.runtime.InvokerHelper

/** Disposable Schema shared by the three-path comparison and binary projection probe. */
class StructuredInputProbeFixture {
    static final String SOURCE = '''
        package inputprobe
        import com.blackbuild.klum.ast.*
        import com.blackbuild.klum.ast.layer3.AutoLink
        import com.blackbuild.klum.ast.layer3.LinkTo
        import com.fasterxml.jackson.annotation.JsonProperty
        import com.fasterxml.jackson.annotation.JsonIgnore
        import java.net.URI

        enum Mode { SAFE, FAST }
        @DSL abstract class ApiRoot {
            String region
            @Builder.Method void inheritedLocale(String value) { region = value }
        }
        @DSL class ProbeRoot extends ApiRoot {
            static List<String> events = []
            @JsonProperty('service-name') String serviceName
            @JsonProperty('class') String category
            @JsonIgnore String internalSetting
            String host
            @Required String requiredValue = 'provided'
            @Validate({ it >= 0 }) int port
            boolean enabled
            Mode mode
            Map<String, String> labels
            ProbeChild child
            @Field(members = 'entry') Map<String, KeyedChild> entries
            @Field(members = 'item') List<ProbeChild> items
            @Field(converters = [{ String value -> URI.create(value) }]) URI source
            @Field(FieldType.LINK) @LinkTo(field = 'child', provider = { delegate }) ProbeChild linked
            @PostCreate void created() { events << 'root:create' }
            @PostApply void applied() { events << 'root:apply' }
            @AutoLink void autolinked() { events << 'root:link' }
            @PostTree void tree() { events << 'root:tree' }
            @Validate void checked() { events << 'root:validate' }
            @Builder.Method void shout(String value) { serviceName = value.toUpperCase() }
            @Builder.Method void endpoint(String host, int port = 8080) { this.host = host; this.port = port }
            @Builder.Method void mapping(Map value) { labels = value }
            @Builder.Method void sequence(List value) { serviceName = value.join(':') }
            @Builder.Method void append(String value) { host = (host ?: '') + value }
            @Builder.Method void fail(String value) { throw new IllegalStateException(value) }
        }
        @DSL class ProbeChild {
            String value = 'default'
            @Owner ProbeRoot owner
            @PostCreate void created() { ProbeRoot.events << 'child:create' }
            @PostApply void applied() { ProbeRoot.events << 'child:apply' }
            @Validate void checked() { ProbeRoot.events << 'child:validate' }
        }
        @DSL class KeyedChild { @Key String name; String value }
        @DSL abstract class AbstractTarget { String value }
        @DSL class UnsupportedRoot { AbstractTarget target }
    '''

    /** Fixture-local candidate grammar only. No inferred admission or new overload/lifecycle engine. */
    static void operations(Object builder, List<List> calls) {
        Map expected = [endpoint: '[String,int]', mapping: 'Map<String,String>', sequence: 'List<String>',
                        labels: 'Map<String,String>', child: 'Map', entries: 'Map<String,Map>', items: 'List<Map>',
                        port: 'int', enabled: 'boolean', mode: 'enum SAFE|FAST', fail: 'String', shout: 'String']
        Map names = ['service-name': 'serviceName', 'class': 'category', mode: 'mode', labels: 'labels',
                     child: 'child', externalChild: 'externalChild', items: 'items', entries: 'entries', shout: 'shout',
                     region: 'region', requiredValue: 'requiredValue', port: 'port', enabled: 'enabled', mapping: 'mapping', sequence: 'sequence',
                     inheritedLocale: 'inheritedLocale', endpoint: 'endpoint', append: 'append', fail: 'fail']
        calls.eachWithIndex { List call, int index ->
            String external = call[0]
            String operation = names[external]
            try {
                if (!operation) throw new NoSuchMethodException('excluded or unknown')
                Object value = call[1]
                if (operation in ['serviceName', 'category', 'region', 'requiredValue', 'shout', 'inheritedLocale', 'append', 'fail'] && !(value instanceof String))
                    throw new IllegalArgumentException('expected String')
                if (operation == 'port' && !(value instanceof Integer)) throw new IllegalArgumentException('expected int')
                if (operation == 'enabled' && !(value instanceof Boolean)) throw new IllegalArgumentException('expected boolean')
                if (operation in ['child', 'externalChild', 'labels', 'mapping', 'entries']) {
                    if (!(value instanceof Map)) throw new IllegalArgumentException('expected data object, no Class/Factory selector')
                }
                if (operation in ['labels', 'mapping'] && !value.every { key, entry -> key instanceof String && entry instanceof String })
                    throw new IllegalArgumentException('expected String map keys and values')
                if (operation == 'sequence' && (!(value instanceof List) || !value.every { it instanceof String }))
                    throw new IllegalArgumentException('expected List<String>')
                if (operation in ['child', 'externalChild']) {
                    String property = operation == 'child' ? 'value' : 'apiValue'
                    if (!value.keySet().every { it == property } || !value.values().every { it instanceof String })
                        throw new IllegalArgumentException('nested controls are not admitted')
                }
                if (operation == 'entries' && (!value.keySet().every { it instanceof String } || !value.values().every { it instanceof Map && it.keySet().every { it == 'value' } && it.values().every { it instanceof String } }))
                    throw new IllegalArgumentException('nested entry controls are not admitted')
                if (operation in ['items', 'sequence'] && !(value instanceof List))
                    throw new IllegalArgumentException('expected data list')
                if (operation == 'mode') value = builder.class.classLoader.loadClass(builder.class.packageName + '.Mode').valueOf(value)
                Object[] arguments
                if (operation == 'endpoint') {
                    if (!(value instanceof List) || value.size() != 2 || !(value[0] instanceof String) || !(value[1] instanceof Integer))
                        throw new IllegalArgumentException('expected [String,int] positional pair')
                    arguments = value.toArray()
                } else if (operation == 'entries') {
                    value.each { key, entry -> InvokerHelper.invokeMethod(builder, 'entry', [entry, key] as Object[]) }
                    return
                } else if (operation == 'items') {
                    value.each { item ->
                        if (!(item instanceof Map) || !item.keySet().every { it == 'value' } || !item.values().every { it instanceof String })
                            throw new IllegalArgumentException('nested item controls are not admitted')
                        InvokerHelper.invokeMethod(builder, 'item', [item] as Object[])
                    }
                    return
                } else {
                    arguments = [value] as Object[]
                }
                InvokerHelper.invokeMethod(builder, operation, arguments)
            } catch (Exception cause) {
                throw new IllegalArgumentException("probe.json#/$index/$external inputprobe.ProbeRoot_DSL.Builder.$operation expected ${expected[operation] ?: 'one admitted value'}; actual ${call[1]?.getClass()?.simpleName}: ${cause.message}", cause)
            }
        }
    }
}
