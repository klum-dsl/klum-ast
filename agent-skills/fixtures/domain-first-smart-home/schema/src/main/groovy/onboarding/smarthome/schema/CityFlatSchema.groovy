package onboarding.smarthome.schema

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.groovy.configdsl.transform.Required
import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues
import onboarding.smarthome.api.HeatedRoom
import onboarding.smarthome.api.Home
import onboarding.smarthome.api.SmokeDetector
import onboarding.smarthome.api.Window

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE, ElementType.FIELD])
@DefaultValues(valueTarget = 'displayName')
@interface DisplayName {

    String value()
}

@DSL
class CityFlat extends Home {

    Kitchen kitchen
    LivingRoom livingRoom
    MainBedroom mainBedroom
}

@DSL
@DisplayName('Kitchen')
class Kitchen extends HeatedRoom {

    StreetWindow street
    @Required SmokeDetector smokeDetector
}

@DSL
@DisplayName('Living room')
class LivingRoom extends HeatedRoom {

    GardenWindow garden
}

@DSL
@DisplayName('Main bedroom')
class MainBedroom extends HeatedRoom {

    GardenWindow garden
}

@DSL
@DisplayName('Garden window')
class GardenWindow extends Window { }

@DSL
@DisplayName('Street window')
class StreetWindow extends Window { }
