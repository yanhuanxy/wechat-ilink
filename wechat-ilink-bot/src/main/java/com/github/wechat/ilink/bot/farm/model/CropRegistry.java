package com.github.wechat.ilink.bot.farm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CropRegistry {

    private static final Map<String, Crop> CROPS = new LinkedHashMap<String, Crop>();

    static {
        register(new Crop("wheat", "小麦", 10, 25, 10, 5, 10, "🌾"));
        register(new Crop("carrot", "胡萝卜", 20, 50, 20, 4, 20, "🥕"));
        register(new Crop("tomato", "番茄", 30, 80, 30, 3, 30, "🍅"));
        register(new Crop("corn", "玉米", 40, 100, 40, 6, 40, "🌽"));
        register(new Crop("strawberry", "草莓", 60, 150, 50, 2, 50, "🍓"));
        register(new Crop("watermelon", "西瓜", 100, 280, 60, 1, 80, "🍉"));
    }

    private static void register(Crop crop) {
        CROPS.put(crop.getKey(), crop);
    }

    public static Crop get(String key) {
        return CROPS.get(key);
    }

    public static Crop getByName(String name) {
        for (Crop crop : CROPS.values()) {
            if (crop.getName().equals(name) || crop.getKey().equalsIgnoreCase(name)) {
                return crop;
            }
        }
        return null;
    }

    public static List<Crop> all() {
        return Collections.unmodifiableList(new ArrayList<Crop>(CROPS.values()));
    }
}
