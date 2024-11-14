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

import org.gradle.api.GradleException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PluginHelper {

    private PluginHelper() {}

    public static String determineOwnVersion() {
        Properties props = new Properties();
        try (InputStream is = PluginHelper.class.getClassLoader().getResourceAsStream("META-INF/versions/com.blackbuild.klum.ast.gradle.AbstractKlumPlugin")) {
            props.load(is);
        } catch (IOException e) {
            throw new GradleException("Could not determine version of plugin.", e);
        }
        return props.getProperty("version");
    }

}
