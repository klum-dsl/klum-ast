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
package fixture.consumer;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
public class ArtifactResourceControl {
    @SuppressWarnings("unchecked") // The private projector implements this JDK-only bridge in its isolated loader.
    public static void main(String[] args) throws Exception {
        URL[] inputs = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(value -> {
                    try { return Path.of(value).toUri().toURL(); }
                    catch (Exception failure) { throw new IllegalArgumentException(failure); }
                }).toArray(URL[]::new);
        URL shadow = new URL("jar:" + Path.of(args[0]).toUri().toURL() + "!/fixture/parent/Destination_DSL$Builder.class");
        try (URLClassLoader loader = new URLClassLoader(inputs, ClassLoader.getPlatformClassLoader()) {
            @Override public URL getResource(String name) {
                return name.equals("fixture/parent/Destination_DSL$Builder.class") ? shadow : super.getResource(name);
            }
        }) {
            if (args[1].equals("projection")) {
                Class<?> type = Class.forName("com.blackbuild.klum.ast.runtime.internal.JsonSchemaProjection", true, loader);
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                BiFunction<List<String>, List<String>, Map<String, String>> projector =
                        (BiFunction<List<String>, List<String>, Map<String, String>>) constructor.newInstance();
                projector.apply(List.of("fixture.child.Child"), List.of());
            } else {
                Class.forName("fixture.consumer.Main", true, loader).getMethod("main", String[].class)
                        .invoke(null, (Object) new String[]{"read"});
            }
            throw new AssertionError("shadow contract accepted");
        }
    }
}
