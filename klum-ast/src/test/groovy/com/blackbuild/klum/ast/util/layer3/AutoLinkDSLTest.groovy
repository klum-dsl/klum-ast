/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.blackbuild.klum.ast.util.KlumInstanceProxy
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo
import spock.lang.Issue

// is in klum-ast, because the tests are a lot better readable using the actual DSL.
@SuppressWarnings('GrPackage')
class AutoLinkDSLTest extends AbstractDSLSpec {

    def "auto link default name and provider"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Map<String, Service> services
                User user 
                User admin
            }

            @DSL
            class Service {
                @Key String name
                @Owner Container container
                @LinkTo User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            service('s1')
            service('s2')
            service('s3') {
                user('serviceUser')
            }
            user('containerUser', password: "secret")
        }

        then:
        instance.services.s1.user.is(instance.user)
        instance.services.s2.user.is(instance.user)
        !instance.services.s3.user.is(instance.user)
        instance.services.s3.user.name == 'serviceUser'
    }

    def "auto link with explicit field name and default provider"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Map<String, Service> services
                User user 
                User admin
            }

            @DSL class Service {
                @Key String name
                @Owner Container container
                @LinkTo(field = "user") User aUser
            }
        ''')

        when:
        instance = create("tmp.Container") {
            service('s1')
            service('s2')
            service('s3') {
                aUser('serviceUser')
            }
            user('containerUser', password: "secret")
        }

        then:
        instance.services.s1.aUser.is(instance.user)
        instance.services.s2.aUser.is(instance.user)
        !instance.services.s3.aUser.is(instance.user)
        instance.services.s3.aUser.name == 'serviceUser'
    }

    def "auto link with fieldId and default provider"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.util.layer3.annotations.LinkSource
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Map<String, Service> services
                @LinkSource("custom") User user 
                User admin
            }

            @DSL class Service {
                @Key String name
                @Owner Container container
                @LinkTo(fieldId = "custom") User aUser
            }
        ''')

        when:
        instance = create("tmp.Container") {
            service('s1')
            service('s2')
            service('s3') {
                aUser('serviceUser')
            }
            user('containerUser', password: "secret")
        }

        then:
        instance.services.s1.aUser.is(instance.user)
        instance.services.s2.aUser.is(instance.user)
        !instance.services.s3.aUser.is(instance.user)
        instance.services.s3.aUser.name == 'serviceUser'
    }

    def "auto link with no default name, single field and default provider"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Map<String, Service> services
                User user 
            }

            @DSL class Service {
                @Key String name
                @Owner Container container
                @LinkTo User access
            }
        ''')

        when:
        instance = create("tmp.Container") {
            service('s1')
            service('s2')
            service('s3') {
                access('serviceUser')
            }
            user('containerUser', password: "secret")
        }

        then:
        instance.services.s1.access.is(instance.user)
        instance.services.s2.access.is(instance.user)
        !instance.services.s3.access.is(instance.user)
        instance.services.s3.access.name == 'serviceUser'
    }

    def "auto link default name and provider closure"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                User admin
                User user
            }
            
            @DSL class Consumer extends Service {
                @LinkTo(provider = {container.producer}) User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                admin('adminUser')
                user('producerUser')
            }
        }

        then:
        instance.consumer.user.is(instance.producer.user)

    }

    @Issue("316")
    def "auto link default name and provider closure pointing to map"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                Map<String, User> users
            }
            
            @DSL class Consumer extends Service {
                @LinkTo(provider = {container.producer.users}) User accessUser
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                user('admin')
                user('accessUser')
            }
        }

        then:
        instance.consumer.accessUser.is(instance.producer.users.accessUser)
    }

    def "auto link implicit instance name strategy and provider closure"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                User admin
                User consumer
            }
            
            @DSL class Consumer extends Service {
                @LinkTo(provider = {container.producer}) User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                admin('adminUser')
                consumer('producerUser')
            }
        }

        then:
        instance.consumer.user.is(instance.producer.consumer)
    }

    def "auto link explicit instance name strategy and provider closure"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                User user
                User consumer
            }
            
            @LinkTo(provider = {container.producer}, strategy = LinkTo.Strategy.INSTANCE_NAME)
            @DSL class Consumer extends Service {
                @LinkTo User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                user('adminUser')
                consumer('producerUser')
            }
        }

        then:
        instance.consumer.user.is(instance.producer.consumer)
    }

    def "auto link explicit instance name strategy and provider closure on class"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                User user
                User consumer
            }
            
            @DSL class Consumer extends Service {
                @LinkTo(provider = {container.producer}, strategy = LinkTo.Strategy.INSTANCE_NAME) User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                user('adminUser')
                consumer('producerUser')
            }
        }

        then:
        instance.consumer.user.is(instance.producer.consumer)
    }

    def "auto link implicit instance name strategy, nameSuffix and provider closure"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkSource
            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Container {
                Producer producer
                Consumer consumer
            }

            @DSL
            abstract class Service {
                @Owner Container container
            }
            
            @DSL class Producer extends Service {
                @LinkSource("somethingElse") User adminUser
                User consumerUser
            }
            
            @DSL class Consumer extends Service {
                @LinkTo(provider = {container.producer}, nameSuffix = "User") User user
            }
        ''')

        when:
        instance = create("tmp.Container") {
            consumer()
            producer() {
                adminUser('adminUser')
                consumerUser('producerUser')
            }
        }

        then:
        instance.consumer.user.is(instance.producer.consumerUser)
    }

    def "auto link with provider type"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner
import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Environment {
                Container container
                User adminUser
                User user
            }

            @DSL class Container {
                @Owner Environment environment
                Service service
            }

            @DSL
            class Service {
                @Owner Container container
                @LinkTo(providerType = Environment) User user
            }
        ''')

        when:
        instance = create("tmp.Environment") {
            adminUser('admin')
            user("defaultUser")
            container() {
                service()
            }
        }

        then:
        instance.container.service.user.is(instance.user)
    }

    def "determine provider scenarios"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            import com.blackbuild.groovy.configdsl.transform.Owner

            @DSL class User {
                @Key String name
                String password
            }

            @DSL class Environment {
                Container container
                User adminUser
                User user
            }

            @DSL class Container {
                @Owner Environment environment
                Service service
                Service service2
            }

            @DSL
            class Service {
                @Owner Container container
                User user
            }
        ''')

        instance = create("tmp.Environment") {
            adminUser('admin')
            user("defaultUser")
            container() {
                service()
                service2()
            }
        }
        def linkTo

        when:
        linkTo = withDefaults(GroovyStub(LinkTo) {
            providerType() >> getClass("tmp.Environment")
        })

        then:
        LinkHelper.determineProviderObject(KlumInstanceProxy.getProxyFor(instance), linkTo) == instance

        when:
        linkTo = withDefaults(GroovyStub(LinkTo) {
            provider() >> { container.service2 }.getClass()
        })

        then:
        LinkHelper.determineProviderObject(KlumInstanceProxy.getProxyFor(instance), linkTo) == instance.container.service2
    }

    LinkTo withDefaults(LinkTo stub) {
        with(stub) {
                field() >> ""
                fieldId() >> ""
                provider() >> LinkTo.None
                providerType() >> Object
                strategy() >> LinkTo.Strategy.AUTO
                nameSuffix() >> ""
        }
        return stub
    }

}
