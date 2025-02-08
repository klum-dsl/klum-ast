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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.ast.util.KlumException;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;

/**
 * An Exception that is bound to a specific object in the model.
 */
public class KlumVisitorException extends KlumException {

    private final String breadcrumbPath;

    public KlumVisitorException(String message, Object responsibleObject, Throwable cause) {
        super(message, cause);
        if (responsibleObject instanceof KlumInstanceProxy)
            this.breadcrumbPath = ((KlumInstanceProxy) responsibleObject).getBreadcrumbPath();
        else if (DslHelper.isDslObject(responsibleObject))
            this.breadcrumbPath = KlumInstanceProxy.getProxyFor(responsibleObject).getBreadcrumbPath();
        else
            this.breadcrumbPath = null;
    }

    public KlumVisitorException(String message, Object responsibleObject) {
        this(message, responsibleObject, null);
    }

    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    @Override
    public String getMessage() {
        if (breadcrumbPath == null)
            return super.getMessage();
        return breadcrumbPath + ": " + super.getBasicMessage();
    }

    public String getUnlocalizedMessage() {
        return super.getMessage();
    }
}
