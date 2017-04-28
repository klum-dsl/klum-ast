package com.blackbuild.groovy.configdsl.transform

import spock.lang.Specification


/**
 * Created by snpaux on 28.04.2017.
 */
class DebugWithMultipleFilesSpec extends AbstractFolderBasedDSLSpec {

    def "load files for alternatives"() {
        given:
        loadFrom("alternatives")

        when:
        loader.loadClass("Config")

        then:
        noExceptionThrown()



    }

}