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
package com.blackbuild.klum.ast.process;

import com.blackbuild.klum.ast.util.InternalKlumBuilder;
import com.blackbuild.klum.ast.util.KlumModelException;
import com.blackbuild.klum.ast.util.InternalKlumObjectSupport;
import groovy.lang.Closure;
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

    // used to prevent concurrent modification exceptions. Used if a phase action registers other actions
    private final NavigableMap<Integer, ApplyLaterPhase> applyLaterPhaseActions = new TreeMap<>();

    private final List<Closure<?>> postPhaseClosures = new ArrayList<>();

    private final Context context = new Context();

    private Object rootObject;
    private int activeObjectPointer = 0;
    private ConstructionSession constructionSession;
    private final Set<InternalKlumBuilder<?>> constructionSessionBuilders =
            Collections.newSetFromMap(new IdentityHashMap<>());

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

    public static Context getContext() {
        return getInstance().context;
    }

    public static void setCurrentMember(String member) {
        getInstance().context.setMember(member);
    }

    public void addPhase(PhaseAction action) {
        phaseActions.computeIfAbsent(action.getPhaseNumber(), ignore -> new ArrayList<>()).add(action);
    }

    public void registerApplyLaterPhase(int phaseNumber) {
        applyLaterPhaseActions.computeIfAbsent(phaseNumber, ignore -> new ApplyLaterPhase(phaseNumber));
    }

    /**
     * Registers a closure to be run directly after the current phase. This can be used to circumvent corner cases,
     * where an object modifies its own containing datastructure.
     * @param closure The closure to be executed after the current phase.
     */
    public static void postPhaseApply(Closure<?> closure) {
        if (getCurrentPhase() == null)
            throw new IllegalStateException("Cannot register post-phase action outside of a phase");
        getInstance().postPhaseClosures.add(closure);
    }

    public static <T> T withPhaseDriver(Supplier<T> generator, Consumer<T> action) {
        Object oldInstance = PhaseDriver.getInstance().context.getInstance();
        try {
            T result = generator.get();
            PhaseDriver.getInstance().context.setInstance(result);
            PhaseDriver.enter(result);
            action.accept(result);
            PhaseDriver.executeIfReady();
            return result;
        } finally {
            PhaseDriver.getInstance().context.setInstance(oldInstance);
            PhaseDriver.leave();
        }
    }

    /** Runs one complete Builder lifecycle and returns its completed model. */
    public static <T> T withBuilderLifecycle(Supplier<? extends InternalKlumBuilder<T>> generator, Consumer<InternalKlumBuilder<T>> action) {
        PhaseDriver driver = PhaseDriver.getInstance();
        if (driver.activeObjectPointer > 0 || driver.constructionSession != null)
            throw new KlumModelException("Cannot start an independent DSL Object factory while a Builder lifecycle is active. "
                    + "Create composition through the owning Builder's generated relationship methods; "
                    + "completed DSL Objects may only be created beforehand and assigned to LINK fields.");

        Object oldInstance = driver.context.getInstance();
        driver.constructionSession = new ConstructionSession();
        boolean entered = false;
        try {
            InternalKlumBuilder<T> builder = generator.get();
            attachToCurrentConstructionSession(builder);
            driver.context.setInstance(builder);
            PhaseDriver.enter(builder);
            entered = true;
            action.accept(builder);
            PhaseDriver.executeIfReady();
            return (T) driver.rootObject;
        } finally {
            driver.context.setInstance(oldInstance);
            if (entered)
                PhaseDriver.leave();
            else {
                driver.completeConstructionSession();
                INSTANCE.remove();
            }
        }
    }

    /** Associates a newly allocated Builder with the active root lifecycle, if one exists. */
    public static void attachToCurrentConstructionSession(InternalKlumBuilder<?> builder) {
        PhaseDriver driver = INSTANCE.get();
        if (driver == null || driver.constructionSession == null)
            return;
        builder.$attachConstructionSession(driver.constructionSession);
        driver.constructionSessionBuilders.add(builder);
    }

    /** Rejects Builder-producing factory calls that are not owned by a root lifecycle. */
    public static void requireActiveConstructionSession() {
        PhaseDriver driver = INSTANCE.get();
        if (driver == null || driver.constructionSession == null)
            throw new KlumModelException("Create.AsBuilder requires an active Construction session. "
                    + "Call it inside the owning root Builder lifecycle and attach the returned Builder to an owned relationship; "
                    + "use Create.With, Create.One, or Create.From for a standalone completed DSL Object.");
    }

    public static void enter(Object object) {
        PhaseDriver driver = getInstance();
        if (driver.activeObjectPointer == 0) {
            driver.rootObject = object;
            if (object instanceof InternalKlumBuilder)
                ((InternalKlumBuilder<?>) object).setModelPath("<root>");
            else
                InternalKlumObjectSupport.setModelPathIfAbsent(object, "<root>");
        }
        driver.activeObjectPointer++;
    }

    public static void leave() {
        PhaseDriver driver = getInstance();
        driver.activeObjectPointer--;
        if (driver.activeObjectPointer == 0) {
            driver.completeConstructionSession();
            INSTANCE.remove();
        }
    }

    private void completeConstructionSession() {
        ConstructionSession completedSession = constructionSession;
        if (completedSession == null)
            return;
        constructionSessionBuilders.forEach(builder -> builder.$completeConstructionSession(completedSession));
        constructionSessionBuilders.clear();
        constructionSession = null;
    }

    public static void executeIfReady() {
        PhaseDriver phaseDriver = getInstance();
        if (phaseDriver.activeObjectPointer != 1) return;
        int lastPhase = 0;
        for (Map.Entry<Integer, List<PhaseAction>> entries : phaseDriver.phaseActions.entrySet()) {
            Integer currentPhase = entries.getKey();
            // execute all apply-later phases between the last executed phase and before the current phase
            for (ApplyLaterPhase applyLaterPhase : phaseDriver.applyLaterPhaseActions.subMap(lastPhase, currentPhase).values()) {
                phaseDriver.executeAction(applyLaterPhase);
            }

            for (PhaseAction action : entries.getValue()) {
                phaseDriver.executeAction(action);
            }
            phaseDriver.callApplyLaterClosures();
            lastPhase = currentPhase;
        }
        // execute remaining apply-later phases
        for (ApplyLaterPhase applyLaterPhase : phaseDriver.applyLaterPhaseActions.tailMap(lastPhase).values()) {
            applyLaterPhase.execute();
        }
    }

    private void callApplyLaterClosures() {
        postPhaseClosures.forEach(Closure::call);
        postPhaseClosures.clear();
    }

    private void executeAction(PhaseAction action) {
        currentPhase = action;
        action.execute();
    }

    public Object getRootObject() {
        return rootObject;
    }

    void replaceRootObject(Object rootObject) {
        this.rootObject = Objects.requireNonNull(rootObject);
    }

    public static class Context {
        private KlumPhase phase = DefaultKlumPhase.CREATE ;
        private Object instance;
        private String member;

        public KlumPhase getPhase() {
            return phase;
        }

        public void setPhase(KlumPhase phase) {
            this.phase = phase;
        }

        public Object getInstance() {
            return instance;
        }

        public void setInstance(Object instance) {
            this.instance = instance;
        }

        public String getMember() {
            return member;
        }

        public void setMember(String member) {
            this.member = member;
        }
    }
}
