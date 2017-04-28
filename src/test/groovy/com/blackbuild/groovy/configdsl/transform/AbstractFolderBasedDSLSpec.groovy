package com.blackbuild.groovy.configdsl.transform


class AbstractFolderBasedDSLSpec extends AbstractDSLSpec {


    def loadFrom(String... folder) {
        File root = new File("src/test/folders")
        folder.each {
            loader.addURL(new File(root, it).toURI().toURL())
        }
    }


}