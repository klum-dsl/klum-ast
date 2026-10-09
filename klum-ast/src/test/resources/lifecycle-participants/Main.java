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
package participant.fixture;
import com.blackbuild.klum.ast.runtime.KlumSchemaException;
import java.util.Map;
public class Main {
    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                Application.Create.With(Map.of("factSource", "binary"));
                throw new AssertionError("Invalid precompiled handler was accepted");
            } catch (RuntimeException failure) {
                Throwable expected = failure;
                while (expected.getCause() != null && !(expected instanceof KlumSchemaException)) expected = expected.getCause();
                if (!(expected instanceof KlumSchemaException) ||
                        !expected.getMessage().contains("annotation parameter must resolve exactly")) throw failure;
                System.out.println("binary-defense=true");
                return;
            }
        }
        Application javaApplication = Application.Create.With(Map.of("factSource", "java"));
        Application staticApplication = StaticWriter.create();
        Application dynamicApplication = DynamicWriter.create();
        if (!javaApplication.getDomain().getFacts().getSource().equals("java") ||
                !staticApplication.getDomain().getFacts().getSource().equals("static") ||
                !dynamicApplication.getDomain().getFacts().getSource().equals("dynamic"))
            throw new AssertionError("Consumer linkage");
        System.out.println("participant-consumers=true");
    }
}
