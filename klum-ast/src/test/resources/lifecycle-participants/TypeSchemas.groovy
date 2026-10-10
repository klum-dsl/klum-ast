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
package participant.fixture
import com.blackbuild.klum.ast.DSL
import participant.fixture.TypeRules.Managed
import participant.fixture.TypeRules.Other
import participant.fixture.TypeRules.Local

@Managed('base') @Other @Local @DSL class BaseSchema extends TypeDomain {}
@DSL class InheritedSchema extends BaseSchema {}
@Managed('override') @DSL class OverrideSchema extends BaseSchema {}
@Managed('one') @Managed('two') @DSL class RepeatedSchema extends BaseSchema {}
@Managed('one') @Managed('two') @DSL class OnlyRepeatedSchema extends TypeDomain {}
@DSL class InheritedContainerSchema extends OnlyRepeatedSchema {}
@Managed('three') @Managed('four') @DSL class OverrideContainerSchema extends OnlyRepeatedSchema {}
@Managed('interface') interface MarkerInterface {}
@DSL class InterfaceSchema extends TypeDomain implements MarkerInterface {}
@Managed('root') @DSL class RootSchema extends TypeDomain { @Managed('incoming') BaseSchema child }
