package com.github.wechat.ilink.bot.farm.model;

import java.util.HashMap;
import java.util.Map;

public class Inventory {

    private final Map<String, Integer> seeds = new HashMap<String, Integer>();
    private final Map<String, Integer> produce = new HashMap<String, Integer>();
    private final Map<String, Integer> tools = new HashMap<String, Integer>();

    public Map<String, Integer> getSeeds() { return seeds; }
    public Map<String, Integer> getProduce() { return produce; }
    public Map<String, Integer> getTools() { return tools; }

    public int getSeedCount(String cropKey) {
        return seeds.containsKey(cropKey) ? seeds.get(cropKey) : 0;
    }

    public void addSeed(String cropKey, int amount) {
        seeds.put(cropKey, getSeedCount(cropKey) + amount);
    }

    public boolean useSeed(String cropKey) {
        if (getSeedCount(cropKey) <= 0) {
            return false;
        }
        seeds.put(cropKey, getSeedCount(cropKey) - 1);
        if (seeds.get(cropKey) <= 0) {
            seeds.remove(cropKey);
        }
        return true;
    }

    public int getProduceCount(String cropKey) {
        return produce.containsKey(cropKey) ? produce.get(cropKey) : 0;
    }

    public void addProduce(String cropKey, int amount) {
        produce.put(cropKey, getProduceCount(cropKey) + amount);
    }

    public boolean useProduce(String cropKey, int amount) {
        if (getProduceCount(cropKey) < amount) {
            return false;
        }
        produce.put(cropKey, getProduceCount(cropKey) - amount);
        if (produce.get(cropKey) <= 0) {
            produce.remove(cropKey);
        }
        return true;
    }

    public int getToolCount(String toolKey) {
        return tools.containsKey(toolKey) ? tools.get(toolKey) : 0;
    }

    public void addTool(String toolKey, int amount) {
        tools.put(toolKey, getToolCount(toolKey) + amount);
    }

    public boolean useTool(String toolKey) {
        if (getToolCount(toolKey) <= 0) {
            return false;
        }
        tools.put(toolKey, getToolCount(toolKey) - 1);
        if (tools.get(toolKey) <= 0) {
            tools.remove(toolKey);
        }
        return true;
    }

    public int totalProduceCount() {
        int total = 0;
        for (int count : produce.values()) {
            total += count;
        }
        return total;
    }

    public int totalSeedCount() {
        int total = 0;
        for (int count : seeds.values()) {
            total += count;
        }
        return total;
    }

    public String summary() {
        int total = totalSeedCount() + totalProduceCount();
        int types = 0;
        for (Map.Entry<String, Integer> e : seeds.entrySet()) {
            if (e.getValue() > 0) types++;
        }
        for (Map.Entry<String, Integer> e : produce.entrySet()) {
            if (e.getValue() > 0) types++;
        }
        return total + "件物品" + (types > 0 ? "(" + types + "种)" : "");
    }
}
