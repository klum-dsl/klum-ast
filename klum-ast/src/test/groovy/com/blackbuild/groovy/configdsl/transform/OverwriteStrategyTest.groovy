/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.CopyHandler
import spock.lang.Issue
import spock.lang.Unroll

@Issue("309")
@Unroll
class OverwriteStrategyTest extends AbstractDSLSpec {

    static final String REPLACE = "REPLACE"
    public static final String ALWAYS_REPLACE = "ALWAYS_REPLACE"
    public static final String SET_IF_NULL = "SET_IF_NULL"
    public static final String MERGE = "MERGE"
    public static final String ADD = "ADD"
    public static final String FULL_REPLACE = "FULL_REPLACE"
    public static final String MERGE_KEYS = "MERGE_KEYS"
    public static final String MERGE_VALUES = "MERGE_VALUES"
    public static final String ADD_MISSING = "ADD_MISSING"
    public static final String SET_IF_EMPTY = "SET_IF_EMPTY"


    def "copy single pojo #strategy"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Foo {
                @Overwrite.Single(OverwriteStrategy.Single.$strategy)
                String bar 
            }
        """

        when:
        def target = Foo.Create.With(bar: targetBar)
        def source = Foo.Create.With(bar: sourceBar)
        CopyHandler.copyToFrom(target, source)

        then:
        target.bar == result

        where:
        strategy       | targetBar | sourceBar || result
        REPLACE        | "target"  | "source"  || "source"
        REPLACE        | null      | "source"  || "source"
        REPLACE        | "target"  | null      || "target"
        ALWAYS_REPLACE | "target"  | "source"  || "source"
        ALWAYS_REPLACE | null      | "source"  || "source"
        ALWAYS_REPLACE | "target"  | null      || null
        SET_IF_NULL    | "target"  | "source"  || "target"
        SET_IF_NULL    | null      | "source"  || "source"
        SET_IF_NULL    | "target"  | null      || "target"
    }

    def "copy single pojo #strategy on class"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @Overwrite(singles = @Overwrite.Single(OverwriteStrategy.Single.$strategy))
            @DSL class Foo {
                String bar 
            }
        """

        when:
        def target = Foo.Create.With(bar: targetBar)
        def source = Foo.Create.With(bar: sourceBar)
        CopyHandler.copyToFrom(target, source)

        then:
        target.bar == result

