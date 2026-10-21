package example

import groovy.transform.CompileStatic

// #896 diagnostic control: generated binary Builder must reject an unknown operation.
@CompileStatic
class BrokenModel {
    static void build(CustomerEnvironment_DSL.Builder<?> builder) {
        builder.missingDslOperation('invalid')
    }
}
