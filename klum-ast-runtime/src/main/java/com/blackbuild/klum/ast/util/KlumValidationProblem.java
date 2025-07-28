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
package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Validate;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;

import static java.util.Comparator.*;

public class KlumValidationProblem implements Serializable, Comparable<KlumValidationProblem> {

    private static final Comparator<KlumValidationProblem> COMPARATOR =
            comparing(KlumValidationProblem::getBreadcrumbPath, nullsLast(naturalOrder()))
            .thenComparing(KlumValidationProblem::getLevel, reverseOrder())
            .thenComparing(KlumValidationProblem::getMember, nullsLast(naturalOrder()));
    public final String member;
    public final String message;
    public final Exception exception;
    public final Validate.Level level;
    private final String breadcrumbPath;

    public KlumValidationProblem(String breadcrumbPath, String member, String message, Exception exception, Validate.Level level) {
        this.breadcrumbPath = breadcrumbPath;
        this.member = member;
        this.message = message;
        this.exception = exception;
        this.level = level;
    }

    public String getMessage() {
        return message;
    }

    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    public String getFullPath() {
        return breadcrumbPath + (member != null ? "#" + member : "");
    }

    public String getFullMessage() {
        return getLevel().name() + " " + getFullPath() + ": " + message;
    }

    public String getLocalMessage() {
        return getLevel().name() + " #" + getMember() + ": " + message;
    }

    public String getMember() {
        return member;
    }

    public Exception getException() {
        return exception;
    }

    public Validate.Level getLevel() {
        return level;
    }

    @Override
    public int compareTo(@NotNull KlumValidationProblem o) {
        return COMPARATOR.compare(this, o);
    }

}
