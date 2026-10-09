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
package fixture.probe
import fixture.annotation.Binding
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship
import java.util.Optional

@CompileStatic
class StaticReader {
    static int reads
    static <T> void live(KlumBuilder<T> builder) {
        KlumBuilderSupport<T> support = KlumBuilderSupport.of(builder)
        KlumBuilderSupport.Structure<T> structure = support.structure
        Optional<KlumSchemaRelationship> relationship = structure.owningRelationship
        Optional<Binding> binding = structure.getOwningRelationshipAnnotation(Binding)
        assert binding.orElseThrow().value() == 'inherited'
        assert relationship.orElseThrow().getAnnotation(Binding) == binding
        assert !structure.getOwningRelationshipAnnotation(Deprecated).present
        reads++
    }
    static <T> void completed(T model) {
        KlumObjectSupport<T> support = KlumObjectSupport.of(model)
        KlumObjectSupport.Structure<T> structure = support.structure
        Optional<KlumSchemaRelationship> relationship = structure.owningRelationship
        Optional<Binding> binding = structure.getOwningRelationshipAnnotation(Binding)
        assert relationship.orElseThrow().getAnnotation(Binding) == binding
        assert binding.orElseThrow().value() == 'inherited'
        assert !structure.getOwningRelationshipAnnotation(Deprecated).present
    }
}

class DynamicReader {
    static int reads
    static void live(builder) {
        def structure = KlumBuilderSupport.of(builder).structure
        def binding = structure.getOwningRelationshipAnnotation(Binding)
        assert structure.owningRelationship.orElseThrow().getAnnotation(Binding) == binding
        assert binding.orElseThrow().value() == 'inherited'
        assert !structure.getOwningRelationshipAnnotation(Deprecated).present
        reads++
    }
    static void completed(model) {
        def structure = KlumObjectSupport.of(model).structure
        assert structure.getOwningRelationshipAnnotation(Binding).orElseThrow().value() == 'inherited'
        assert structure.owningRelationship.orElseThrow().name == 'child'
        assert !structure.getOwningRelationshipAnnotation(Deprecated).present
    }
}
