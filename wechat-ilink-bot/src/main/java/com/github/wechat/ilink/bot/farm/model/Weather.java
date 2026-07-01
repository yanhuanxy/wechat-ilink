package com.github.wechat.ilink.bot.farm.model;

public enum Weather {
    SUNNY("晴天", "所有作物生长速度 +10%", 0.1),
    CLOUDY("多云", "正常生长", 0.0),
    RAINY("雨天", "自动浇水一次", 0.0),
    STORMY("暴风雨", "10%几率作物受害", -0.1);

    private final String name;
    private final String effect;
    private final double growBonus;

    Weather(String name, String effect, double growBonus) {
        this.name = name;
        this.effect = effect;
        this.growBonus = growBonus;
    }

    public String getName() { return name; }
    public String getEffect() { return effect; }
    public double getGrowBonus() { return growBonus; }

    public static Weather today() {
        long daySeed = System.currentTimeMillis() / (24 * 60 * 60 * 1000);
        int idx = (int) (daySeed % values().length);
        return values()[idx];
    }
}
