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
package com.blackbuild.klum.ast.schema

/** Fixed observable contracts for #529; independent of compiler/runtime execution during tests. */
final class CompiledInputExpectedContracts {
    private CompiledInputExpectedContracts() {}

    static Map contract(String fixture) {
        switch (fixture) {
            case 'admission': return [model: 'admission.Parent', builder: 'admission.Parent_DSL$Builder',
                    entries: [
                            [externalName: 'annotations', role: 'PROPERTY', source: 'admission.Parent#annotations',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'annotation', descriptors: ['annotation(java.lang.String, java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.Map', builder: null, keyType: 'java.lang.String', item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null],
                            [externalName: 'child', role: 'OWNED_OBJECT', source: 'admission.Parent#child',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'child', descriptors: [
                                     'child(admission.Child):admission.Child',
                                     'child(com.blackbuild.klum.ast.runtime.KlumBuilder):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'child(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'child(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'child(java.lang.Class, java.lang.String):admission.Child_DSL$Builder',
                                     'child(java.lang.Class, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'child(java.lang.String):admission.Child_DSL$Builder',
                                     'child(java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'child(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'child(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'child(java.util.Map, java.lang.Class, java.lang.String):admission.Child_DSL$Builder',
                                     'child(java.util.Map, java.lang.Class, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'child(java.util.Map, java.lang.String):admission.Child_DSL$Builder',
                                     'child(java.util.Map, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder'
                             ],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'NESTED_OBJECT', type: 'admission.Child', builder: 'admission.Child_DSL$Builder', keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'children', role: 'KEYED_OWNED_MAP', source: 'admission.Parent#children',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'children', descriptors: [
                                     'children(admission.Child, groovy.lang.Closure):void',
                                     'children(admission.Child_DSL$Builder):admission.Child_DSL$Builder',
                                     'children(admission.Child_DSL$Builder[]):void',
                                     'children(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'children(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'children(groovy.lang.Closure):void',
                                     'children(java.lang.Class, java.lang.String):admission.Child_DSL$Builder',
                                     'children(java.lang.Class, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'children(java.lang.Class[]):void',
                                     'children(java.lang.String):admission.Child_DSL$Builder',
                                     'children(java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'children(java.util.Collection):void',
                                     'children(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'children(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, java.lang.String, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'children(java.util.Map, groovy.lang.Closure):void',
                                     'children(java.util.Map, java.lang.Class, java.lang.String):admission.Child_DSL$Builder',
                                     'children(java.util.Map, java.lang.Class, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder',
                                     'children(java.util.Map, java.lang.String):admission.Child_DSL$Builder',
                                     'children(java.util.Map, java.lang.String, groovy.lang.Closure):admission.Child_DSL$Builder'
                             ],
                             nameOrigin: 'IDENTITY', mapKeyType: 'java.lang.String', excludedChildKeyMember: 'name',
                             payload: [kind: 'NESTED_OBJECT', type: 'admission.Child', builder: 'admission.Child_DSL$Builder', keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'fallback', role: 'PROPERTY', source: 'admission.Base#fallback',
                             owner: 'admission.Base_DSL$Builder',
                             operation: 'fallback', descriptors: ['fallback(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'inheritedLabel', role: 'PROPERTY', source: 'admission.Base#inheritedLabel',
                             owner: 'admission.Base_DSL$Builder',
                             operation: 'inheritedLabel', descriptors: ['inheritedLabel(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'inheritedOperation', role: 'OPERATION', source: 'admission.Base#inheritedOperation(java.lang.String):void',
                             owner: 'admission.Base_DSL$Builder',
                             operation: 'inheritedOperation', descriptors: ['inheritedOperation(java.lang.String):void'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'labels', role: 'PROPERTY', source: 'admission.Parent#labels',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'label', descriptors: ['label(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.List', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null],
                            [externalName: 'nestedAnnotations', role: 'PROPERTY', source: 'admission.Parent#nestedAnnotations',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'nestedAnnotation', descriptors: ['nestedAnnotation(java.lang.String, java.util.List):java.util.List'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.Map', builder: null, keyType: 'java.lang.String', item: [kind: 'LIST', type: 'java.util.List', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]]], fixedChildKey: null],
                            [externalName: 'promote', role: 'OPERATION', source: 'admission.Parent#promote(java.lang.String):void',
                             owner: 'admission.Parent_DSL$Builder',
                             operation: 'promote', descriptors: ['promote(java.lang.String):void'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'renamed', role: 'PROPERTY', source: 'admission.Base#region',
                             owner: 'admission.Base_DSL$Builder',
                             operation: 'region', descriptors: ['region(admission.Region):admission.Region'],
                             nameOrigin: 'JSON_PROPERTY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'ENUM', type: 'admission.Region', builder: null, keyType: null, item: null], fixedChildKey: null]
                    ], rejections: [
                            [source: 'admission.Base#ignoredRename', reason: 'excluded by @JsonIgnore(true)'],
                            [source: 'admission.Base#implicitSecret', reason: 'excluded by @JsonIgnore(true)'],
                            [source: 'admission.Base#secret', reason: 'excluded by @JsonIgnore(true)']
                    ]]
            case 'collectioncontracts-EnumSet': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(collectioncontracts.Mode):collectioncontracts.Mode'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.EnumSet', builder: null, keyType: null, item: [kind: 'ENUM', type: 'collectioncontracts.Mode', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-List': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.List', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-Map': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String, java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.Map', builder: null, keyType: 'java.lang.String', item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-NavigableMap': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String, java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.NavigableMap', builder: null, keyType: 'java.lang.String', item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-NavigableSet': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-Set': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.Set', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-SortedMap': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String, java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.SortedMap', builder: null, keyType: 'java.lang.String', item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'collectioncontracts-SortedSet': return [model: 'collectioncontracts.Configuration', builder: 'collectioncontracts.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'collectioncontracts.Configuration#values',
                             owner: 'collectioncontracts.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.SortedSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [

                    ]]
            case 'fixedschema': return [model: 'fixedschema.Configuration', builder: 'fixedschema.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'entries', role: 'KEYED_OWNED_MAP', source: 'fixedschema.Configuration#entries',
                             owner: 'fixedschema.Configuration_DSL$Builder',
                             operation: 'entries', descriptors: [
                                     'entries(fixedschema.Entry, groovy.lang.Closure):void',
                                     'entries(fixedschema.Entry_DSL$Builder[]):void',
                                     'entries(groovy.lang.Closure):void',
                                     'entries(java.lang.Class[]):void',
                                     'entries(java.util.Collection):void',
                                     'entries(java.util.Map, groovy.lang.Closure):void'
                             ],
                             nameOrigin: 'IDENTITY', mapKeyType: 'java.lang.String', excludedChildKeyMember: 'name',
                             payload: [kind: 'NESTED_OBJECT', type: 'fixedschema.Entry', builder: 'fixedschema.Entry_DSL$Builder', keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'main-slot', role: 'OWNED_OBJECT', source: 'fixedschema.Configuration#primary',
                             owner: 'fixedschema.Configuration_DSL$Builder',
                             operation: 'primary', descriptors: [
                                     'primary():fixedschema.Entry_DSL$Builder',
                                     'primary(com.blackbuild.klum.ast.runtime.KlumBuilder):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'primary(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'primary(com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'primary(fixedschema.Entry):fixedschema.Entry',
                                     'primary(groovy.lang.Closure):fixedschema.Entry_DSL$Builder',
                                     'primary(java.lang.Class):fixedschema.Entry_DSL$Builder',
                                     'primary(java.lang.Class, groovy.lang.Closure):fixedschema.Entry_DSL$Builder',
                                     'primary(java.util.Map):fixedschema.Entry_DSL$Builder',
                                     'primary(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'primary(java.util.Map, com.blackbuild.klum.ast.runtime.KlumFactory$BuilderFactoryProvider, groovy.lang.Closure):com.blackbuild.klum.ast.runtime.KlumBuilder',
                                     'primary(java.util.Map, groovy.lang.Closure):fixedschema.Entry_DSL$Builder',
                                     'primary(java.util.Map, java.lang.Class):fixedschema.Entry_DSL$Builder',
                                     'primary(java.util.Map, java.lang.Class, groovy.lang.Closure):fixedschema.Entry_DSL$Builder'
                             ],
                             nameOrigin: 'JSON_PROPERTY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'NESTED_OBJECT', type: 'fixedschema.Entry', builder: 'fixedschema.Entry_DSL$Builder', keyType: null, item: null], fixedChildKey: 'primary']
                    ], rejections: [
                            [source: 'fixedschema.Configuration#derived', reason: 'literal structured instance keys conflict with @Field.keyMapping-derived keys'],
                            [source: 'fixedschema.Configuration#linked', reason: 'requires one concrete owned relationship']
                    ]]
            case 'invisiblefields': return [model: 'invisiblefields.Configuration', builder: 'invisiblefields.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'invisiblefields.Configuration#budget', reason: 'unsupported numeric type java.lang.Number; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'invisiblefields.Configuration#ignored', reason: 'excluded by @JsonIgnore(true)'],
                            [source: 'invisiblefields.Configuration#marker', reason: 'has no admitted scalar, enum, List, or Map payload'],
                            [source: 'invisiblefields.Configuration#reserved', reason: 'field is absent from the final public Builder contract'],
                            [source: 'invisiblefields.Configuration#values', reason: 'has no unambiguous compiler-generated element population method value(java.lang.Integer); replacement setters and authored overrides are not input paths']
                    ]]
            case 'numericboundary': return [model: 'numericboundary.Configuration', builder: 'numericboundary.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'numericboundary.Configuration#allocate(numericboundary.OpaqueNumber):void', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budget', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budgets', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#limits', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input']
                    ]]
            case 'numericboundary-consume-List': return [model: 'numericboundary.Configuration', builder: 'numericboundary.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'numericboundary.Configuration#budget', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budgets', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#consume(java.util.List):void', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#limits', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input']
                    ]]
            case 'numericboundary-consume-Map': return [model: 'numericboundary.Configuration', builder: 'numericboundary.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'numericboundary.Configuration#budget', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budgets', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#consume(java.util.Map):void', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#limits', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input']
                    ]]
            case 'numericboundary-consume-Map-container': return [model: 'numericboundary.Configuration', builder: 'numericboundary.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'numericboundary.Configuration#budget', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budgets', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#consume(java.util.Map):void', reason: 'unsupported whole-container payload target java.util.Set; use ordinary List/Map payloads or a supported field\'s generated element population'],
                            [source: 'numericboundary.Configuration#limits', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input']
                    ]]
            case 'numericboundary-consume-Set': return [model: 'numericboundary.Configuration', builder: 'numericboundary.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'numericboundary.Configuration#budget', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#budgets', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#consume(java.util.Set):void', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input'],
                            [source: 'numericboundary.Configuration#limits', reason: 'unsupported numeric type numericboundary.OpaqueNumber; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input']
                    ]]
            case 'rejectedparent': return [model: 'rejectedparent.Configuration', builder: 'rejectedparent.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'shared', role: 'PROPERTY', source: 'rejectedparent.Configuration#available',
                             owner: 'rejectedparent.Configuration_DSL$Builder',
                             operation: 'available', descriptors: ['available(java.lang.String):java.lang.String'],
                             nameOrigin: 'JSON_PROPERTY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null]
                    ], rejections: [
                            [source: 'rejectedparent.Base#first', reason: 'external-name collision for \'shared\': rejectedparent.Base#first, rejectedparent.Base#second'],
                            [source: 'rejectedparent.Base#second', reason: 'external-name collision for \'shared\': rejectedparent.Base#first, rejectedparent.Base#second']
                    ]]
            case 'rejection': return [model: 'rejection.Parent', builder: 'rejection.Parent_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'rejection.Parent#abstractChild', reason: 'requires one concrete owned relationship'],
                            [source: 'rejection.Parent#first', reason: 'external-name collision for \'duplicate\': rejection.Parent#first, rejection.Parent#second'],
                            [source: 'rejection.Parent#firstOperation(java.lang.String):void', reason: 'external-name collision for \'operation\': rejection.Parent#firstOperation(java.lang.String):void, rejection.Parent#secondOperation(java.lang.String):void'],
                            [source: 'rejection.Parent#marker', reason: 'has no admitted scalar, enum, List, or Map payload'],
                            [source: 'rejection.Parent#numberedChildren', reason: 'requires Map<String, concrete keyed DSL Object> ownership'],
                            [source: 'rejection.Parent#raw(java.util.Map):void', reason: '@Builder.Method payload is outside the admitted grammar'],
                            [source: 'rejection.Parent#replace(java.lang.String, java.lang.String):void', reason: '@Builder.Method must declare exactly one payload'],
                            [source: 'rejection.Parent#reset():void', reason: '@Builder.Method must declare exactly one payload'],
                            [source: 'rejection.Parent#second', reason: 'external-name collision for \'duplicate\': rejection.Parent#first, rejection.Parent#second'],
                            [source: 'rejection.Parent#secondOperation(java.lang.String):void', reason: 'external-name collision for \'operation\': rejection.Parent#firstOperation(java.lang.String):void, rejection.Parent#secondOperation(java.lang.String):void'],
                            [source: 'rejection.Parent#unkeyedChildren', reason: 'has no admitted scalar, enum, List, or Map payload']
                    ]]
            case 'restrictedarguments-ArrayList': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.ArrayList):void', reason: 'unsupported whole-container payload target java.util.ArrayList; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-Collection': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.Collection):void', reason: 'unsupported whole-container payload target java.util.Collection; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-EnumSet': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.EnumSet):void', reason: 'unsupported whole-container payload target java.util.EnumSet; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-LinkedHashMap': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.LinkedHashMap):void', reason: 'unsupported whole-container payload target java.util.LinkedHashMap; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-List': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.List):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'restrictedarguments-Map': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.Map):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'restrictedarguments-Map-nested-set': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.Map):void', reason: 'unsupported whole-container payload target java.util.Set; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-NavigableSet': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.NavigableSet):void', reason: 'unsupported whole-container payload target java.util.NavigableSet; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-Object': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.lang.Object):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'restrictedarguments-Set': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.Set):void', reason: 'unsupported whole-container payload target java.util.Set; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-SortedSet': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.SortedSet):void', reason: 'unsupported whole-container payload target java.util.SortedSet; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-String-array': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume([Ljava.lang.String;):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'restrictedarguments-TreeMap': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume(java.util.TreeMap):void', reason: 'unsupported whole-container payload target java.util.TreeMap; use ordinary List/Map payloads or a supported field\'s generated element population']
                    ]]
            case 'restrictedarguments-int-array': return [model: 'restrictedarguments.Configuration', builder: 'restrictedarguments.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'fieldValues', role: 'PROPERTY', source: 'restrictedarguments.Configuration#fieldValues',
                             owner: 'restrictedarguments.Configuration_DSL$Builder',
                             operation: 'fieldValue', descriptors: ['fieldValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'restrictedarguments.Configuration#consume([I):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'safeannotations': return [model: 'safeannotations.Configuration', builder: 'safeannotations.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'computed', role: 'PROPERTY', source: 'safeannotations.Configuration#computed',
                             owner: 'safeannotations.Configuration_DSL$Builder',
                             operation: 'computed', descriptors: ['computed(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null],
                            [externalName: 'configure-input', role: 'OPERATION', source: 'safeannotations.Configuration#configure(java.util.Map):void',
                             owner: 'safeannotations.Configuration_DSL$Builder',
                             operation: 'configure', descriptors: ['configure(java.util.Map):void'],
                             nameOrigin: 'JSON_PROPERTY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'MAP', type: 'java.util.Map', builder: null, keyType: 'java.lang.String', item: [kind: 'LIST', type: 'java.util.List', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]]], fixedChildKey: null],
                            [externalName: 'initialized', role: 'PROPERTY', source: 'safeannotations.Configuration#initialized',
                             owner: 'safeannotations.Configuration_DSL$Builder',
                             operation: 'initialized', descriptors: ['initialized(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null], fixedChildKey: null]
                    ], rejections: [
                            [source: 'safeannotations.Configuration#hidden(java.lang.String):void', reason: 'excluded by @JsonIgnore(true)']
                    ]]
            case 'shadowedpopulation': return [model: 'shadowedpopulation.Configuration', builder: 'shadowedpopulation.Configuration_DSL$Builder',
                    entries: [
                            [externalName: 'values', role: 'PROPERTY', source: 'shadowedpopulation.Configuration#values',
                             owner: 'shadowedpopulation.Configuration_DSL$Builder',
                             operation: 'addValue', descriptors: ['addValue(java.lang.String):java.lang.String'],
                             nameOrigin: 'IDENTITY', mapKeyType: null, excludedChildKeyMember: null,
                             payload: [kind: 'LIST', type: 'java.util.NavigableSet', builder: null, keyType: null, item: [kind: 'SCALAR', type: 'java.lang.String', builder: null, keyType: null, item: null]], fixedChildKey: null]
                    ], rejections: [
                            [source: 'shadowedpopulation.Configuration#values(java.lang.Iterable):void', reason: '@Builder.Method payload is outside the admitted grammar']
                    ]]
            case 'unresolvedinput': return [model: 'unresolvedinput.Configuration', builder: 'unresolvedinput.Configuration_DSL$Builder',
                    entries: [

                    ], rejections: [
                            [source: 'unresolvedinput.Base#consume(java.lang.Object):void', reason: '@Builder.Method payload is outside the admitted grammar'],
                            [source: 'unresolvedinput.Base#value', reason: 'has no admitted scalar, enum, List, or Map payload']
                    ]]
            default: throw new IllegalArgumentException('Unknown expected contract: ' + fixture)
        }
    }
}
