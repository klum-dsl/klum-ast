/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.runtime.generated;

import com.blackbuild.klum.ast.runtime.internal.BoundTemplateHandler;
import groovy.lang.Closure;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Generated-code linkage for the model-package Template adapters emitted by the DSL transformation.
 *
 * <p>This is not a supported handwritten client API or general extension SPI. Generated schema classes
 * may link this type; their model-package {@code Foo_DSL.TemplateScope} contract remains the public API.</p>
 *
 * @param <T> the generated model type
 */
@SuppressWarnings("java:S100")
public class GeneratedTemplateSupport<T> {

    private final BoundTemplateHandler<T> templateHandler;
    private final GeneratedTemplateFactorySupport<T> templateFactory;

    public GeneratedTemplateSupport(Class<T> type) {
        templateHandler = new BoundTemplateHandler<>(type);
        templateFactory = new GeneratedTemplateFactorySupport<>(type);
    }

    public <C> C With(T template, Closure<C> body) {
        return templateHandler.With(template, body);
    }

    public <C> C With(Map<String, ?> template, Closure<C> body) {
        return templateHandler.With(template, body);
    }

    public <C> C WithAll(Map<Class<?>, Map<String, ?>> newTemplates, Closure<C> body) {
        return templateHandler.WithAll(newTemplates, body);
    }

    public <C> C WithAll(List<Object> newTemplates, Closure<C> body) {
        return templateHandler.WithAll(newTemplates, body);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.With()} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T Create() {
        return templateFactory.With();
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.With(configMap, configuration)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T Create(Map<String, ?> configMap, Closure<?> configuration) {
        return templateFactory.With(configMap, configuration);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.With(configuration)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T Create(Closure<?> configuration) {
        return templateFactory.With(configuration);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.With(configMap)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T Create(Map<String, ?> configMap) {
        return templateFactory.With(configMap);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.From(scriptFile)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T CreateFrom(File scriptFile) {
        return templateFactory.From(scriptFile);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.From(scriptFile, loader)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T CreateFrom(File scriptFile, ClassLoader loader) {
        return templateFactory.From(scriptFile, loader);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.From(scriptUrl)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T CreateFrom(URL scriptUrl) {
        return templateFactory.From(scriptUrl);
    }

    /**
     * @deprecated Use {@code Foo.Create.Template.From(scriptUrl, loader)} for the matching DSL model type.
     */
    @Deprecated(since = "4.0")
    public T CreateFrom(URL scriptUrl, ClassLoader loader) {
        return templateFactory.From(scriptUrl, loader);
    }
}
