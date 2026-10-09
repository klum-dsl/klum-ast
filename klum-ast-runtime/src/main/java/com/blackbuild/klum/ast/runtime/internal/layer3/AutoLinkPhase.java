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
package com.blackbuild.klum.ast.runtime.internal.layer3;

import com.blackbuild.klum.ast.runtime.DefaultKlumPhase;
import com.blackbuild.klum.ast.runtime.BuilderVisitingPhaseAction;
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder;
import com.blackbuild.klum.ast.runtime.internal.LifecycleHelper;
import com.blackbuild.klum.ast.runtime.internal.LifecycleParticipants;
import com.blackbuild.klum.ast.layer3.AutoLink;
import com.blackbuild.klum.ast.layer3.LinkTo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class AutoLinkPhase extends BuilderVisitingPhaseAction {

    public AutoLinkPhase() {
        super(DefaultKlumPhase.AUTO_LINK);
    }

    @Override
    protected void doVisit(@NotNull String path, @NotNull InternalKlumBuilder<?> element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        Map<String, Object> fields = ClusterModel.getPropertiesStream(element, Object.class,
                        field -> field.isAnnotationPresent(LinkTo.class) || LifecycleParticipants.hasParticipant(field))
                .collect(HashMap::new, (result, field) -> result.put(field.getName(), field.getValue()), Map::putAll);
        fields.entrySet()
                .forEach(entry -> {
                    if (isUnset(entry) && element.getModelField(entry.getKey()).isAnnotationPresent(LinkTo.class))
                        LinkHelper.autoLink(element, entry.getKey());
                    LifecycleParticipants.processField(element, entry.getKey());
                });

        LifecycleHelper.executeLifecycleMethods(element, AutoLink.class);
    }

}
