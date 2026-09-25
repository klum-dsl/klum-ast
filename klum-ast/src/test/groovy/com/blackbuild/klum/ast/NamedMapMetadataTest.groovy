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

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.runtime.KlumFactory
import groovy.transform.NamedParam
import groovy.transform.NamedParams
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import javax.tools.ToolProvider

@Issue('792')
class NamedMapMetadataTest extends AbstractDSLSpec {

    def setup() {
        createClass '''
            package namedmeta

            import com.blackbuild.klum.ast.layer3.Cluster
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL
            class Catalog {
                String name
                Item primary

                @Field(members = 'listed')
                List<Item> listedItems

                @Field(members = 'mapped', keyMapping = { it.title })
                Map<String, Item> mappedItems

                @Cluster
                Map<String, Item> getFeatured() { }
            }

            @DSL
            class BaseItem {
                String inherited

                @Builder.Method
                String inheritedOperation(String value) {
                    inherited = value
                }
            }

            @DSL
            class Item extends BaseItem {
                static class Factory extends KlumFactory.Unkeyed<Item> {
                    protected Factory() { super(Item) }

                    Item custom(Map<String, ?> values) {
                        With(values)
                    }
                }

                String title
                int count
                String mode

                @Field(converters = [{ long value -> new Date(value) }])
                Date published

                List<String> tags

                @Builder.Method
                String mode(Integer value) {
                    mode = "number:$value"
                }

                @Builder.Method
                void overloaded(String value) {
                    title = value
                }

                @Builder.Method
                void overloaded(Integer value) {
                    count = value
                }
            }
        '''
    }

    def "static source and separately compiled binary consumers accept the supported literal maps"() {
        when:
        Class<?> consumer = createSecondaryClass '''
            package namedmeta

            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticNamedMapConsumer {
                static Item item() {
                    Item.Create.With(
                            inherited: 'field',
                            inheritedOperation: 'operation',
                            setTitle: 'setter',
                            mode: 7,
                            published: 123L,
                            tag: 'one',
                            overloaded: 'overload')
                }

                static Catalog catalog(Item seed) {
                    Catalog.Create.With(name: 'root') {
                        primary(copyFrom: seed) {
                            title 'single'
                        }
                        listed(title: 'collection')
                        mapped(title: 'map')
                        featured {
                            primary(title: 'cluster')
                        }
                    }
                }

                static Item fromMapVariable() {
                    Map<String, ?> values = [title: 'variable']
                    Item.Create.With(values)
                }
            }
        '''
        def item = consumer.item()
        def seed = getClass('namedmeta.Item').Create.With(title: 'seed')
        def catalog = consumer.catalog(seed)

        then:
        item.inherited == 'operation'
        item.title == 'overload'
        item.mode == 'number:7'
        item.published.time == 123L
        item.tags == ['one']
        catalog.name == 'root'
        catalog.primary.title == 'cluster'
        catalog.listedItems*.title == ['collection']
        catalog.mappedItems.keySet() == ['map'] as Set
        consumer.fromMapVariable().title == 'variable'
    }

    def "same-source static consumers use the generated contract"() {
        when: 'a Schema and static consumer compile together'
        createClass '''
            package namedmetasource

            import groovy.transform.CompileStatic

            @DSL
            class SourceItem {
                String title
            }

            @CompileStatic
            class SameSourceConsumer {
                static SourceItem create() {
                    SourceItem.Create.With(title: 'same source')
                }
            }
        '''

        then:
        getClass('namedmetasource.SameSourceConsumer').create().title == 'same source'
    }

    def "default-parameter eligibility follows the final public one-argument Builder contract"() {
        given:
        createClass '''
            package namedmetadefaults

            @DSL
            class DefaultParameterItem {
                String configured
                boolean strict

                @Builder.Method
                void configure(String value, boolean strict = false) {
                    configured = value
                    this.strict = strict
                }
            }
        '''
        Class<?> item = getClass('namedmetadefaults.DefaultParameterItem')
        Class<?> publicBuilder = getClass('namedmetadefaults.DefaultParameterItem_DSL$Builder')
        def oneArgumentOperation = publicBuilder.methods.find {
            it.name == 'configure' && it.parameterTypes.toList() == [String]
        }
        NamedParam configureMetadata = namedParams(
                item.getField('Create').type.getMethod('With', Map).parameters[0]
        ).find { it.value() == 'configure' }

        expect: 'metadata eligibility is defined by the final public contract, not source-method arity'
        (oneArgumentOperation != null) == (configureMetadata != null)
        oneArgumentOperation == null || configureMetadata.type() == String

        when: 'the generated contract exposes the one-argument overload'
        Class<?> consumer = oneArgumentOperation == null ? null : createSecondaryClass('''
            package namedmetadefaults

            import groovy.transform.CompileStatic

            @CompileStatic
            class DefaultParameterConsumer {
                static DefaultParameterItem viaNamedMap() {
                    DefaultParameterItem.Create.With(configure: 'value')
                }

                static DefaultParameterItem viaDirectBuilderCall() {
                    DefaultParameterItem.Create.With {
                        configure 'value'
                    }
                }
            }
        ''')

        then: 'the map entry and direct Builder call have identical behavior'
        if (oneArgumentOperation != null) {
            def viaNamedMap = consumer.viaNamedMap()
            def viaDirectBuilderCall = consumer.viaDirectBuilderCall()
            assert viaNamedMap.configured == viaDirectBuilderCall.configured
            assert viaNamedMap.strict == viaDirectBuilderCall.strict
            assert viaNamedMap.configured == 'value'
            assert !viaNamedMap.strict
        }
    }

