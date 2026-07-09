package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.engine.GameEngine;
import com.github.wechat.ilink.bot.engine.ResponseRenderer;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FarmMode implements BotMode {

    private static final Logger log = LoggerFactory.getLogger(FarmMode.class);

    @Override
    public BotModeType type() {
        return BotModeType.FARM;
    }

    @Override
    public ModeOutcome handleText(ModeContext ctx, PlayerSession session, String text) {
        String userId = session.getUserId();
        String commandText = text.substring(1).trim();
        if (commandText.isEmpty()) {
            try {
                ctx.sender().sendText(userId, "输入『帮助』查看可用命令");
            } catch (Exception ignored) {
            }
            return ModeOutcome.handled();
        }

        ModeSender sender = ctx.sender();
        GameEngine engine = ctx.engine();
        ResponseRenderer renderer = ctx.renderer();

        try {
            CommandResult result = engine.dispatch(userId, commandText);

            Object imageBase64 = result.getData().get(CommandResult.IMAGE_DATA_KEY);
            if (imageBase64 instanceof byte[]) {
                sendImageResult(sender, userId, result, (byte[]) imageBase64);
            } else {
                String response = renderer.render(result);
                if (response != null && !response.isEmpty()) {
                    sender.sendText(userId, response);
                }
            }
        } catch (Exception e) {
            log.error("游戏处理出错, userId={}", userId, e);
            try {
                sender.sendText(userId, "出了点问题，输入'#帮助'查看可用命令");
            } catch (Exception ignored) {
            }
        }
        return ModeOutcome.handled();
    }

    private void sendImageResult(ModeSender sender, String userId,
                                 CommandResult result, byte[] imageBytes) {
        try {
            sender.sendImage(userId, imageBytes, "bot-qrcode.png", result.getMessage());
        } catch (Exception e) {
            log.error("发送二维码图片失败, userId={}", userId, e);
            try {
                sender.sendText(userId, result.getMessage());
            } catch (Exception ignored) {
            }
        }
    }
}