        where:
        strategy       | targetBar | sourceBar || result
        REPLACE        | "target"  | "source"  || "source"
        REPLACE        | null      | "source"  || "source"
        REPLACE        | "target"  | null      || "target"
        ALWAYS_REPLACE | "target"  | "source"  || "source"
        ALWAYS_REPLACE | null      | "source"  || "source"
        ALWAYS_REPLACE | "target"  | null      || null
        SET_IF_NULL    | "target"  | "source"  || "target"
        SET_IF_NULL    | null      | "source"  || "source"
        SET_IF_NULL    | "target"  | null      || "target"
    }

    def "copy single DSL #strategy"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Inner {
                String foo
                String bar 
            }

            @DSL class Outer {
                @Overwrite.Single(OverwriteStrategy.Single.$strategy)
                Inner inner 
            }
        """

        when:
        def target = Outer.Create.With {
            if (targetMap != null) {
                inner(targetMap)
            }
        }
        def source = Outer.Create.With {
            if (sourceMap != null) {
                inner(sourceMap)
            }
        }
        CopyHandler.copyToFrom(target, source)

        then:
        !target.inner?.is(source.inner)
        (resultMap == null && target.inner == null) || target.inner.foo == resultMap.foo && target.inner.bar == resultMap.bar

        where:
        strategy       | targetMap              | sourceMap              || resultMap
        REPLACE        | [foo: "t1", bar: "t2"] | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        REPLACE        | [foo: "t1", bar: "t2"] | [foo: null, bar: "s2"] || [foo: null, bar: "s2"]
        REPLACE        | [bar: "t2"]            | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        REPLACE        | null                   | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        REPLACE        | [foo: "t1", bar: "t2"] | null                   || [foo: "t1", bar: "t2"]
        ALWAYS_REPLACE | [foo: "t1", bar: "t2"] | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        ALWAYS_REPLACE | [foo: "t1", bar: "t2"] | [bar: "s2"]            || [bar: "s2"]
        ALWAYS_REPLACE | [bar: "t2"]            | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        ALWAYS_REPLACE | null                   | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        ALWAYS_REPLACE | [foo: "t1", bar: "t2"] | null                   || null
        SET_IF_NULL    | [foo: "t1", bar: "t2"] | [foo: "s1", bar: "s2"] || [foo: "t1", bar: "t2"]
        SET_IF_NULL    | [foo: "t1", bar: "t2"] | [bar: "s2"]            || [foo: "t1", bar: "t2"]
        SET_IF_NULL    | [bar: "t2"]            | [foo: "s1", bar: "s2"] || [bar: "t2"]
        SET_IF_NULL    | null                   | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        SET_IF_NULL    | [foo: "t1", bar: "t2"] | null                   || [foo: "t1", bar: "t2"]
        MERGE          | [foo: "t1", bar: "t2"] | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        MERGE          | [foo: "t1", bar: "t2"] | [bar: "s2"]            || [foo: "t1", bar: "s2"]
        MERGE          | [bar: "t2"]            | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        MERGE          | null                   | [foo: "s1", bar: "s2"] || [foo: "s1", bar: "s2"]
        MERGE          | [foo: "t1", bar: "t2"] | null                   || [foo: "t1", bar: "t2"]
    }

    def "copy collection #strategy"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Foo {
                @Overwrite.Collection(OverwriteStrategy.Collection.$strategy)
                List<Integer> bars
            }
        """

        when:
        def target = Foo.Create.With(bars: targetBars)
        def source = Foo.Create.With {
            // need to explicitly set null to overwrite the default empty collection
            bars = sourceBars
        }
        CopyHandler.copyToFrom(target, source)

        then:
        !target.bars.is(source.bars)
        target.bars == resultBars

        where:
        strategy       | targetBars | sourceBars || resultBars
        REPLACE        | [1, 2]     | [3, 4]     || [3, 4]
        REPLACE        | [1, 2]     | []         || [1, 2]
        REPLACE        | [1, 2]     | null       || [1, 2]
        REPLACE        | []         | [3, 4]     || [3, 4]
        ALWAYS_REPLACE | [1, 2]     | [3, 4]     || [3, 4]
        ALWAYS_REPLACE | [1, 2]     | []         || []
        ALWAYS_REPLACE | [1, 2]     | null       || [1, 2]
        ALWAYS_REPLACE | []         | [3, 4]     || [3, 4]
        SET_IF_EMPTY   | [1, 2]     | [3, 4]     || [1, 2]
        SET_IF_EMPTY   | [1, 2]     | []         || [1, 2]
        SET_IF_EMPTY   | [1, 2]     | null       || [1, 2]
        SET_IF_EMPTY   | []         | [3, 4]     || [3, 4]
        ADD            | [1, 2]     | [3, 4]     || [1, 2, 3, 4]
        ADD            | [1, 2]     | []         || [1, 2]
        ADD            | [1, 2]     | null       || [1, 2]
        ADD            | []         | [3, 4]     || [3, 4]
    }

    def "copy map #strategy"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Foo {
                @Overwrite.Map(OverwriteStrategy.Map.$strategy)
                Map<String, Integer> bars
            }
        """

        when:
        def target = Foo.Create.With(bars: targetBars)
        def source = Foo.Create.With {
            bars = sourceBars
        }
        CopyHandler.copyToFrom(target, source)

        then:
        !target.bars.is(source.bars)
        target.bars == resultBars

        where:
        strategy       | targetBars   | sourceBars   || resultBars
        FULL_REPLACE   | [a: 1, b: 2] | [b: 3, c: 4] || [b: 3, c: 4]
        FULL_REPLACE   | [a: 1, b: 2] | [:]          || [a: 1, b: 2]
        FULL_REPLACE   | [a: 1, b: 2] | null         || [a: 1, b: 2]
        FULL_REPLACE   | [:]          | [b: 3, c: 4] || [b: 3, c: 4]
        ALWAYS_REPLACE | [a: 1, b: 2] | [b: 3, c: 4] || [b: 3, c: 4]
        ALWAYS_REPLACE | [a: 1, b: 2] | [:]          || [:]
        ALWAYS_REPLACE | [a: 1, b: 2] | null         || [a: 1, b: 2]
        ALWAYS_REPLACE | [:]          | [b: 3, c: 4] || [b: 3, c: 4]
        SET_IF_EMPTY   | [a: 1, b: 2] | [b: 3, c: 4] || [a: 1, b: 2]
        SET_IF_EMPTY   | [a: 1, b: 2] | [:]          || [a: 1, b: 2]
        SET_IF_EMPTY   | [a: 1, b: 2] | null         || [a: 1, b: 2]
        SET_IF_EMPTY   | [:]          | [b: 3, c: 4] || [b: 3, c: 4]
        MERGE_KEYS     | [a: 1, b: 2] | [b: 3, c: 4] || [a: 1, b: 3, c: 4]
        MERGE_KEYS     | [a: 1, b: 2] | [:]          || [a: 1, b: 2]
        MERGE_KEYS     | [a: 1, b: 2] | null         || [a: 1, b: 2]
        MERGE_KEYS     | [:]          | [b: 3, c: 4] || [b: 3, c: 4]
        ADD_MISSING    | [a: 1, b: 2] | [b: 3, c: 4] || [a: 1, b: 2, c: 4]
        ADD_MISSING    | [a: 1, b: 2] | [:]          || [a: 1, b: 2]
        ADD_MISSING    | [a: 1, b: 2] | null         || [a: 1, b: 2]
        ADD_MISSING    | [:]          | [b: 3, c: 4] || [b: 3, c: 4]
    }

    def "copy DSL map #strategy"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Inner {
                @Key String key
                Integer foo
                Integer bar 
            }

            @DSL class Outer {
                @Overwrite.Map(OverwriteStrategy.Map.$strategy)
                Map<String, Inner> inners 
            }
        """

        when:
        def target = Outer.Create.With {
            targetMap.each { key, value ->
                inner(foo: value.foo, bar: value.bar, key)
            }
        }
        def source = Outer.Create.With {
            sourceMap.each { key, value ->
                inner(foo: value.foo, bar: value.bar, key)
            }
        }
        CopyHandler.copyToFrom(target, source)

        then:
        !target.inners?.is(source.inners)
        target.inners.keySet() == resultMap.keySet()
        target.inners.every { key, value ->
            value.foo == resultMap[key].foo && value.bar == resultMap[key].bar
        }


        //(resultMap == null && target.inner == null) || target.inner.foo == resultMap.foo && target.inner.bar == resultMap.bar

        where:
        strategy       | targetMap                                  | sourceMap                                  || resultMap
        FULL_REPLACE   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        FULL_REPLACE   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [d: [foo: 4, bar: 5]]                      || [d: [foo: 4, bar: 5]]
        FULL_REPLACE   | [b: [foo: 1, bar: 2]]                      | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        FULL_REPLACE   | [:]                                        | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        FULL_REPLACE   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [:]                                        || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
        ALWAYS_REPLACE | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        ALWAYS_REPLACE | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [d: [foo: 4, bar: 5]]                      || [d: [foo: 4, bar: 5]]
        ALWAYS_REPLACE | [b: [foo: 1, bar: 2]]                      | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        ALWAYS_REPLACE | [:]                                        | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        ALWAYS_REPLACE | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [:]                                        || [:]
        SET_IF_EMPTY   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
        SET_IF_EMPTY   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [d: [foo: 4, bar: 5]]                      || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
        SET_IF_EMPTY   | [b: [foo: 1, bar: 2]]                      | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 1, bar: 2]]
        SET_IF_EMPTY   | [:]                                        | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        SET_IF_EMPTY   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [:]                                        || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
        MERGE_KEYS     | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [a: [foo: 1, bar: 2], b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_KEYS     | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [d: [foo: 4, bar: 5]]                      || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2], d: [foo: 4, bar: 5]]
        MERGE_KEYS     | [b: [foo: 1, bar: 2]]                      | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_KEYS     | [:]                                        | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_KEYS     | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [:]                                        || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
        MERGE_VALUES   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [a: [foo: 1, bar: 2], b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_VALUES   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [b: [bar: 4], d: [foo: 4, bar: 5]]         || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_VALUES   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [d: [foo: 4, bar: 5]]                      || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2], d: [foo: 4, bar: 5]]
        MERGE_VALUES   | [b: [foo: 1, bar: 2]]                      | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_VALUES   | [:]                                        | [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]] || [b: [foo: 3, bar: 4], d: [foo: 4, bar: 5]]
        MERGE_VALUES   | [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]] | [:]                                        || [a: [foo: 1, bar: 2], b: [foo: 1, bar: 2]]
    }

    @Issue("325")
    def "dsl single adder should merge with existing objects"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Inner {
                Integer foo
                Integer bar 
            }

            @DSL class Outer {
                Inner inner 
            }
        """

        when:
        def template = Outer.Create.Template {
            inner {
                foo 1
            }
        }

        Outer.withTemplate(template) {
            instance = Outer.Create.With {
                inner {
                    bar 2
                }
            }
        }

        then:
        instance.inner.foo == 1
        instance.inner.bar == 2
    }

    @Issue("325")
    def "dsl single adder should use merge even if another strategy is configured"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Inner {
                Integer foo
                Integer bar 
            }

            @DSL class Outer {
                @Overwrite.Single(OverwriteStrategy.Single.SET_IF_NULL)
                Inner inner 
            }
        """

        when:
        def template = Outer.Create.Template {
            inner {
                foo 1
            }
        }

        Outer.withTemplate(template) {
            instance = Outer.Create.With {
                inner {
                    bar 2
                }
            }
        }

        then:
        instance.inner.foo == 1
        instance.inner.bar == 2
    }

    @Issue("325")

    def "dsl map adder should merge with existing objects"() {
        given:
        createClass """
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy
            
            @DSL class Inner {
                @Key String key
                Integer foo
                Integer bar 
            }

            @DSL class Outer {
                Map<String, Inner> inners 
            }
        """

        when:
        def template = Outer.Create.Template {
            inner("a") {
                foo 1
            }
        }

        def innerInstance
        Outer.withTemplate(template) {
            instance = Outer.Create.With {
                inner("a") {
                    bar 2
                }
            }
        }

        then:
        instance.inners.a.foo == 1
        instance.inners.a.bar == 2
    }

}