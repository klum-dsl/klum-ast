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
package com.blackbuild.klum.ast.ast;

import com.blackbuild.annodocimal.ast.Documentation;

import java.util.Collection;
import java.util.Map;

/** KlumAST's concise vocabulary for the supported immutable AnnoDocimal documentation model. */
public final class KlumDocumentation {

    private Documentation.Builder builder = Documentation.builder();

    public KlumDocumentation title(String text) {
        builder.summary(text);
        return this;
    }

    public KlumDocumentation p(String text) {
        builder.paragraph(text);
        return this;
    }

    public KlumDocumentation returnType(String text) {
        builder.returns(text);
        return this;
    }

    public KlumDocumentation param(String name, String text) {
        builder.param(name, text);
        return this;
    }

    public KlumDocumentation throwsException(String name, String text) {
        builder.throwsException(name, text);
        return this;
    }

    public KlumDocumentation deprecated(String text) {
        builder.tag("deprecated", text == null ? "" : text);
        return this;
    }

    public KlumDocumentation see(Documentation.Link link) {
        builder.see(link);
        return this;
    }

    public KlumDocumentation seeAlso(String target) {
        return see(Documentation.Link.text(target));
    }

    public KlumDocumentation templates(Map<String, String> values) {
        builder.templateValues(values);
        return this;
    }

    KlumDocumentation replace(Documentation documentation) {
        Map<String, String> currentTemplates = build().getTemplateValues();
        builder = documentation.toBuilder().templateValues(currentTemplates);
        return this;
    }

    KlumDocumentation replaceParameters(Map<String, String> parameters) {
        builder.replaceParameters(parameters);
        return this;
    }

    KlumDocumentation filterParameters(Collection<String> parameters) {
        builder.filterParameters(parameters);
        return this;
    }

    Documentation build() {
        return builder.build();
    }

    Documentation rendered() {
        Documentation documentation = build();
        if (documentation.getTemplateValues().isEmpty()) return documentation;
        String rendered = documentation.render();
        for (Map.Entry<String, String> value : documentation.getTemplateValues().entrySet()) {
            rendered = rendered.replace("{{" + value.getKey() + "}}", value.getValue());
        }
        return Documentation.parse(rendered);
    }

    KlumDocumentation copy() {
        return new KlumDocumentation().replace(build());
    }

    boolean isEmpty() {
        return build().isEmpty();
    }
}
