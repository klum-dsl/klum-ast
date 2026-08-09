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

import com.blackbuild.klum.ast.runtime.internal.FactoryHelper;
import groovy.lang.Closure;

import java.io.File;
import java.net.URL;
import java.util.Map;

/**
 * Generated-code linkage for the model-package Template Factory adapters emitted by the DSL transformation.
 *
 * <p>This is not a supported handwritten client API or general extension SPI. Generated schema classes link this
 * type through their model-package {@code Foo_DSL.Factory.Template} contract.</p>
 *
 * @param <T> the generated model type
 */
@SuppressWarnings("java:S100")
public final class GeneratedTemplateFactorySupport<T> {

    private final Class<T> type;

    public GeneratedTemplateFactorySupport(Class<T> type) {
        this.type = type;
    }

    public T With() {
        return FactoryHelper.createAsTemplate(type, null, (Closure<?>) null);
    }

    public T With(Map<String, ?> configMap, Closure<?> configuration) {
        return FactoryHelper.createAsTemplate(type, configMap, configuration);
    }

    public T With(Closure<?> configuration) {
        return FactoryHelper.createAsTemplate(type, null, configuration);
    }

    public T With(Map<String, ?> configMap) {
        return FactoryHelper.createAsTemplate(type, configMap, null);
    }

    public T From(File scriptFile) {
        return FactoryHelper.createAsTemplate(type, scriptFile, null);
    }

    public T From(File scriptFile, ClassLoader loader) {
        return FactoryHelper.createAsTemplate(type, scriptFile, loader);
    }

    public T From(URL scriptUrl) {
        return FactoryHelper.createAsTemplate(type, scriptUrl, null);
    }

    public T From(URL scriptUrl, ClassLoader loader) {
        return FactoryHelper.createAsTemplate(type, scriptUrl, loader);
    }
}
