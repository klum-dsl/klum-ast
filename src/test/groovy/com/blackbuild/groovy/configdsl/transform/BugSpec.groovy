package com.blackbuild.groovy.configdsl.transform
/**
 * Tests for various encountered bugs.
 */
class BugSpec extends AbstractDSLSpec {

    def "method with default value is already defined"() {
        when:
        createClass '''
@DSL class ImageRegistrySpec {
}

@DSL
class ImagePushSpecification {
    @Key String imageName
    ImageRegistrySpec srcImageRegistry
}
'''
        then:
        noExceptionThrown()

    }


}