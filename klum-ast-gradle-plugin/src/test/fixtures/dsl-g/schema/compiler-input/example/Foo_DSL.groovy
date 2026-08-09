package example

import com.blackbuild.annodocimal.annotations.AnnoDoc
import com.blackbuild.klum.ast.runtime.KlumBuilder
import com.blackbuild.klum.ast.runtime.KlumFactory.BuilderFactoryProvider

interface Recipient {
}

@AnnoDoc('Documentation for Foo_DSL')
interface Foo_DSL {
    interface Factory {
        Recipient With(Map<String, ?> values)
    }

    interface Template {
        Recipient Create(Map<String, ?> values)
    }

    @AnnoDoc('Documentation for Builder')
    interface Builder<T extends Recipient> extends KlumBuilder<T> {
        BuilderFactoryProvider<T, ? extends KlumBuilder<T>> recipient(
                BuilderFactoryProvider<T, ? extends KlumBuilder<T>> factory,
                Closure<?> body)
    }
}

class Foo {
    public static final Foo_DSL.Factory Create = null
    public static final Foo_DSL.Template Template = null
}
