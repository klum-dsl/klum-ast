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

import com.blackbuild.klum.ast.util.KlumModelProxy
import com.blackbuild.klum.ast.util.KlumObjectSupport
import com.blackbuild.klum.ast.util.KlumException
import com.blackbuild.klum.ast.util.layer3.ModelVisitor
import com.blackbuild.klum.ast.util.layer3.StructureUtil
import org.jetbrains.annotations.NotNull

import javax.tools.ToolProvider
import java.lang.reflect.Modifier

class KlumObjectSupportSpec extends AbstractDSLSpec {

    def "Java callers use explicit completed-object getters for a root and subtree without proxy access"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Owner

            @DSL class Root {
                Child child
            }

            @DSL class Child {
                @Owner Root root
            }
        '''

        when:
        instance = Root.Create.With {
            child {}
        }
        def rootSupport = KlumObjectSupport.of(instance)
        def childSupport = KlumObjectSupport.of(instance.child)
        compileJavaConsumer('''
            import com.blackbuild.klum.ast.util.KlumObjectSupport;

            public final class JavaObjectSupportConsumer {
                public static void inspect(Root root, Child child) {
                    KlumObjectSupport<Root> rootSupport = KlumObjectSupport.of(root);
                    Root supportedRoot = rootSupport.getObject();
                    String rootBreadcrumb = rootSupport.getBreadcrumbPath();
                    String rootModelPath = rootSupport.getModelPath();
                    KlumObjectSupport.Structure<Child> structure = KlumObjectSupport.of(child).getStructure();
                }
            }
        ''')

        then:
        rootSupport.getObject().is(instance)
        childSupport.getObject().is(instance.child)
        rootSupport.getBreadcrumbPath() == '$/Root.With'
        childSupport.getBreadcrumbPath() == '$/Root.With/child'
        rootSupport.getModelPath() == '<root>'
        childSupport.getModelPath() == '<root>.child'
        rootSupport.getBreadcrumbPath() != rootSupport.getModelPath()
        childSupport.getBreadcrumbPath() != childSupport.getModelPath()
        rootSupport.structure.directOwners.empty
        rootSupport.structure.singleOwner.empty

        and: "the public facade stores and exposes only its completed-object target"
        KlumObjectSupport.declaredFields*.type.every { it != KlumModelProxy }
        KlumObjectSupport.methods.every { Modifier.isPublic(it.modifiers) ? it.returnType != KlumModelProxy : true }
        KlumObjectSupport.Structure.methods.every { Modifier.isPublic(it.modifiers) ? it.returnType != KlumModelProxy : true }
        !Serializable.isAssignableFrom(KlumObjectSupport)

        when: 'the target is not a completed DSL Object'
        KlumObjectSupport.of(new Object())

        then:
        def error = thrown(KlumException)
        error.message.contains('not a completed DSL Object')

        when: 'the internal companion itself is supplied'
        KlumObjectSupport.of(KlumModelProxy.getProxyFor(instance))

        then:
        def companionError = thrown(KlumException)
        companionError.message.contains('not a completed DSL Object')
    }

    def "structure support exposes composition owners, relative paths, and typed traversal"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.FieldType
            import com.blackbuild.groovy.configdsl.transform.Owner

            @DSL class Root {
                Child primary
                List<Child> children
                Map<String, Child> childrenByName
                @Field(FieldType.LINK) Child linked
            }

            @DSL class Child {
                @Key String name
                @Owner Root root
                Child loop
            }
        '''
        def existingLink = Child.Create.With('external') {}

