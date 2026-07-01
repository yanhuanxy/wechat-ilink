package com.github.wechat.ilink.bot.farm.model;

public class Crop {

    private final String key;
    private final String name;
    private final int buyPrice;
    private final int sellPrice;
    private final int growTimeMinutes;
    private final int yieldAmount;
    private final int expReward;
    private final String emoji;

    public Crop(String key, String name, int buyPrice, int sellPrice,
                int growTimeMinutes, int yieldAmount, int expReward, String emoji) {
        this.key = key;
        this.name = name;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.growTimeMinutes = growTimeMinutes;
        this.yieldAmount = yieldAmount;
        this.expReward = expReward;
        this.emoji = emoji;
    }

    public String getKey() { return key; }
    public String getName() { return name; }
    public int getBuyPrice() { return buyPrice; }
    public int getSellPrice() { return sellPrice; }
    public int getGrowTimeMinutes() { return growTimeMinutes; }
    public int getYieldAmount() { return yieldAmount; }
    public int getExpReward() { return expReward; }
    public String getEmoji() { return emoji; }
}
