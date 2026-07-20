package onboarding.smarthome.model

import onboarding.smarthome.api.GenericDevice
import onboarding.smarthome.api.HomematicSmokeDetector
import onboarding.smarthome.api.HomematicThermostat
import onboarding.smarthome.api.HomematicWindowSensor
import onboarding.smarthome.api.TadoThermostat
import onboarding.smarthome.schema.CityFlat

CityFlat.Create.With('city-flat') {
    kitchen {
        thermostat(HomematicThermostat) {
            serialNumber 'NEQ1234567'
            channel 1
            targetTemperature 19.5
        }
        smokeDetector(HomematicSmokeDetector) {
            serialNumber 'NEQ7654321'
        }
        street {
            windowSensor(HomematicWindowSensor) {
                serialNumber 'NEQ1111111'
                channel 2
            }
        }
        devices {
            device(GenericDevice) {
                deviceId 'kitchen-coffee'
                kind 'coffee-machine'
                label 'Coffee machine'
            }
        }
    }
    livingRoom {
        thermostat(HomematicThermostat) {
            serialNumber 'NEQ2222222'
            channel 1
            targetTemperature 20.0
        }
        garden { }
    }
    mainBedroom {
        thermostat(TadoThermostat) {
            deviceId 'VA1234567890'
            targetTemperature 18.0
        }
        garden {
            windowSensor(HomematicWindowSensor) {
                serialNumber 'NEQ3333333'
                channel 3
            }
        }
        devices {
            device(GenericDevice) {
                deviceId 'bedroom-reading-light'
                kind 'light'
                label 'Reading light'
            }
        }
    }
}
