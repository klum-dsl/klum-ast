# Smart-home field-test brief

A building-management team needs a completed model of one small apartment. Kitchen, LivingRoom, MainBedroom, and their garden/street Windows are fixed floorplan facts. The installed devices and provider connection identities can change more often. A later target may be a dashboard, automation hub, or energy report, but no target format is authoritative today.

The team has one generic client and one Model Writer who configures a sample apartment. Heated rooms require a thermostat, the Kitchen requires a smoke detector, and Window sensors are optional. Labels are stable Schema defaults. The client needs generic rooms and Windows, but must not need the concrete room Schema classes or know which provider configures a device.

This brief intentionally omits live temperature state, provider runtime behavior, target-specific transport formats, OpenHAB generation, and an opinion about future Layer 3 extensions.
