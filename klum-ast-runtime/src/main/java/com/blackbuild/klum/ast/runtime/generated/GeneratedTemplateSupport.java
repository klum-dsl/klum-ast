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
 * may link this type; their model-package {@code Foo_DSL.Template} contract remains the public API.</p>
 *
 * @param <T> the generated model type
 */
@SuppressWarnings("java:S100")
public class GeneratedTemplateSupport<T> {

    private final BoundTemplateHandler<T> delegate;

    public GeneratedTemplateSupport(Class<T> type) {
        delegate = new BoundTemplateHandler<>(type);
    }

    public <C> C With(T template, Closure<C> body) {
        return delegate.With(template, body);
    }

    public <C> C With(Map<String, ?> template, Closure<C> body) {
        return delegate.With(template, body);
    }

    public <C> C WithAll(Map<Class<?>, Map<String, ?>> newTemplates, Closure<C> body) {
        return delegate.WithAll(newTemplates, body);
    }

    public <C> C WithAll(List<Object> newTemplates, Closure<C> body) {
        return delegate.WithAll(newTemplates, body);
    }

    public T Create() {
        return delegate.Create();
    }

    public T Create(Map<String, ?> configMap, Closure<?> configuration) {
        return delegate.Create(configMap, configuration);
    }

    public T Create(Closure<?> configuration) {
        return delegate.Create(configuration);
    }

    public T Create(Map<String, ?> configMap) {
        return delegate.Create(configMap);
    }

    public T CreateFrom(File scriptFile) {
        return delegate.CreateFrom(scriptFile);
    }

    public T CreateFrom(File scriptFile, ClassLoader loader) {
        return delegate.CreateFrom(scriptFile, loader);
    }

    public T CreateFrom(URL scriptUrl) {
        return delegate.CreateFrom(scriptUrl);
    }

    public T CreateFrom(URL scriptUrl, ClassLoader loader) {
        return delegate.CreateFrom(scriptUrl, loader);
    }
}
