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
import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationContext;
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
import java.lang.annotation.Annotation;
import java.util.Map;
abstract class BaseHandler<A extends Annotation> implements LifecycleMutationHandler<A> {}
public class BindFacts extends BaseHandler<Binding> {
    private boolean used;
    public void mutate(LifecycleMutationContext<Binding> context) {
        if (used) throw new AssertionError("Handler reused");
        used = true;
        Knowledge_DSL.Builder<Knowledge> application = Knowledge.Create.narrowBuilder(context.getContainingBuilder());
        Domain_DSL.Builder<Domain> domain = Domain.Create.narrowBuilder(context.getTargetBuilder());
        if (!context.getAnnotation().value().equals("java") ||
                context.getAnnotation(Binding.class).orElseThrow() != context.getAnnotation() ||
                context.getAnnotation(Deprecated.class).isPresent() ||
                !context.getFieldName().equals("domain") || context.getDeclaredType() != Domain.class ||
                context.getFieldType() != FieldType.DEFAULT ||
                KlumBuilderSupport.of(context.getTargetBuilder()).getModelType() != Domain.class)
            throw new AssertionError("Context contract");
        domain.facts(Facts.Create.AsBuilder().With(Map.of("source", application.getFactSource())));
    }
}
