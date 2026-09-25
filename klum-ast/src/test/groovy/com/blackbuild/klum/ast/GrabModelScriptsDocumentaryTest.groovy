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

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.klum.cast.KlumCastValidated
import org.apache.ivy.Ivy
import org.jspecify.annotations.NullMarked
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Issue("552")
@Tag("documentary")
@See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Grab-Model-Scripts.md#run-a-connected-local-model")
class GrabModelScriptsDocumentaryTest extends AbstractDSLSpec {

    @Issue("794")
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
        installKlumRuntimeClosure(repository)
        installSupportDependency(repository)
        File grapeRoot = new File(tempFolder.root, 'grape-home')
        File grapeConfig = createGrapeConfig(grapeRoot, repository)

        and:
        File modelScript = new File(tempFolder.root, 'catalog.groovy')
        modelScript.text = '''
            @Grab('com.example.platform:deployment-schema:1.4.2')
            import com.example.platform.Deployment
            import com.blackbuild.klum.ast.runtime.KlumModelException

            def initialClasspath = System.getProperty('java.class.path').split(File.pathSeparator) as List
            assert initialClasspath.size() == 2
            assert initialClasspath.every { !it.toLowerCase().contains('klum') }
            assert Class.forName('com.blackbuild.klum.ast.runtime.internal.KlumInstanceProxy',
                    false, Deployment.classLoader)

            def deployment = Deployment.Create.With('catalog') {
                environment 'production'
                service {
                    image 'catalog:1.0'
                }
            }

            assert deployment.service.image == 'catalog:1.0'
            assert Deployment.classLoader.getResource('deployment-support.marker')

            try {
                Deployment.Create.With('catalog') {
                    service(nmae: 'catalog:1.0')
                }
                assert false: 'an unknown named-map key must fail'
            } catch (KlumModelException error) {
                assert error.message.contains("Unknown named-map Builder call 'nmae'")
                assert error.message.contains('Model com.example.platform.Service')
                assert error.cause.class.name == 'groovy.lang.MissingMethodException'
            }
            println "STANDALONE_GRAB_OK:${deployment.name}:${deployment.service.image}"
        '''

        when:
        def result = runIsolatedModel(modelScript, grapeRoot, grapeConfig)

