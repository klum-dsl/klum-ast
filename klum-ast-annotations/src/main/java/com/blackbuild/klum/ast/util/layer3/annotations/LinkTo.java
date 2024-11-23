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
package com.blackbuild.klum.ast.util.layer3.annotations;

import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.AlsoNeeds;
import com.blackbuild.klum.cast.checks.MutuallyExclusive;
import groovy.lang.Closure;

import java.lang.annotation.*;

/**
 * Provides mechanisms to automatically fill a field with an existing object from somewhere else in the model tree.
 *
 * <h2>Example</h2>
 * Consider an environment model where you define different, interdependent services. Service 'provider' defines
 * multiple users for various tasks, one being the one used by consumer. So we want the consumer object to use the same
 * User object as the provider. This might look like this:
 *
 * <pre><code>
 * {@literal @}DSL abstract class Service {
 *   {@literal @}Owner Environment env
 * }
 *
 * {@literal @}DSL class Producer extends Service {
 *   User admin
 *   User internal
 *   User monitoring
 * }
 *
 * {@literal @}DSL class Consumer extends Service {
 *   {@literal @}LinkTo(provider={env.services.consumer}) User internal
 * }
 * </code></pre>
 *
 * The LinkTo annotation on the internal field of the Consumer class will cause the internal field to be filled with the
 * same User object as the internal field of the Producer class.
 *
 * <h2>Usage</h2>
 *
 * LinkTo is handled in the AutoLink phase, i.e. after owners have been set and auto-create objects have been created.
 * It will work on any annotated field that is not yet set.
 *
 * <h3>provider</h3>
 *
 * The link mechanism is centered around the provider object, i.e. the object that contains the field to be linked. This
 * is determined the following way:
 *
 * <ul>
 *     <li>provider: contains a code closure that is run relative to the annotated field's instance to access the owner (like in the example)</li>
 *     <li>providerType: contains a type of owner. Finds the first element of the given type in the owner hierarchy</li>
 *     <li>otherwise, the single owner field of the annotated field's class is used</li>
 * </ul>
 *
 * If the annotated field's instance has no owner or multiple owner fields, the provider must be specified explicitly using the provider member.
 * If the provider evaluates to null, the link is not set (no exception is thrown).
 *
 * <h4>Map provider</h4>
 *
 * If the provider is a map, the field name is used as the key to access the provider. If the key does not exist, the link is not set.
 *
 * <h3>target field</h3>
 *
 * Once the provider is determined, the field of the provider to be used as the provider of the link is resolved. This is done the following way:
 * <ul>
 *     <li>If the field member is set, the field with the given name is used</li>
 *     <li>if the fieldId member is set, the field with the matching LinkSource annotation is taken. It is illegal
 *     to have field and fieldId set together</li>
 *     <li>if neither field nor fieldId is set, the field with the same name as the annotated field is used</li>
 *     <li>if no field with the given name exists and exactly one field not annotated with LinkSource and of the correct type exists, that one is used</li>
 *     <li>if no matching field is found, an exception is thrown</li>
 * </ul>
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@WriteAccess(WriteAccess.Type.LIFECYCLE)
@KlumCastValidated
@NeedsDSLClass
@MutuallyExclusive({"provider", "providerType"})
@MutuallyExclusive({"field", "fieldId"})
@Inherited
@Documented
public @interface LinkTo {

    /**
     * The field of the target owner object to be used as the target for the link.
     */
    String field() default "";

    /**
     * If set use the field of the owner with a matching LinkSource annotation with the same id. Only one
     * of field and targetId can be used at most.
     */
    String fieldId() default "";

    /**
     * The owner of the link. By default, the owner of the annotated field's instance is used.
     */
    Class<? extends Closure<Object>> provider() default NoClosure.class;

    /** If set, the owner is determined by walking the owner hierarchy up until the given type is found. */
    Class<?> providerType() default Object.class;

    /**
     * If set, determines the strategy to determine which field of the provider is to be used as the link source.
     * FIELD_NAME: use the field with the same name as the annotated field, i.e. if the annotated field is called
     * 'admin', the field 'admin' of the provider is used.
     * INSTANCE_NAME: use the field with the same name as the instance name of the annotated field's owner, i.e. the
     * name of the field of the annotated field's classes owner pointing to the instance of the annotated field's container.
     * Can only be set together with one of provider or providerType.
     */
    @AlsoNeeds({"provider", "providerType"})
    Strategy strategy() default Strategy.AUTO;

    /**
     * If set, is added to automatically determined names (i.e. FIELD_NAME or INSTANCE_NAME).
     */
    String nameSuffix() default "";

    enum Strategy {
        AUTO,
        FIELD_NAME,
        INSTANCE_NAME
    }
}
