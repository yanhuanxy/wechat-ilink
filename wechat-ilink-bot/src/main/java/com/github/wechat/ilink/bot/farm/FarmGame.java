package com.github.wechat.ilink.bot.farm;

import com.github.wechat.ilink.bot.command.CommandRegistry;
import com.github.wechat.ilink.bot.command.QrCodeProvider;
import com.github.wechat.ilink.bot.farm.handler.BuySeedCommand;
import com.github.wechat.ilink.bot.farm.handler.CheckinCommand;
import com.github.wechat.ilink.bot.farm.handler.ClearAllCommand;
import com.github.wechat.ilink.bot.farm.handler.CouponShopCommand;
import com.github.wechat.ilink.bot.farm.handler.FarmBagCommand;
import com.github.wechat.ilink.bot.farm.handler.FertilizeCommand;
import com.github.wechat.ilink.bot.farm.handler.HarvestAllCommand;
import com.github.wechat.ilink.bot.farm.handler.HelpCommand;
import com.github.wechat.ilink.bot.farm.handler.PestAllCommand;
import com.github.wechat.ilink.bot.farm.handler.PestRankCommand;
import com.github.wechat.ilink.bot.farm.handler.PlantAllCommand;
import com.github.wechat.ilink.bot.farm.handler.SeedShopCommand;
import com.github.wechat.ilink.bot.farm.handler.SellAllCommand;
import com.github.wechat.ilink.bot.farm.handler.ShareCommand;
import com.github.wechat.ilink.bot.farm.handler.StealCheckCommand;
import com.github.wechat.ilink.bot.farm.handler.ToolBagCommand;
import com.github.wechat.ilink.bot.farm.handler.UserInfoCommand;
import com.github.wechat.ilink.bot.farm.handler.ViewFarmCommand;
import com.github.wechat.ilink.bot.farm.handler.WaterAllCommand;

import com.github.wechat.ilink.bot.farm.handler.WaterRankCommand;
import com.github.wechat.ilink.bot.farm.handler.WeatherCommand;
import com.github.wechat.ilink.bot.farm.handler.WeedRankCommand;
import com.github.wechat.ilink.bot.persistence.ActionRankRepository;

public class FarmGame {

    private final CommandRegistry registry;
    private final ActionRankRepository rankRepo;
    private final QrCodeProvider qrCodeProvider;

    public FarmGame(CommandRegistry registry, ActionRankRepository rankRepo, QrCodeProvider qrCodeProvider) {
        this.registry = registry;
        this.rankRepo = rankRepo;
        this.qrCodeProvider = qrCodeProvider;
    }

    public void registerCommands() {
        registry.register(new UserInfoCommand());
        registry.register(new ViewFarmCommand());
        registry.register(new SeedShopCommand());
        registry.register(new BuySeedCommand());
        registry.register(new CouponShopCommand());
        registry.register(new FarmBagCommand());
        registry.register(new ToolBagCommand());
        registry.register(new CheckinCommand());
        registry.register(new WeatherCommand());
        registry.register(new PlantAllCommand());
        registry.register(new HarvestAllCommand());
        registry.register(new SellAllCommand());
        registry.register(new ClearAllCommand());
        registry.register(new WaterAllCommand());
        registry.register(new PestAllCommand());
        registry.register(new FertilizeCommand());
        registry.register(new StealCheckCommand());
        registry.register(new PestRankCommand(rankRepo));
        registry.register(new WeedRankCommand(rankRepo));
        registry.register(new WaterRankCommand(rankRepo));
        registry.register(new HelpCommand());
        registry.register(new ShareCommand(qrCodeProvider));

        registry.registerAlias("我的信息", "USER_INFO");
        registry.registerAlias("info", "USER_INFO");
        registry.registerAlias("信息", "USER_INFO");
        registry.registerAlias("查看农场", "VIEW_FARM");
        registry.registerAlias("农场", "VIEW_FARM");
        registry.registerAlias("查看", "VIEW_FARM");
        registry.registerAlias("种子商店", "SEED_SHOP");
        registry.registerAlias("商店", "SEED_SHOP");
        registry.registerAlias("购买", "BUY_SEED");
        registry.registerAlias("买种子", "BUY_SEED");
        registry.registerAlias("买", "BUY_SEED");
        registry.registerAlias("点券商店", "COUPON_SHOP");
        registry.registerAlias("点券", "COUPON_SHOP");
        registry.registerAlias("农场背包", "FARM_BAG");
        registry.registerAlias("背包", "FARM_BAG");
        registry.registerAlias("道具背包", "TOOL_BAG");
        registry.registerAlias("道具", "TOOL_BAG");
        registry.registerAlias("农场签到", "CHECKIN");
        registry.registerAlias("签到", "CHECKIN");
        registry.registerAlias("打卡", "CHECKIN");
        registry.registerAlias("农场天气查询", "WEATHER");
        registry.registerAlias("天气", "WEATHER");
        registry.registerAlias("一键种植", "PLANT_ALL");
        registry.registerAlias("种植", "PLANT_ALL");
        registry.registerAlias("一键收获", "HARVEST_ALL");
        registry.registerAlias("收获", "HARVEST_ALL");
        registry.registerAlias("一键卖菜", "SELL_ALL");
        registry.registerAlias("卖菜", "SELL_ALL");
        registry.registerAlias("一键锄地", "CLEAR_ALL");
        registry.registerAlias("锄地", "CLEAR_ALL");
        registry.registerAlias("一键浇水", "WATER_ALL");
        registry.registerAlias("浇水", "WATER_ALL");
        registry.registerAlias("一键除虫", "PEST_ALL");
        registry.registerAlias("除虫", "PEST_ALL");
        registry.registerAlias("施肥", "FERTILIZE");
        registry.registerAlias("偷菜查询", "STEAL_CHECK");
        registry.registerAlias("偷菜", "STEAL_CHECK");
        registry.registerAlias("驱虫", "PEST_RANK");
        registry.registerAlias("除草", "WEED_RANK");
        registry.registerAlias("浇水排行", "WATER_RANK");
        registry.registerAlias("驱虫排行", "PEST_RANK");
        registry.registerAlias("除草排行", "WEED_RANK");
        registry.registerAlias("帮助", "HELP");
        registry.registerAlias("菜单", "HELP");
        registry.registerAlias("分享", "SHARE");
        registry.registerAlias("分享二维码", "SHARE");
        registry.registerAlias("邀请", "SHARE");
    }
}
