package example

import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.KlumBuilder
import java.util.function.Supplier

@CompileStatic
class ConfiguredModel implements Supplier<EnvironmentApi> {
    EnvironmentApi get() {
        CustomerEnvironment.Create.With {
            billing('billing') { image 'billing:1' }
            inventory('inventory') { image 'inventory:2' }
        }
    }

    @CompileStatic
    static EnvironmentApi staticBoundary() { CustomerEnvironment.Create.With() }

    static void main(String[] args) {
        assert staticBoundary() instanceof EnvironmentApi
        EnvironmentApi result = new ConfiguredModel().get()
        assert result.applications.keySet() == ['billing', 'inventory'] as Set
        assert result.applications.billing.image == 'billing:1'
        assert result.applications.inventory.name == 'inventory'
        println "MODEL_OK ${result.applications.keySet()} ${result.applications.billing.image}"
        println "RUNTIME_ORIGIN ${KlumBuilder.protectionDomain.codeSource.location}"
    }
}
