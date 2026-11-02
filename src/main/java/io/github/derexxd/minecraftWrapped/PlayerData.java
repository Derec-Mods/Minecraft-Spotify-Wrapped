package io.github.derexxd.minecraftWrapped;

import java.util.Map;

public class PlayerData {

    String uuid;
    String name;
    Map<String, Long> counters;

    PlayerData(String uuid, String name, Map<String, Long> counters) {
        this.uuid = uuid;
        this.name = name;
        this.counters = counters;
    }
}

