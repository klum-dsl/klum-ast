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

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Issue("552")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Grab-Model-Scripts.md#run-a-connected-local-model")
class GrabModelScriptsDocumentaryTest extends AbstractDSLSpec {

    def "runs a standalone Model script against a separately compiled Schema"() {
        given:
        createClass '''
            package com.example.platform

            @DSL
            class Deployment {
                @Key String name
                String environment
                Service service
            }

            @DSL
            class Service {
                String image
            }
        '''

        and:
        File repository = new File(tempFolder.root, 'repository')
        installSchema(repository)
        installSupportDependency(repository)
        File grapeRoot = new File(tempFolder.root, 'grape-home')
        File grapeConfig = createGrapeConfig(grapeRoot, repository)
        String oldGrapeRoot = System.getProperty('grape.root')
        String oldGrapeConfig = System.getProperty('grape.config')
        System.setProperty('grape.root', grapeRoot.absolutePath)
        System.setProperty('grape.config', grapeConfig.absolutePath)

        and:
        def modelScript = '''
            @Grab('com.example.platform:deployment-schema:1.4.2')
            import com.example.platform.Deployment

            def deployment = Deployment.Create.With('catalog') {
                environment 'production'
                service {
                    image 'catalog:1.0'
                }
            }

            assert deployment.service.image == 'catalog:1.0'
            assert Deployment.classLoader.getResource('deployment-support.marker')
            deployment
        '''

        when:
        def modelLoader = new GroovyClassLoader(getClass().classLoader)
        def deployment
        try {
            deployment = new GroovyShell(modelLoader).evaluate(modelScript)
        } finally {
            restoreSystemProperty('grape.root', oldGrapeRoot)
            restoreSystemProperty('grape.config', oldGrapeConfig)
            modelLoader.close()
        }

        then:
        deployment.name == 'catalog'
        deployment.environment == 'production'
        deployment.service.image == 'catalog:1.0'
    }

    private void installSchema(File repository) {
        File module = moduleDirectory(repository, 'deployment-schema', '1.4.2')
        createJar(compilerConfiguration.targetDirectory, new File(module, 'deployment-schema-1.4.2.jar'))
        new File(module, 'deployment-schema-1.4.2.pom').text = '''
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example.platform</groupId>
              <artifactId>deployment-schema</artifactId>
              <version>1.4.2</version>
              <dependencies>
                <dependency>
                  <groupId>com.example.platform</groupId>
                  <artifactId>deployment-support</artifactId>
                  <version>1.0.0</version>
                </dependency>
              </dependencies>
            </project>
        '''.stripIndent()
    }

    private static void installSupportDependency(File repository) {
        File module = moduleDirectory(repository, 'deployment-support', '1.0.0')
        createJar(new File(module, 'deployment-support-1.0.0.jar'),
                'deployment-support.marker', 'resolved transitively')
        new File(module, 'deployment-support-1.0.0.pom').text = '''
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example.platform</groupId>
              <artifactId>deployment-support</artifactId>
              <version>1.0.0</version>
            </project>
        '''.stripIndent()
    }

    private static File moduleDirectory(File repository, String artifact, String version) {
        File module = new File(repository, "com/example/platform/$artifact/$version")
        assert module.mkdirs()
        module
    }

    private static void createJar(File classesDirectory, File jarFile) {
        jarFile.withOutputStream { output ->
            new JarOutputStream(output).withCloseable { jar ->
                classesDirectory.eachFileRecurse { file ->
                    if (!file.file) return
                    String name = classesDirectory.toPath().relativize(file.toPath()).toString().replace(File.separator, '/')
                    jar.putNextEntry(new JarEntry(name))
                    jar.write(file.bytes)
                    jar.closeEntry()
                }
            }
        }
    }

    private static void createJar(File jarFile, String entryName, String contents) {
        jarFile.withOutputStream { output ->
            new JarOutputStream(output).withCloseable { jar ->
                jar.putNextEntry(new JarEntry(entryName))
                jar.write(contents.bytes)
                jar.closeEntry()
            }
        }
    }

    private static File createGrapeConfig(File grapeRoot, File repository) {
        assert grapeRoot.mkdirs()
        File config = new File(grapeRoot, 'grapeConfig.xml')
        config.text = """
            <ivysettings>
              <settings defaultResolver="downloadGrapes"/>
              <resolvers>
                <chain name="downloadGrapes" returnFirst="true">
                  <filesystem name="cachedGrapes">
                    <ivy pattern="${grapeRoot.absolutePath}/grapes/[organisation]/[module]/ivy-[revision].xml"/>
                    <artifact pattern="${grapeRoot.absolutePath}/grapes/[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"/>
                  </filesystem>
                  <ibiblio name="fixture" root="${repository.toURI()}" m2compatible="true"/>
                </chain>
              </resolvers>
            </ivysettings>
        """.stripIndent()
        config
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name)
        } else {
            System.setProperty(name, value)
        }
    }
}
