/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
package com.blackbuild.klum.ast.gradle;

import com.blackbuild.annodocimal.plugin.AnnoDocimalPlugin;
import org.gradle.api.NonNullApi;
import org.gradle.api.plugins.PluginManager;

@NonNullApi
public class KlumAstSchemaPlugin extends AbstractKlumPlugin<KlumExtension> {

    @Override
    protected void registerExtension() {
        extension = project.getExtensions().create("klumSchema", KlumExtension.class);
    }

    protected void addDependentPlugins() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(AnnoDocimalPlugin.class);
    }

    protected void addDependencies() {
        project.getDependencies().add("compileOnly", "com.blackbuild.klum.ast:klum-ast:" + version);
        project.getDependencies().add("api", "com.blackbuild.klum.ast:klum-ast-runtime:" + version);
    }

    @Override
    protected void additionalConfig() {
        // empty for now
    }
}
