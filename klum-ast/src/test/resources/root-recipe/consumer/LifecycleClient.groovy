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
package fixture.consumer

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import fixture.lifecycle.Application
import fixture.lifecycle.Application_DSL
import fixture.lifecycle.Environment
import fixture.lifecycle.Service
import groovy.transform.CompileStatic

/** Consumer setup takes already-parsed YAML mappings; no parser or Environment selection belongs to KlumAST. */
@CompileStatic
class LifecycleClient {
    static void qualify() {
        Map<String, Object> environmentDocument = [region: 'eu', endpoint: 'https://eu.example.test']
        Environment eu = Environment.Create.FromStruct(environmentDocument, 'environment-eu.yaml')
        Environment us = Environment.Create.FromStruct(
                [region: 'us', endpoint: 'https://us.example.test'], 'environment-us.yaml')
        assert Environment.validations == 2
        assert KlumObjectSupport.of(eu).structure.owningRelationship.empty
        List<String> environmentPhases = new ArrayList<>(eu.phases)
        Map<String, Object> applicationDocument = [name: 'orders', image: 'input:v1',
                services: [[name: 'api'], [name: 'worker']]]

        // A missing setup overlay fails at the normal root PostApply, after the Struct children were configured.
        boolean missingContextRejected = false
        try {
            Application.Create.FromStruct(applicationDocument, 'orders.yaml')
        } catch (KlumModelException failure) {
            missingContextRejected = true
            assert failure.cause instanceof AssertionError
            assert failure.cause.message.contains('consumer overlay must supply the completed Environment')
        }
        assert missingContextRejected
        assert Application.validations == 0
        assert Service.validations == 0

        // Setup owns the completed Environment. The only context route is the ordinary root LINK configurator.
        Application orders = load(applicationDocument, 'orders.yaml', eu)
        Application repeated = load(applicationDocument, 'orders-again.yaml', eu)
        Application overseas = load(applicationDocument, 'orders-us.yaml', us)
        assert orders.name == 'orders'
        assert !orders.is(repeated)
        assert orders.environment.is(eu)
        assert repeated.environment.is(eu)
        assert overseas.environment.is(us)
        assert orders.services*.name == ['api', 'worker']
        assert orders.services.every { Service service ->
            service.application.is(orders) && service.environment.is(eu) &&
                    service.endpoint == eu.endpoint && service.image == 'overlay:v2'
        }
        assert overseas.services.every { Service service -> service.environment.is(us) && service.endpoint == us.endpoint }
        assert Application.validations == 3
        assert Service.validations == 6
        assert eu.phases == environmentPhases
        assert us.phases == environmentPhases
        assert Environment.validations == 2
        assert KlumObjectSupport.of(eu).structure.owningRelationship.empty
        assert applicationDocument.image == 'input:v1'
        assert !applicationDocument.containsKey('environment')
    }

    private static Application load(Map<String, Object> document, String sourceName, Environment selectedEnvironment) {
        Application_DSL.Builder<?> active
        Application result = Application.Create.FromStruct(document, sourceName) {
            active = delegate as Application_DSL.Builder<?>
            assert image == 'input:v1'
            assert services.every { it.phases == ['create', 'apply'] }
            environment selectedEnvironment
            image 'overlay:v2'
        }
        boolean sealedMutationRejected = false
        try {
            active.image('stale')
        } catch (KlumModelException ignored) {
            sealedMutationRejected = true
        }
        assert sealedMutationRejected
        assert result.image == 'overlay:v2'
        result
    }
}
