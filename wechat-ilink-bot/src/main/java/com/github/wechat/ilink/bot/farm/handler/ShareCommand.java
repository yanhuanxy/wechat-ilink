package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.Command;
import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.command.QrCodeProvider;
import com.github.wechat.ilink.bot.session.PlayerSession;
import com.github.wechat.ilink.bot.util.QrCodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShareCommand implements Command {

    static final long COOLDOWN_MS = 60_000L;
    private static final Logger log = LoggerFactory.getLogger(ShareCommand.class);

    private final QrCodeProvider qrCodeProvider;
    private final ConcurrentHashMap<String, Long> lastShareTime;

    public ShareCommand(QrCodeProvider qrCodeProvider) {
        this.qrCodeProvider = qrCodeProvider;
        this.lastShareTime = new ConcurrentHashMap<String, Long>();
    }

    @Override
    public String name() {
        return "SHARE";
    }

    @Override
    public String description() {
        return "分享二维码";
    }

    @Override
    public CommandResult execute(PlayerSession session, String[] args) {
        String userId = session.getUserId();

        Long lastTime = lastShareTime.get(userId);
        long now = System.currentTimeMillis();
        if (lastTime != null && (now - lastTime) < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (now - lastTime)) / 1000L;
            return CommandResult.error("操作太频繁，请 " + remaining + " 秒后再试");
        }

        String qrCodeUrl = qrCodeProvider.getQrCodeUrl();
        if (qrCodeUrl == null || qrCodeUrl.isEmpty()) {
            return CommandResult.error("二维码暂不可用，请稍后再试");
        }

        lastShareTime.put(userId, now);
        Map<String, Object> data = new HashMap<String, Object>();
        try {
            byte[] bytes = QrCodeGenerator.generatePng(qrCodeUrl);
            data.put(CommandResult.IMAGE_DATA_KEY, bytes);
        }catch (Exception e) {
            log.error("构建base64二维码失败", e);
        }

        return CommandResult.success("扫描下方二维码添加好友，一起玩帮帮农场吧！", data);
    }
}
