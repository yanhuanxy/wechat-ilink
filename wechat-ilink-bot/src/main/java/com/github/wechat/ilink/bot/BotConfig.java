package com.github.wechat.ilink.bot;

public class BotConfig {

    private String name;
    private String routeTag;

    public BotConfig() {
    }

    public BotConfig(String name, String routeTag) {
        this.name = name;
        this.routeTag = routeTag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRouteTag() {
        return routeTag;
    }

    public void setRouteTag(String routeTag) {
        this.routeTag = routeTag;
    }
}
