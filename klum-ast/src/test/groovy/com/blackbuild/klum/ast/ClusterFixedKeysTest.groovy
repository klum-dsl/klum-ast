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

import javax.tools.ToolProvider
import java.net.URL
import java.net.URLClassLoader

@Issue("356")
class ClusterFixedKeysTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Layer3.md#fixed-cluster-keys")
    def "uses a Cluster convention to derive direct relationship keys"() {
        given:
        createClass '''
            package sample

            import com.blackbuild.klum.ast.layer3.Cluster
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy

            @DSL
            abstract class Home {
                @Cluster(value = ManagedZone, fixedKeys = true)
                abstract Map<String, Zone> getZones()
            }

            @Retention(RetentionPolicy.RUNTIME)
            @interface ManagedZone {}

            @DSL
            abstract class Zone {
            }

            @DSL class Kitchen extends Zone {
                @Key String name
                String purpose
            }

            @DSL class Shed {
                @Key String name
                String purpose
            }

            @DSL
            class FloorPlan extends Home {
                @ManagedZone Kitchen kitchen
                Kitchen pantry
                Shed attic
            }
        '''
        builderClass = getBuilderClass('sample.FloorPlan')

        expect: 'the generated Builder accepts the selected field without a key and leaves the unselected field unchanged'
        builderClassHasMethod('kitchen')
        builderClassHasMethod('kitchen', Map)
        builderClassHasNoMethod('kitchen', String)
        builderClassHasNoMethod('kitchen', Map, String)
        builderClassHasMethod('pantry', String)
        builderClassHasMethod('pantry', Map, String)
        !getClass('sample.FloorPlan').getDeclaredField('kitchen').isAnnotationPresent(Field)

        when:
        instance = create('sample.FloorPlan') {
            kitchen(purpose: 'food preparation')
            pantry('dry-goods')
            attic('stored-items') {
                purpose 'storage'
            }
        }

        then:
        instance.kitchen.name == 'kitchen'
        instance.kitchen.purpose == 'food preparation'
        instance.pantry.name == 'dry-goods'
        instance.attic.name == 'stored-items'

        when: 'a statically compiled consumer names only the generated public Builder contract'
        Class<?> consumer = createSecondaryClass('''
            package sample

            import groovy.transform.CompileStatic

            @CompileStatic
            class FixedKeyHomeConsumer {
                static FloorPlan create() {
                    FloorPlan.Create.With {
                        ((FloorPlan_DSL.Builder<FloorPlan>) delegate).kitchen([purpose: 'public contract'])
                    }
                }
            }
        ''', 'sample/FixedKeyHomeConsumer.groovy')

        then:
        consumer.create().kitchen.name == 'kitchen'
        !getClass('sample.FloorPlan_DSL$Builder').methods.any {
            it.name == 'kitchen' && it.parameterTypes.contains(String)
        }

        when: 'a Java consumer compiles against and verifies the generated public Builder contract'
        Class<?> javaConsumer = compileJavaConsumer('''
            package sample;

            import java.lang.reflect.Method;
            import java.util.Map;

            public final class FixedKeyHomeJavaConsumer {
                public static void configure(FloorPlan_DSL.Builder<FloorPlan> builder) {
                    builder.kitchen(Map.of("purpose", "Java public contract"));
                }

                public static void verifyFixedKeyApi() throws NoSuchMethodException {
                    FloorPlan_DSL.Builder.class.getMethod("kitchen", Map.class);
                    for (Method method : FloorPlan_DSL.Builder.class.getMethods()) {
                        if (method.getName().equals("kitchen")) {
                            for (Class<?> parameterType : method.getParameterTypes()) {
                                if (parameterType.equals(String.class)) {
                                    throw new AssertionError("fixed-key Builder API must not expose a key-taking kitchen overload");
                                }
                            }
                        }
                    }
                }
            }
        ''', 'sample/FixedKeyHomeJavaConsumer.java')
        javaConsumer.getMethod('verifyFixedKeyApi').invoke(null)

        then:
        noExceptionThrown()
    }

    def "rejects an unkeyed fixed-key Cluster member"() {
        when:
        createClass '''
            package sample

            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL abstract class Home {
                @Cluster(fixedKeys = true) abstract Map<String, Zone> getZones()
            }

            @DSL class Zone {}

            @DSL class FloorPlan extends Home {
                Zone kitchen
            }
        '''

        then:
        MultipleCompilationErrorsException exception = thrown()
        exception.message.contains('@Cluster(fixedKeys = true) only supports direct, single keyed DSL Object relationship fields; field kitchen is not keyed.')
    }

    def "rejects collection and map fixed-key Cluster members"() {
        when:
        createClass '''
            package sample

            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL abstract class CollectionHome {
                @Cluster(fixedKeys = true) abstract Map<String, Collection<Zone>> getZones()
            }

            @DSL class Zone {
                @Key String name
            }

            @DSL class CollectionFloorPlan extends CollectionHome {
                List<Zone> zones
            }
        '''

        then:
        MultipleCompilationErrorsException collectionException = thrown()
        collectionException.message.contains('field zones is a collection or map.')

        when:
        createClass '''
            package other

            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL abstract class MapHome {
                @Cluster(fixedKeys = true) abstract Map<String, Map<String, Zone>> getZones()
            }

            @DSL class Zone {
                @Key String name
            }

            @DSL class MapFloorPlan extends MapHome {
                Map<String, Zone> zones
            }
        '''

        then:
        MultipleCompilationErrorsException mapException = thrown()
        mapException.message.contains('field zones is a collection or map.')
    }

    def "rejects an explicit field key for a fixed-key Cluster member"() {
        when:
        createClass '''
            package sample

            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL abstract class Home {
                @Cluster(fixedKeys = true) abstract Map<String, Zone> getZones()
            }

            @DSL class Zone {
                @Key String name
            }

            @DSL class FloorPlan extends Home {
                @Field(key = Field.FieldName) Zone kitchen
            }
        '''

        then:
        MultipleCompilationErrorsException exception = thrown()
        exception.message.contains('@Cluster(fixedKeys = true) cannot be combined with an explicit @Field(key = ...) on field kitchen.')
    }

    private Class<?> compileJavaConsumer(String source, String filename) {
        File sourceFile = new File(tempFolder.root, filename)
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        def errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                errors,
                '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath,
                sourceFile.absolutePath
        )
        assert result == 0: errors.toString()
        URLClassLoader consumerLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], loader)
        try {
            return consumerLoader.loadClass(filename.replace('/', '.').replace('.java', ''))
        } finally {
            consumerLoader.close()
        }
    }
}
