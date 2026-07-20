package onboarding.smarthome.client;

import onboarding.smarthome.api.Home;
import onboarding.smarthome.api.Window;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A deliberately small Client Developer example. Its project depends only on the Domain API.
 */
public final class WindowStateClient {

    public Map<String, String> readWindowStates(Home home, WindowStateService windowStateService) {
        Map<String, String> states = new LinkedHashMap<>();
        home.getRooms().forEach((roomName, room) ->
                room.getWindows().forEach((windowName, window) ->
                        states.put(roomName + "." + windowName, windowStateService.stateFor(window))));
        return states;
    }
}
