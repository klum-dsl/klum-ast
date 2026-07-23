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
package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.util.layer3.CompositionTraversal;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import com.blackbuild.klum.ast.util.layer3.StructuralPath;
import com.blackbuild.klum.ast.validation.KlumValidationResult;
import com.blackbuild.klum.ast.validation.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Java-first support for a completed DSL Object or one of its completed subtrees.
 *
 * <p>This facade exposes the completed object's construction path, structural model path, composition structure,
 * and stored validation without exposing its internal companion or generic extension metadata. A construction path
 * identifies the Builder/factory invocation that created the object. It is distinct from a structural model path,
 * contextual traversal paths, managed import sources, and validation locations; it does not represent provenance or
 * lineage.</p>
 *
 * @param <T> the completed DSL Object type
 */
public final class KlumObjectSupport<T> {

    private final T object;

    private KlumObjectSupport(T object) {
        this.object = object;
    }

    /**
     * Creates support for a completed DSL Object.
     *
     * @param object a completed DSL Object or subtree
     * @param <T> the completed DSL Object type
     * @return support for {@code object}
     * @throws KlumException if {@code object} is not a completed DSL Object
     */
    public static <T> KlumObjectSupport<T> of(T object) {
        if (object == null)
            throw new KlumException("KlumObjectSupport requires a non-null completed DSL Object");
        if (object instanceof KlumModelProxy)
            throw new KlumException("Object of type " + object.getClass().getName() + " is not a completed DSL Object");
        KlumModelProxy.getProxyFor(object);
        return new KlumObjectSupport<>(object);
    }

    /** Returns the completed DSL Object supplied to {@link #of(Object)}. */
    public T getObject() {
        return object;
    }

    /**
     * Returns the immutable Builder/factory invocation path through which this completed DSL Object was constructed.
     *
     * <p>This construction path is distinct from {@link #getModelPath()}, contextual traversal paths, managed import
     * sources, and validation locations. It does not retain provenance or lineage.</p>
     */
    public String getConstructionPath() {
        return DslHelper.getBreadcrumbPath(object);
    }

    /** Returns the structural model path of this completed DSL Object. */
    public String getModelPath() {
        return DslHelper.getModelPath(object);
    }

    /** Returns composition structure support rooted at this completed DSL Object. */
    public Structure<T> getStructure() {
        return new Structure<>(object);
    }

    /** Returns stored validation support rooted at this completed DSL Object. */
    public Validation<T> getValidation() {
        return new Validation<>(object);
    }

    /**
     * Stored validation support rooted at a completed DSL Object.
     *
     * <p>This helper only reads results recorded by the construction lifecycle. It never executes validators,
     * creates validation results, or mutates recorded issues.</p>
     *
     * @param <T> the root object type
     */
    public static final class Validation<T> {

        private final T object;

        private Validation(T object) {
            this.object = object;
        }

        /** Returns the validation result stored for this object, or {@code null} when no result was recorded. */
        public KlumValidationResult getResult() {
            return InternalKlumObjectSupport.getValidationResult(object);
        }

        /**
         * Returns stored validation results for this object and its owned composition subtree.
         * Owner and {@code LINK} fields are not followed.
         */
        public List<KlumValidationResult> getSubtreeResults() {
            List<KlumValidationResult> results = new ArrayList<>();
            KlumObjectSupport.of(object).getStructure().visit((path, element, container, nameOfFieldInContainer) -> {
                KlumValidationResult result = InternalKlumObjectSupport.getValidationResult(element);
                if (result != null)
                    results.add(result);
            });
            return List.copyOf(results);
        }

        /** Verifies stored subtree results using the configured validation failure level. */
        public List<KlumValidationResult> verify() throws KlumValidationException {
            return verify(Validator.getFailLevel());
        }

        /** Verifies stored subtree results using {@code failLevel}. */
        public List<KlumValidationResult> verify(Validate.Level failLevel) throws KlumValidationException {
            Objects.requireNonNull(failLevel, "failLevel");
            List<KlumValidationResult> results = getSubtreeResults();
            KlumValidationResult.throwOn(results, failLevel);
            return results;
        }
    }

    /**
     * Structural support rooted at a completed DSL Object.
     *
     * @param <T> the root object type
     */
    public static final class Structure<T> {

        private final T object;

        private Structure(T object) {
            this.object = object;
        }

        /** Returns the non-transitive, non-root owners directly assigned to this object. */
        public Set<Object> getDirectOwners() {
            return Collections.unmodifiableSet(new LinkedHashSet<>(KlumModelProxy.getProxyFor(object).getOwners()));
        }

