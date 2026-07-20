package onboarding.smarthome.api

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Key
import com.blackbuild.groovy.configdsl.transform.Required
import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate
import com.blackbuild.klum.ast.util.layer3.annotations.Cluster

@DSL
abstract class Home {

    @Key String name
    @AutoCreate @Cluster Map<String, Area> areas
}

@DSL
abstract class Area {

    String displayName
    @AutoCreate @Cluster Map<String, Window> windows
    List<Device> devices
}

@DSL
abstract class Room extends Area { }

@DSL
abstract class HeatedRoom extends Room {

    @Required Thermostat thermostat
}

@DSL
abstract class Window {

    String displayName
    WindowSensor windowSensor
}

@DSL
abstract class Device { }

@DSL
class GenericDevice extends Device {

    String deviceId
    String kind
    String label
}

@DSL
abstract class Thermostat extends Device {

    BigDecimal targetTemperature
}

@DSL
class TadoThermostat extends Thermostat {

    String deviceId
}

@DSL
class HomematicThermostat extends Thermostat {

    int channel
    String serialNumber
}

@DSL
abstract class WindowSensor extends Device { }

@DSL
class HomematicWindowSensor extends WindowSensor {

    int channel
    String serialNumber
}

@DSL
abstract class SmokeDetector extends Device { }

@DSL
class HomematicSmokeDetector extends SmokeDetector {

    String serialNumber
}