        then:
        result.finished
        result.exitCode == 0
        result.output.contains('STANDALONE_GRAB_OK:catalog:catalog:1.0')
    }

    private void installSchema(File repository) {
        File module = moduleDirectory(repository, 'com.example.platform', 'deployment-schema', '1.4.2')
        createJar(compilerConfiguration.targetDirectory, new File(module, 'deployment-schema-1.4.2.jar'))
        String klumVersion = requiredSystemProperty('klumVersion')
        new File(module, 'deployment-schema-1.4.2.pom').text = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example.platform</groupId>
              <artifactId>deployment-schema</artifactId>
              <version>1.4.2</version>
              <dependencyManagement>
                <dependencies>
                  <dependency>
                    <groupId>com.blackbuild.klum.ast</groupId>
                    <artifactId>klum-ast-bom</artifactId>
                    <version>$klumVersion</version>
                    <type>pom</type>
                    <scope>import</scope>
                  </dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency>
                  <groupId>com.blackbuild.klum.ast</groupId>
                  <artifactId>klum-ast-runtime</artifactId>
                  <scope>compile</scope>
                </dependency>
                <dependency>
                  <groupId>com.example.platform</groupId>
                  <artifactId>deployment-support</artifactId>
                  <version>1.0.0</version>
                </dependency>
              </dependencies>
            </project>
        """.stripIndent()
    }

    private static void installKlumRuntimeClosure(File repository) {
        String klumVersion = requiredSystemProperty('klumVersion')
        installPublishedModule(repository, 'com.blackbuild.klum.ast', 'klum-ast-runtime', klumVersion,
                requiredSystemFile('klumRuntimeJar'), requiredSystemFile('klumRuntimePom'))
        installPublishedModule(repository, 'com.blackbuild.klum.ast', 'klum-ast-annotations', klumVersion,
                requiredSystemFile('klumAnnotationsJar'), requiredSystemFile('klumAnnotationsPom'))
        installPom(repository, 'com.blackbuild.klum.ast', 'klum-ast-bom', klumVersion,
                requiredSystemFile('klumBomPom'))

        installLeafModule(repository, 'com.blackbuild.annodocimal', 'anno-docimal-annotations', '1.0.1',
                codeSource(AnnoDoc), [['org.jspecify', 'jspecify', '1.0.0']])
        installLeafModule(repository, 'org.jspecify', 'jspecify', '1.0.0', codeSource(NullMarked))
        installLeafModule(repository, 'com.blackbuild.klum.cast', 'klum-cast-annotations', '0.4.0',
                codeSource(KlumCastValidated))
    }

    private static void installSupportDependency(File repository) {
        File module = moduleDirectory(repository, 'com.example.platform', 'deployment-support', '1.0.0')
        createJar(new File(module, 'deployment-support-1.0.0.jar'),
                'deployment-support.marker', 'resolved transitively')
        writePom(new File(module, 'deployment-support-1.0.0.pom'),
                'com.example.platform', 'deployment-support', '1.0.0')
    }

    private static void installPublishedModule(
            File repository,
            String group,
            String artifact,
            String version,
            File artifactFile,
            File pomFile
    ) {
        File module = moduleDirectory(repository, group, artifact, version)
        Files.copy(artifactFile.toPath(), new File(module, "$artifact-${version}.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        Files.copy(pomFile.toPath(), new File(module, "$artifact-${version}.pom").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
    }

    private static void installPom(
            File repository,
            String group,
            String artifact,
            String version,
            File pomFile
    ) {
        File module = moduleDirectory(repository, group, artifact, version)
        Files.copy(pomFile.toPath(), new File(module, "$artifact-${version}.pom").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
    }

    private static void installLeafModule(
            File repository,
            String group,
            String artifact,
            String version,
            File artifactFile,
            List<List<String>> dependencies = []
    ) {
        File module = moduleDirectory(repository, group, artifact, version)
        Files.copy(artifactFile.toPath(), new File(module, "$artifact-${version}.jar").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
        writePom(new File(module, "$artifact-${version}.pom"), group, artifact, version, dependencies)
    }

    private static File moduleDirectory(File repository, String group, String artifact, String version) {
        File module = new File(repository, "${group.replace('.', '/')}/$artifact/$version")
        assert module.mkdirs()
        module
    }

    private static void writePom(
            File pomFile,
            String group,
            String artifact,
            String version,
            List<List<String>> dependencies = []
    ) {
        String dependencyXml = dependencies.collect { dependency ->
            """
                <dependency>
                  <groupId>${dependency[0]}</groupId>
                  <artifactId>${dependency[1]}</artifactId>
                  <version>${dependency[2]}</version>
                </dependency>
            """.stripIndent()
        }.join('')
        pomFile.text = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
              <dependencies>
                $dependencyXml
              </dependencies>
            </project>
        """.stripIndent()
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

    private Map<String, Object> runIsolatedModel(File script, File grapeRoot, File grapeConfig) {
        List<File> bootstrapClasspath = [codeSource(GroovyShell), codeSource(Ivy)]
        assert bootstrapClasspath.size() == 2
        assert bootstrapClasspath.every { !it.name.toLowerCase().contains('klum') }
        File outputFile = new File(tempFolder.root, 'model-output.txt')
        Process process = new ProcessBuilder(
                new File(System.getProperty('java.home'), 'bin/java').absolutePath,
                "-Dgrape.root=${grapeRoot.absolutePath}",
                "-Dgrape.config=${grapeConfig.absolutePath}",
                '-cp',
                bootstrapClasspath*.absolutePath.join(File.pathSeparator),
                'groovy.ui.GroovyMain',
                script.absolutePath
        )
                .directory(tempFolder.root)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        [finished: finished, exitCode: finished ? process.exitValue() : null, output: outputFile.text]
    }

    private static File codeSource(Class<?> type) {
        new File(type.protectionDomain.codeSource.location.toURI())
    }

    private static File requiredSystemFile(String name) {
        File file = new File(requiredSystemProperty(name))
        assert file.file: "System property '$name' does not identify a file: $file"
        file
    }

    private static String requiredSystemProperty(String name) {
        String value = System.getProperty(name)
        assert value: "Missing required system property '$name'"
        value
    }
}
