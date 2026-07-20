package onboarding.smarthome.model

import com.blackbuild.klum.ast.util.KlumValidationException
import onboarding.smarthome.api.HomematicThermostat
import onboarding.smarthome.api.HomematicWindowSensor
import onboarding.smarthome.api.TadoThermostat
import onboarding.smarthome.client.WindowStateClient
import onboarding.smarthome.schema.CityFlat
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag

@Issue('471')
@Tag('documentary')
@See('https://github.com/klum-dsl/klum-ast/blob/master/wiki/Domain-First-Modeling.md#smart-home-journey')
class SmartHomeJourneyDocumentaryTest extends Specification {

    def 'loads a floorplan-specific Model and lets a generic client inspect every window'() {
        when: 'the Model Writer loads the registered model against the finished Schema'
        CityFlat home = CityFlat.Create.FromClasspath()

        then: 'the Schema supplies fixed areas, windows, and their default labels'
        home.areas.keySet() == ['kitchen', 'livingRoom', 'mainBedroom'] as Set
        home.mainBedroom.displayName == 'Main bedroom'
        home.mainBedroom.windows.keySet() == ['garden'] as Set
        home.kitchen.windows.keySet() == ['street'] as Set

        and: 'the Model chooses provider-specific Builders and mutable-in-practice identifiers'
        home.mainBedroom.thermostat instanceof TadoThermostat
        home.mainBedroom.thermostat.deviceId == 'VA1234567890'
        home.livingRoom.thermostat instanceof HomematicThermostat
        home.livingRoom.thermostat.serialNumber == 'NEQ2222222'
        home.mainBedroom.garden.windowSensor instanceof HomematicWindowSensor
        home.livingRoom.garden.windowSensor == null
        home.mainBedroom.devices*.label == ['Reading light']

        when: 'a Client Developer asks a speculative external system about generic API windows'
        Map<String, String> states = new WindowStateClient().readWindowStates(home, { window ->
            window.windowSensor ? 'closed' : 'unmanaged'
        })

        then: 'the client uses Cluster projections, not Schema or provider types'
        states == [
            'kitchen.street'      : 'closed',
            'livingRoom.garden'   : 'unmanaged',
            'mainBedroom.garden'  : 'closed'
        ]
    }

    def 'rejects a fixed heated room without the thermostat required by the floorplan'() {
        when:
        CityFlat.Create.With('incomplete-flat') { }

        then:
        thrown(KlumValidationException)
    }
}