    def "native metadata rejects unknown keys incompatible values computed keys and spread maps"() {
        when:
        createSecondaryClass '''
            package namedmeta
            import groovy.transform.CompileStatic

            @CompileStatic
            class UnknownKeyConsumer {
                static Item create() { Item.Create.With(doesNotExist: 'value') }
            }
        '''
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass '''
            package namedmeta
            import groovy.transform.CompileStatic

            @CompileStatic
            class WrongValueConsumer {
                static Item create() { Item.Create.With(count: 'wrong') }
            }
        '''
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass '''
            package namedmeta
            import groovy.transform.CompileStatic

            @CompileStatic
            class ComputedKeyConsumer {
                static Item create() {
                    String key = 'title'
                    Item.Create.With([(key): 'computed'])
                }
            }
        '''
        then:
        thrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass '''
            package namedmeta
            import groovy.transform.CompileStatic

            @CompileStatic
            class SpreadMapConsumer {
                static Item create() {
                    Map<String, ?> values = [title: 'spread']
                    Item.Create.With(*:values)
                }
            }
        '''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "hidden public reflection and AnnoDocimal mirror contracts carry the same public catalog"() {
        given:
        Class<?> item = getClass('namedmeta.Item')
        Class<?> catalogBuilder = getClass('namedmeta.Catalog_DSL$Builder')
        Class<?> collectionFactory = getClass('namedmeta.Catalog_DSL$Builder$CollectionFactory_listedItems')
        Class<?> clusterFactory = getClass('namedmeta.Catalog_DSL$Builder$ClusterFactory_featured')

        when:
        NamedParam[] root = namedParams(item.getField('Create').type.getMethod('With', Map).parameters[0])
        NamedParam[] hiddenRoot = namedParams(getClass('namedmeta.Item$_Factory').getMethod('With', Map).parameters[0])
        NamedParam[] single = namedParams(catalogBuilder.getMethod('primary', Map).parameters[0])
        NamedParam[] hiddenSingle = namedParams(getClass('namedmeta.Catalog$Builder').getMethod('primary', Map).parameters[0])
        NamedParam[] collection = namedParams(collectionFactory.getMethod('listed', Map).parameters[0])
        NamedParam[] hiddenCollection = namedParams(getClass('namedmeta.Catalog$_listedItems').getMethod('listed', Map).parameters[0])
        NamedParam[] cluster = namedParams(clusterFactory.getMethod('primary', Map).parameters[0])
        NamedParam[] hiddenCluster = namedParams(getClass('namedmeta.Catalog$_featured').getMethod('primary', Map).parameters[0])

        then:
        root*.value() == hiddenRoot*.value()
        root*.value().containsAll(['inherited', 'inheritedOperation', 'setTitle', 'mode', 'published', 'tag', 'copyFrom', 'overloaded'])
        root.find { it.value() == 'count' }.type() == Integer
        root.find { it.value() == 'overloaded' }.type() == Object
        root.every { it.type().primitive || java.lang.reflect.Modifier.isPublic(it.type().modifiers) }
        single*.value() == hiddenSingle*.value()
        single*.value() == root*.value()
        collection*.value() == hiddenCollection*.value()
        collection*.value() == root*.value()
        cluster*.value() == hiddenCluster*.value()
        cluster*.value() == root*.value()

        and: 'excluded APIs remain ordinary Map contracts without named metadata'
        !KlumFactory.UnkeyedBuilderFactory.getMethod('With', Map).parameters[0].isAnnotationPresent(NamedParams)
        !catalogBuilder.getMethod('primary', Map, Class).parameters[0].isAnnotationPresent(NamedParams)
        !item.getField('Create').type.getField('Template').type.getMethod('With', Map).parameters[0]
                .isAnnotationPresent(NamedParams)
        !item.getField('Create').type.getMethod('FromMap', Map).parameters[0].isAnnotationPresent(NamedParams)
        !item.getField('Create').type.getMethod('custom', Map).parameters[0].isAnnotationPresent(NamedParams)

        when:
        File mirrorRoot = new File(tempFolder.root, 'named-map-mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'namedmeta/Item_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'namedmeta/Item_DSL.java').text

        then:
        mirror.contains('@NamedParams')
        mirror.contains('@NamedParam')
        mirror.contains('value = "copyFrom"')
        mirror.contains('type = Integer.class')
        !mirror.contains('Item$Builder.class')
        compileJavaSource(new File(mirrorRoot, 'namedmeta/Item_DSL.java'))
    }

    private static NamedParam[] namedParams(java.lang.reflect.Parameter parameter) {
        parameter.getAnnotation(NamedParams)?.value() ?: []
    }

    private void compileJavaSource(File sourceFile) {
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                null,
                '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath,
                sourceFile.absolutePath
        )
        assert result == 0
    }
}