        /**
         * Returns this object's sole direct owner, if it has one.
         *
         * @throws KlumModelException if this object has more than one distinct direct owner
         */
        public Optional<Object> getSingleOwner() {
            Set<Object> owners = getDirectOwners();
            if (owners.size() > 1)
                throw new KlumModelException("Object has more than one distinct owner");
            return owners.stream().findFirst();
        }

        /**
         * Returns the direct-owner hierarchy from this object to its composition root.
         *
         * @throws KlumSchemaException if direct owners form a cycle
         */
        public List<Object> getOwnerHierarchy() {
            List<Object> result = new ArrayList<>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Object current = object;
            while (DslHelper.isDslObject(current)) {
                if (!seen.add(current))
                    throw new KlumSchemaException("Object " + current + " has an owner cycle");
                result.add(current);
                current = KlumObjectSupport.of(current).getStructure().getSingleOwner().orElse(null);
            }
            return List.copyOf(result);
        }

        /** Returns the nearest owner-hierarchy member of {@code type}, if present. */
        public <R> Optional<R> getAncestorOfType(Class<R> type) {
            Objects.requireNonNull(type, "type");
            return getOwnerHierarchy().stream().filter(type::isInstance).map(type::cast).findFirst();
        }

        /** Returns the path from this object to the supplied owned descendant. */
        public String getRelativePath(Object child) {
            return getRelativePath(child, null);
        }

        /** Returns the path from this object to the supplied owned descendant, prefixed by {@code rootPath}. */
        public String getRelativePath(Object child, String rootPath) {
            return createPath(child, candidate -> candidate == object, rootPath);
        }

        /** Returns this object's path relative to its composition root. */
        public String getFullPath() {
            return getFullPath(null);
        }

        /** Returns this object's path relative to its composition root, prefixed by {@code rootPath}. */
        public String getFullPath(String rootPath) {
            return createPath(object, null, rootPath);
        }

        /**
         * Visits this completed-object composition graph using the established four-argument visitor contract.
         * Owner and {@code LINK} fields are not followed.
         */
        public void visit(ModelVisitor visitor) {
            visit(visitor, "<root>");
        }

        /**
         * Visits this completed-object composition graph using the established four-argument visitor contract.
         * Owner and {@code LINK} fields are not followed.
         */
        public void visit(ModelVisitor visitor, String rootPath) {
            Objects.requireNonNull(visitor, "visitor");
            CompositionTraversal.visit(object, visitor, rootPath);
        }

        /** Visits every composed object assignable to {@code type}. */
        public <R> void visit(Class<R> type, BiConsumer<String, R> visitor) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(visitor, "visitor");
            visit((path, element, container, nameOfFieldInContainer) -> {
                if (type.isInstance(element))
                    visitor.accept(path, type.cast(element));
            });
        }

        /** Returns every composed object assignable to {@code type}, indexed by its path from this structure root. */
        public <R> Map<String, R> findAll(Class<R> type) {
            return findAll(type, "<root>");
        }

        /**
         * Returns every composed object assignable to {@code type}, indexed by paths using {@code rootPath}.
         * This overload supports compatibility adapters that retain an existing path prefix.
         */
        public <R> Map<String, R> findAll(Class<R> type, String rootPath) {
            Map<String, R> result = new LinkedHashMap<>();
            Objects.requireNonNull(type, "type");
            visit((path, element, container, nameOfFieldInContainer) -> {
                if (type.isInstance(element))
                    result.put(path, type.cast(element));
            }, rootPath);
            return result;
        }

        private static String createPath(Object child, Predicate<Object> stopCondition, String rootPath) {
            List<Object> hierarchy = KlumObjectSupport.of(child).getStructure().getOwnerHierarchy();
            int ancestorIndex;
            if (stopCondition == null) {
                ancestorIndex = hierarchy.size() - 1;
            } else {
                ancestorIndex = -1;
                for (int i = 0; i < hierarchy.size(); i++) {
                    if (stopCondition.test(hierarchy.get(i))) {
                        ancestorIndex = i;
                        break;
                    }
                }
                if (ancestorIndex == -1)
                    throw new IllegalArgumentException("Could not find matching ancestor");
            }

            Deque<String> elements = StructuralPath.hierarchyToPath(hierarchy.subList(0, ancestorIndex + 1));
            if (rootPath != null)
                elements.addFirst(rootPath);
            return String.join(".", elements);
        }

    }
}
