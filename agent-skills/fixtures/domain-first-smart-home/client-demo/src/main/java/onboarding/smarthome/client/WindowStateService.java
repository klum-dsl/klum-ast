package onboarding.smarthome.client;

import onboarding.smarthome.api.Window;

@FunctionalInterface
public interface WindowStateService {

    String stateFor(Window window);
}
