package com.blackbuild.groovy.configdsl.transform


class Bla {

    def static withTemplates(Map<Class, Object> templates, Closure closure) {
        if (!templates)
            return closure.call()

        def keys = templates.keySet().asList()
        def firstKey = keys.head()

        firstKey.withTemplate(templates.get(firstKey)) {
            Bla.withTemplates(templates.subMap(keys.tail(), closure))
        }
    }
}



{ -> }.curry()