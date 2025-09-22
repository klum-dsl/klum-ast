/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3.annotations;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.NeedsOneOf;

import java.lang.annotation.*;

/**
 * Can be used to mark fields to emit a notification if its value is manually set or not set.
 * This is most useful in combination with {@link com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate}
 * or {@link com.blackbuild.klum.ast.util.layer3.annotations.LinkTo}, to designate that the AutoCreate/AutoLink mechanism
 * is either an unwanted fallback (ifUnset), or the expected behavior (ifSet).
 * <p>Notify can also be used to change the default behavior for deprecated fields.</p>
 * The default level is WARNING, but this can be overridden by {@link #level()}.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@NeedsDSLClass
@NeedsOneOf(value = {"ifSet", "ifUnset"}, exclusive = true)
@Documented
public @interface Notify {
    /** The message to be given for the generated issue if the field was manually set. */
    String ifSet() default "";
    /** The message to be given for the generated issue if the field was not manually set. */
    String ifUnset() default "";
    /** The level of the generated issue. */
    Validate.Level level() default Validate.Level.WARNING;
}
