/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.validation.AstValidator;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Set;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class LinkToAstValidator extends AstValidator {

    private Set<String> members;

    @Override
    protected void extraValidation() {
        members = annotation.getMembers().keySet();
        ownerAndOwnerTypeAreMutuallyExclusive();
        strategyInstanceNameNeedsOwnerOrOwnerType();
    }

    private void strategyInstanceNameNeedsOwnerOrOwnerType() {
        if (!members.contains("strategy")) return;
        if (members.contains("owner") || members.contains("ownerType")) return;
        addError("strategy INSTANCE_NAME needs owner or ownerType", annotation);
    }

    private void ownerAndOwnerTypeAreMutuallyExclusive() {
        if (members.contains("owner") && members.contains("ownerType"))
            addError("Only one of owner and ownerType is allowed", annotation);
    }
}