        when:
        instance = Root.Create.With {
            primary('primary') {}
            children {
                child('first') {}
                child('second') {}
            }
            childrenByName('map-key') {}
            linked existingLink
        }
        def support = KlumObjectSupport.of(instance)
        def primarySupport = KlumObjectSupport.of(instance.primary)
        def visited = [:]
        support.structure.visit(new ModelVisitor() {
            @Override
            void visit(@NotNull String path, @NotNull Object element, Object container, String nameOfFieldInContainer) {
                visited[path] = element
            }
        })
        def typedPaths = []
        support.structure.visit(Child) { path, child -> typedPaths << path }
        compileJavaConsumer('''
            import com.blackbuild.klum.ast.util.KlumObjectSupport;
            import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
            import java.util.List;
            import java.util.Map;
            import java.util.Optional;
            import java.util.Set;
            import java.util.function.BiConsumer;

            public final class JavaObjectSupportConsumer {
                public static void inspect(Root root, Child child) {
                    KlumObjectSupport.Structure<Root> structure = KlumObjectSupport.of(root).getStructure();
                    Set<Object> owners = KlumObjectSupport.of(child).getStructure().getDirectOwners();
                    Optional<Object> owner = KlumObjectSupport.of(child).getStructure().getSingleOwner();
                    List<Object> hierarchy = KlumObjectSupport.of(child).getStructure().getOwnerHierarchy();
                    Optional<Root> ancestor = KlumObjectSupport.of(child).getStructure().getAncestorOfType(Root.class);
                    String relativePath = structure.getRelativePath(child);
                    structure.visit(new ModelVisitor() {
                        @Override
                        public void visit(String path, Object element, Object container, String field) { }
                    });
                    structure.visit(Child.class, (String path, Child value) -> { });
                    Map<String, Child> children = structure.findAll(Child.class);
                }
            }
        ''')

        then:
        primarySupport.structure.directOwners == [instance] as Set
        primarySupport.structure.singleOwner.get().is(instance)
        primarySupport.structure.ownerHierarchy == [instance.primary, instance]
        primarySupport.structure.getAncestorOfType(instance.class).get().is(instance)
        support.structure.getRelativePath(instance.primary) == 'primary'
        support.structure.getRelativePath(instance.children[1]) == 'children[1]'
        support.structure.getRelativePath(instance.childrenByName['map-key']) == "childrenByName.'map-key'"
        primarySupport.structure.fullPath == 'primary'
        primarySupport.structure.getFullPath('<root>') == '<root>.primary'
        visited == [
                '<root>': instance,
                '<root>.primary': instance.primary,
                '<root>.children[0]': instance.children[0],
                '<root>.children[1]': instance.children[1],
                "<root>.childrenByName.'map-key'": instance.childrenByName['map-key'],
        ]
        typedPaths == ['<root>.primary', '<root>.children[0]', '<root>.children[1]', "<root>.childrenByName.'map-key'"]
        support.structure.findAll(Child) == visited.findAll { it.value.class == Child }

        when: 'a corrupt relationship cycle is encountered'
        def loop = instance.primary.class.getDeclaredField('loop')
        loop.accessible = true
        loop.set(instance.primary, instance.primary)
        def cyclePaths = []
        support.structure.visit(Child) { path, child -> cyclePaths << path }

        then: 'each completed Object is visited once, while owners and LINKs stay outside composition'
        cyclePaths == typedPaths

        and: 'the compatibility utility preserves its established completed-model behavior through the facade'
        StructureUtil.getRelativePath(instance, instance.childrenByName['map-key']) == "childrenByName.'map-key'"
        StructureUtil.getAncestorOfType(instance.primary, instance.class).get().is(instance)
    }

    def "structure support separates direct and transitive owners"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.Owner

            @DSL class Root {
                Branch branch
            }

            @DSL class Branch {
                @Owner Root root
                Leaf leaf
            }

            @DSL class Leaf {
                @Owner Branch branch
                @Owner(transitive = true) Root root
            }
        '''

        when:
        instance = Root.Create.With {
            branch {
                leaf {}
            }
        }
        def leafSupport = KlumObjectSupport.of(instance.branch.leaf)

        then:
        leafSupport.structure.directOwners == [instance.branch] as Set
        leafSupport.structure.singleOwner.get().is(instance.branch)
        leafSupport.structure.ownerHierarchy == [instance.branch.leaf, instance.branch, instance]
        leafSupport.structure.getAncestorOfType(instance.class).get().is(instance)
    }

    private void compileJavaConsumer(String source) {
        File sourceFile = new File(tempFolder.root, 'JavaObjectSupportConsumer.java')
        sourceFile.text = source.stripIndent()
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
