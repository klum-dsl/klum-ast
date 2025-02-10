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
package com.blackbuild.klum.ast.process;

import com.blackbuild.klum.ast.util.KlumException;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.util.Collection;
import java.util.Map;

/**
 * Represents an action that is executed in a phase. The action is executed for each element in the model.
 */
public abstract class AbstractPhaseAction implements PhaseAction {

    private final KlumPhase phase;

    protected AbstractPhaseAction(KlumPhase phase) {
        if (phase.getNumber() < 0)
            throw new IllegalArgumentException("Phase must be >= 0");
        if (phase.getNumber() == 0)
            throw new IllegalArgumentException("Creation Phase (0) cannot execute custom actions");
        this.phase = phase;
    }

    @Override
    public KlumPhase getPhase() {
        return phase;
    }

    @Override
    public void execute() {
        try {
            doExecute();
        } catch (KlumException e) {
            throw e;
        } catch (Exception e) {
            throw new KlumException(e);
        }
    }

    protected abstract void doExecute();

    protected boolean isUnset(Map.Entry<String, Object> entry) {
        Object value = entry.getValue();
        return isEmpty(value);
    }

    protected static boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof Collection)
            return ((Collection<?>) value).isEmpty();
        if (value instanceof Map)
            return ((Map<?, ?>) value).isEmpty();
        if (!DefaultTypeTransformation.castToBoolean(value))
            return true;
        return false;
    }
}
