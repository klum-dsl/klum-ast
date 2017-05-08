/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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

import groovy.transform.TypeChecked
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer

@SuppressWarnings("GroovyAssignabilityCheck")
class StaticTypingConfigSpec extends AbstractDSLSpec {

    def setup() {
        compilerConfiguration.addCompilationCustomizers(new ASTTransformationCustomizer(TypeChecked.class))
    }

    def "static type checking with illegal method call"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                List<System> systems
                
                int number() {
                    return systems.size()
                }
            }
            @DSL
            class System {
                String name
                List<Component> components
            }
            @DSL
            class Component {
                String name
            }
        ''')

        when:
        def script = createSecondaryClass '''
            import pk.*
            
            Config.create {
                Component.withTemplate(name: 'Kurt') {
                    systems {
                        system {
                            components {
                                component()
                            }
                        }                    
                    }
                }
            }
'''
        instance = clazz.createFrom(script)

        then:
        noExceptionThrown()
    }

}
