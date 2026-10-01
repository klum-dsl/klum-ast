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
package com.blackbuild.klum.ast.gradle;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/** Opt-in portable GDSL producer declarations. */
public abstract class KlumSchemaGdslExtension {
    private final NamedDomainObjectContainer<KlumGdslMapping> mappings;

    @Inject
    public KlumSchemaGdslExtension(ObjectFactory objects) {
        getPublish().convention(false);
        mappings = objects.domainObjectContainer(KlumGdslMapping.class,
                name -> objects.newInstance(KlumGdslMapping.class, name));
    }

    public abstract Property<Boolean> getPublish();

    public NamedDomainObjectContainer<KlumGdslMapping> getMappings() {
        return mappings;
    }

    public void mappings(Action<? super NamedDomainObjectContainer<KlumGdslMapping>> action) {
        action.execute(mappings);
    }
}
