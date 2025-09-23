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
package com.blackbuild.groovy.configdsl.transform


import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor

class ModelPathTest extends AbstractDSLSpec {

    def "model path is set correctly"() {
        given:
        createClass '''
@DSL class Model {
    Level2 level2
}

@DSL class Level2 {
    Level3 level3
    List<Level3> level3Elements
    List<KeyedValue> keyedValues
    Map<String, KeyedValue> keyedMapValue
}

@DSL class Level3 {
    
}

@DSL class KeyedValue {
    @Key String key
}
'''
        when:
        instance = Model.Create.With {
            level2 {
                level3 {}
                level3Element()
                level3Element()
                keyedValue('first') {}
                keyedValue('second') {}
                keyedMapValue('mapFirst') {}
                keyedMapValue('map-second') {}
            }
        }

        then:
        getProxyFor(instance).modelPath == '<root>'
        getProxyFor(instance.level2).modelPath == '<root>.level2'
        getProxyFor(instance.level2.level3).modelPath == '<root>.level2.level3'
        getProxyFor(instance.level2.level3Elements[0]).modelPath == '<root>.level2.level3Elements[0]'
        getProxyFor(instance.level2.level3Elements[1]).modelPath == '<root>.level2.level3Elements[1]'
        getProxyFor(instance.level2.keyedValues[0]).modelPath == '<root>.level2.keyedValues[0]'
        getProxyFor(instance.level2.keyedValues[1]).modelPath == '<root>.level2.keyedValues[1]'
        getProxyFor(instance.level2.keyedMapValue['mapFirst']).modelPath == '<root>.level2.keyedMapValue.mapFirst'
        getProxyFor(instance.level2.keyedMapValue['map-second']).modelPath == "<root>.level2.keyedMapValue.'map-second'"
    }

    def "model path is set correctly when using nested Create.With"() {
        given:
        createClass '''
@DSL class Model {
    Level2 level2
}

@DSL class Level2 {
    Level3 level3
    List<Level3> level3Elements
    List<KeyedValue> keyedValues
    Map<String, KeyedValue> keyedMapValue
}

@DSL class Level3 {
    
}

@DSL class KeyedValue {
    @Key String key
}
'''
        // need local variables to access the classes from within delegate only closures
        def Model = getClass('Model')
        def Level3 = getClass('Level3')
        def Level2 = getClass('Level2')
        def KeyedValue = getClass('KeyedValue')

        when:
        instance = Model.Create.With {
            level2(Level2.Create.With {
                level3 ()
                level3Element()
                level3Element()
                keyedValue('first') {}
                keyedValue('second') {}
                keyedMapValue('mapFirst') {}
                keyedMapValue('map-second') {}
            })
        }

        then:
        getProxyFor(instance).modelPath == '<root>'
        getProxyFor(instance.level2).modelPath == '<root>.level2'
        getProxyFor(instance.level2.level3).modelPath == '<root>.level2.level3'
        getProxyFor(instance.level2.level3Elements[0]).modelPath == '<root>.level2.level3Elements[0]'
        getProxyFor(instance.level2.level3Elements[1]).modelPath == '<root>.level2.level3Elements[1]'
        getProxyFor(instance.level2.keyedValues[0]).modelPath == '<root>.level2.keyedValues[0]'
        getProxyFor(instance.level2.keyedValues[1]).modelPath == '<root>.level2.keyedValues[1]'
        getProxyFor(instance.level2.keyedMapValue['mapFirst']).modelPath == '<root>.level2.keyedMapValue.mapFirst'
        getProxyFor(instance.level2.keyedMapValue['map-second']).modelPath == "<root>.level2.keyedMapValue.'map-second'"
    }

