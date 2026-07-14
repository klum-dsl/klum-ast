package example

import com.blackbuild.annodocimal.annotations.AnnoDoc

@AnnoDoc('Documentation for Foo_DSL')
interface Foo_DSL {
    @AnnoDoc('Documentation for Builder')
    interface Builder {
    }
}
