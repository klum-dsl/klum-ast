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
import com.blackbuild.klum.ast.runtime.KlumModelException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import javax.tools.ToolProvider
import java.lang.reflect.Modifier
import java.lang.reflect.InvocationTargetException

@Issue("925")
class InheritedBuilderSetterTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Inheritance.md#abstract-model-properties-with-builder-setters")
    def "abstract Builder setter binds to subtype storage and default"() {
        given:
        createClass(sensorSchema())

        when:
        def defaulted = WindowSensor.Create.With {
            window { displayName 'kitchen' }
        }
        def configured = WindowSensor.Create.With {
            window { displayName 'kitchen' }
            rename 'custom sensor'
        }
        def parentDefaulted = WindowSensor.Create.With {
            window { displayName 'kitchen' }
            preferredDisplayName 'parent default sensor'
        }

        then:
        defaulted.displayName == 'window sensor kitchen'
        configured.displayName == 'custom sensor'
        parentDefaulted.displayName == 'parent default sensor'

        and: 'the parent owns a read contract, while only the subtype owns storage'
        !Sensor.declaredFields.any { it.name == 'displayName' }
        WindowSensor.declaredFields.count { it.name == 'displayName' } == 1
        Sensor.getDeclaredMethod('getDisplayName').returnType == String
        hasNoMethod(Sensor, 'setDisplayName', String)
        hasNoMethod(WindowSensor, 'setDisplayName', String)
        hasNoMethod(WindowSensor, 'rename', String)
        hasNoMethod(WindowSensor, 'defaultDisplayName')
    }

    def "parent setter works with a #compilation ancestor and #defaultDescription"() {
        given:
        String parent = '''
            @DSL abstract class Sensor {
                abstract String getDisplayName()
                @Builder.Method abstract void setDisplayName(String displayName)
                @Builder.Method void rename(String value) { setDisplayName(value) }
            }
        '''
        String child = """
            @DSL class WindowSensor extends Sensor {
                $defaultAnnotation String displayName
            }
        """
        if (compilation == 'binary') {
            createClass(parent)
            createSecondaryClass(child)
        } else {
            createClass(parent + child)
        }

        when:
        instance = WindowSensor.Create.With { rename 'custom sensor' }

        then:
        instance.displayName == 'custom sensor'

        where:
        compilation | defaultDescription | defaultAnnotation
        'source'    | 'no default'       | ''
        'binary'    | 'no default'       | ''
        'source'    | 'a default'        | "@Default(code = { 'default sensor' })"
        'binary'    | 'a default'        | "@Default(code = { 'default sensor' })"
    }

    def "abstract setter resolves an ancestor getter with #compilation compilation"() {
        given:
        String sensor = '''
            @DSL abstract class Sensor {
                abstract String getDisplayName()
            }
        '''
        String configurable = '''
            @DSL abstract class ConfigurableSensor extends Sensor {
                @Builder.Method abstract void setDisplayName(String value)
                @Builder.Method void rename(String value) { setDisplayName(value) }
            }
        '''
        String concrete = '''
            @DSL class WindowSensor extends ConfigurableSensor {
                String displayName
            }
        '''
        switch (compilation) {
            case 'all source':
                createClass(sensor + configurable + concrete)
                break
            case 'binary parents':
                createClass(sensor + configurable)
                createSecondaryClass(concrete)
                break
            case 'binary getter ancestor':
                createClass(sensor)
                createSecondaryClass(configurable + concrete)
                break
            case 'all separate':
                createClass(sensor)
                createSecondaryClass(configurable)
                createSecondaryClass(concrete)
                break
        }
        Class<?> hiddenBuilder = getBuilderClass('ConfigurableSensor')
        Class<?> publicBuilder = getClass('ConfigurableSensor_DSL$Builder')
        Class<?> publicSubtypeBuilder = getClass('WindowSensor_DSL$Builder')
        def retained

        when:
        def direct = WindowSensor.Create.With {
            setDisplayName('direct sensor')
            retained = delegate
        }
        def renamed = WindowSensor.Create.With { rename('renamed sensor') }

        then:
        direct.displayName == 'direct sensor'
        renamed.displayName == 'renamed sensor'
        !Modifier.isAbstract(hiddenBuilder.getDeclaredMethod('setDisplayName', String).modifiers)
        publicBuilder.getDeclaredMethod('setDisplayName', String).returnType == Void.TYPE
        publicSubtypeBuilder.getMethod('setDisplayName', String).returnType == Void.TYPE
        publicBuilder.isAssignableFrom(publicSubtypeBuilder)
        WindowSensor.getMethod('getDisplayName').returnType == String
        hasNoMethod(Sensor, 'setDisplayName', String)
        hasNoMethod(ConfigurableSensor, 'setDisplayName', String)
        hasNoMethod(WindowSensor, 'setDisplayName', String)
        !Sensor.declaredFields.any { it.name == 'displayName' }
        !ConfigurableSensor.declaredFields.any { it.name == 'displayName' }
        WindowSensor.declaredFields.count { it.name == 'displayName' } == 1

        when:
        retained.rename('late sensor')

        then:
        thrown(KlumModelException)

        where:
        compilation << ['all source', 'binary parents', 'binary getter ancestor', 'all separate']
    }

    def "getter-only parent supports subtype default"() {
        when:
        createClass('''
            @DSL abstract class Sensor { abstract String getDisplayName() }
            @DSL class WindowSensor extends Sensor {
                @Default(code = { 'window sensor kitchen' }) String displayName
            }
        ''')
        instance = WindowSensor.Create.One()

        then:
        instance.displayName == 'window sensor kitchen'
    }

    def "concrete Builder setter supports subtype default"() {
        when:
        createClass('''
            @DSL abstract class Sensor {
                abstract String getDisplayName()
                @Builder.Method void setDisplayName(String value) { this.displayName = value }
            }
            @DSL class WindowSensor extends Sensor {
                @Default(code = { 'window sensor kitchen' }) String displayName
            }
        ''')
        instance = WindowSensor.Create.One()

        then:
        instance.displayName == 'window sensor kitchen'
    }

    def "abstract accessor appears on hidden public and mirrored Builder contracts"() {
        given:
        createClass(sensorSchema())
        Class<?> parentBuilder = getClass('sensors.Sensor_DSL$Builder')
        Class<?> childBuilder = getClass('sensors.WindowSensor_DSL$Builder')
        Class<?> hiddenParent = getBuilderClass('sensors.Sensor')

        when:
        Class<?> consumer = createSecondaryClass('''
            package sensors
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticConsumer {
                static void configure(Sensor_DSL.Builder<?> sensor) {
                    sensor.setDisplayName('static sensor')
                }
            }
        ''')
        instance = WindowSensor.Create.With {
            window { displayName 'kitchen' }
            consumer.configure(delegate)
        }

        then:
        instance.displayName == 'static sensor'
        !Modifier.isAbstract(hiddenParent.modifiers)
        !Modifier.isAbstract(hiddenParent.getDeclaredMethod('setDisplayName', String).modifiers)
        parentBuilder.getDeclaredMethod('setDisplayName', String).returnType == Void.TYPE
        parentBuilder.getDeclaredMethod('setDisplayName', String).getAnnotation(Builder.Method)
        childBuilder.getMethod('setDisplayName', String).returnType == Void.TYPE
        parentBuilder.isAssignableFrom(childBuilder)

        when:
        compileJavaConsumer()
        File mirrorRoot = new File(tempFolder.root, 'mirrors')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'sensors/Sensor_DSL.class')
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'sensors/Sensor_DSL.java').text

        then:
        mirror.contains('void setDisplayName(String displayName)')
        !mirror.contains('defaultDisplayName')
    }

    def "inherited accessor uses normal sealing guards"() {
        given:
        createClass(sensorSchema())
        def retained
        WindowSensor.Create.With {
            window { displayName 'kitchen' }
            retained = delegate
        }

        when:
        getBuilderClass('sensors.Sensor').getDeclaredMethod('setDisplayName', String).invoke(retained, 'late')

        then:
        def error = thrown(InvocationTargetException)
        error.cause instanceof KlumModelException
    }

    def "unsupported abstract Builder method #declaration reports an accessor diagnostic"() {
        when:
        createClass("""
            @DSL abstract class Sensor {
                abstract String getDisplayName()
                @Builder.Method $declaration
            }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Abstract @Builder.Method requires a void setX(value) accessor matching an abstract Model getX()')
        !error.message.contains('NullPointerException')

        where:
        declaration << [
                'abstract void rename(String value)',
                'abstract String setDisplayName(String value)',
                'abstract void setDisplayName(Integer value)',
                'abstract void setOtherName(String value)',
                'abstract void setDisplayName()'
        ]
    }

    def "abstract accessor requires exact types even when the setter type is a subtype"() {
        when:
        createClass('''
            @DSL abstract class Sensor {
                abstract CharSequence getDisplayName()
            }
            @DSL abstract class ConfigurableSensor extends Sensor {
                @Builder.Method abstract void setDisplayName(String value)
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Abstract @Builder.Method requires a void setX(value) accessor matching an abstract Model getX()')
        !error.message.contains('NullPointerException')
        !error.message.contains('BUG!')
    }

    private static String sensorSchema() {
        '''
            package sensors

            @DSL abstract class Sensor {
                abstract String getDisplayName()
                @Builder.Method abstract void setDisplayName(String displayName)
                String preferredDisplayName

                @Builder.Method void rename(String value) { setDisplayName(value) }

                @Default void defaultDisplayName() {
                    if (preferredDisplayName) setDisplayName(preferredDisplayName)
                }
            }

            @DSL class Window { String displayName }

            @DSL class WindowSensor extends Sensor {
                Window window
                @Default(code = { "window sensor $window.displayName" })
                String displayName
            }
        '''
    }

    private void compileJavaConsumer() {
        File sourceFile = new File(tempFolder.root, 'sensors/JavaConsumer.java')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = '''
            package sensors;
            public final class JavaConsumer {
                public static void configure(Sensor_DSL.Builder<?> sensor) {
                    sensor.setDisplayName("java sensor");
                }
            }
        '''.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        ByteArrayOutputStream errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(null, null, errors,
                '-classpath', classpath, '-d', compilerConfiguration.targetDirectory.absolutePath, sourceFile.absolutePath)
        assert result == 0: errors.toString()
    }
}