    def "model path is set correctly when using nested Create.With in Converter"() {
        given:
        createClass '''import java.util.logging.Level
@DSL class Model {
    Level2 level2
}

@DSL class Level2 {
    Level3 level3
    List<Level3> level3Elements
    List<KeyedValue> keyedValues
    Map<String, KeyedValue> keyedMapValue
    
    static Level2 fromString(String ignored) {
        return Level2.Create.With {
            level3 ()
            level3Element()
            level3Element()
            keyedValue('first') {}
            keyedValue('second') {}
            keyedMapValue('mapFirst') {}
            keyedMapValue('map-second') {}
        }
    }
}

@DSL class Level3 {
    
}

@DSL class KeyedValue {
    @Key String key
}
'''
        // need local variables to access the classes from within delegate only closures
        def Model = getClass('Model')
        def Level3 = getClass('Level3')
        def Level2 = getClass('Level2')
        def KeyedValue = getClass('KeyedValue')

        when:
        instance = Model.Create.With {
            level2("bla")
        }

        then:
        getProxyFor(instance).modelPath == '<root>'
        getProxyFor(instance.level2).modelPath == '<root>.level2'
        getProxyFor(instance.level2.level3).modelPath == '<root>.level2.level3'
        getProxyFor(instance.level2.level3Elements[0]).modelPath == '<root>.level2.level3Elements[0]'
        getProxyFor(instance.level2.level3Elements[1]).modelPath == '<root>.level2.level3Elements[1]'
        getProxyFor(instance.level2.keyedValues[0]).modelPath == '<root>.level2.keyedValues[0]'
        getProxyFor(instance.level2.keyedValues[1]).modelPath == '<root>.level2.keyedValues[1]'
        getProxyFor(instance.level2.keyedMapValue['mapFirst']).modelPath == '<root>.level2.keyedMapValue.mapFirst'
        getProxyFor(instance.level2.keyedMapValue['map-second']).modelPath == "<root>.level2.keyedMapValue.'map-second'"
    }

    def "model path is set correctly when using nested Create.With in Factory"() {
        given:
        createClass '''import java.util.logging.Level
@DSL class Model {
    Level2 level2
    
    static Model fromString(String string) {
        return Model.Create.With {
            level2(string)
        }
    }
}

@DSL class Level2 {
    Level3 level3
    List<Level3> level3Elements
    List<KeyedValue> keyedValues
    Map<String, KeyedValue> keyedMapValue
    
    static Level2 fromString(String ignored) {
        return Level2.Create.With {
            level3 ()
            level3Element()
            level3Element()
            keyedValue('first') {}
            keyedValue('second') {}
            keyedMapValue('mapFirst') {}
            keyedMapValue('map-second') {}
        }
    }
}

@DSL class Level3 {
    
}

@DSL class KeyedValue {
    @Key String key
}
'''
        // need local variables to access the classes from within delegate only closures
        def Model = getClass('Model')
        def Level3 = getClass('Level3')
        def Level2 = getClass('Level2')
        def KeyedValue = getClass('KeyedValue')

        when:
        instance = Model.fromString("bla")

        then:
        getProxyFor(instance).modelPath == '<root>'
        getProxyFor(instance.level2).modelPath == '<root>.level2'
        getProxyFor(instance.level2.level3).modelPath == '<root>.level2.level3'
        getProxyFor(instance.level2.level3Elements[0]).modelPath == '<root>.level2.level3Elements[0]'
        getProxyFor(instance.level2.level3Elements[1]).modelPath == '<root>.level2.level3Elements[1]'
        getProxyFor(instance.level2.keyedValues[0]).modelPath == '<root>.level2.keyedValues[0]'
        getProxyFor(instance.level2.keyedValues[1]).modelPath == '<root>.level2.keyedValues[1]'
        getProxyFor(instance.level2.keyedMapValue['mapFirst']).modelPath == '<root>.level2.keyedMapValue.mapFirst'
        getProxyFor(instance.level2.keyedMapValue['map-second']).modelPath == "<root>.level2.keyedMapValue.'map-second'"
    }

    def "model path is set correctly when using nested AutoCreate"() {
        given:
        createClass '''import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate

import java.util.logging.Level
@DSL class Model {
    @AutoCreate Level2 level2
}

@DSL class Level2 {
    @AutoCreate Level3 level3
}

@DSL class Level3 {
    
}

'''
        when:
        instance = Model.Create.One()

        then:
        getProxyFor(instance).modelPath == '<root>'
        getProxyFor(instance.level2).modelPath == '<root>.level2'
        getProxyFor(instance.level2.level3).modelPath == '<root>.level2.level3'
    }

}