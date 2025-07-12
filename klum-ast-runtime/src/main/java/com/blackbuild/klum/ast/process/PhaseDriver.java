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
package com.blackbuild.klum.ast.process;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PhaseDriver {

    private static final ThreadLocal<PhaseDriver> INSTANCE = new ThreadLocal<>();

    @NotNull
    public static PhaseDriver getInstance() {
        if (INSTANCE.get() == null)
            INSTANCE.set(new PhaseDriver());
        return INSTANCE.get();
    }

    private final NavigableMap<Integer, List<PhaseAction>> phaseActions = new TreeMap<>();

    private final Set<Integer> applyLaterPhases = new TreeSet<>();

    private Object rootObject;
    private int activeObjectPointer = 0;

    private PhaseAction currentPhase;

    public PhaseDriver() {
        ServiceLoader.load(PhaseAction.class).forEach(this::addPhase);
    }

    public static KlumPhase getCurrentPhase() {
        PhaseAction phaseAction = getCurrentPhaseAction();
        return phaseAction == null ? null : phaseAction.getPhase();
    }

    public static String getCurrentPhaseActionType() {
        PhaseAction phaseAction = getCurrentPhaseAction();
        return phaseAction == null ? null : phaseAction.getClass().getName();
    }

    private static @Nullable PhaseAction getCurrentPhaseAction() {
        PhaseDriver phaseDriver = INSTANCE.get();
        if (phaseDriver == null || phaseDriver.currentPhase == null) return null;
        return phaseDriver.currentPhase;
    }

    public void addPhase(PhaseAction action) {
        phaseActions.computeIfAbsent(action.getPhaseNumber(), ignore -> new ArrayList<>()).add(action);
    }

    public void registerApplyLaterPhase(int phaseNumber) {
        if (applyLaterPhases.contains(phaseNumber)) return;
        applyLaterPhases.add(phaseNumber);
        addPhase(new ApplyLaterPhase(phaseNumber));
    }

    public static <T> T withPhase(Supplier<T> preparation, Consumer<T> action) {
        try {
            T result = preparation.get();
            PhaseDriver.enter(result);
            action.accept(result);
            PhaseDriver.executeIfReady();
            return result;
        } finally {
            PhaseDriver.leave();
        }
    }

    public static void enter(Object object) {
        PhaseDriver driver = getInstance();
        if (driver.activeObjectPointer == 0)
            driver.rootObject = object;
        driver.activeObjectPointer++;
    }

    public static void leave() {
        PhaseDriver driver = getInstance();
        driver.activeObjectPointer--;
        if (getInstance().activeObjectPointer == 0)
            INSTANCE.remove();
    }

    public static void executeIfReady() {
        PhaseDriver phaseDriver = getInstance();
        if (phaseDriver.activeObjectPointer != 1) return;
        for (List<PhaseAction> actions : phaseDriver.phaseActions.values()) {
            for (PhaseAction action : actions) {
                phaseDriver.currentPhase = action;
                action.execute();
            }
        }
    }

    public Object getRootObject() {
        return rootObject;
    }
}
