package com.github.wechat.ilink.bot.farm.handler;

import com.github.wechat.ilink.bot.command.CommandResult;
import com.github.wechat.ilink.bot.command.QrCodeProvider;
import com.github.wechat.ilink.bot.session.PlayerSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareCommandTest {

    private PlayerSession session;
    private StubQrCodeProvider provider;
    private ShareCommand command;

    @BeforeEach
    void setUp() {
        session = new PlayerSession("testUser");
        provider = new StubQrCodeProvider("https://liteapp.weixin.qq.com/q/test?qrcode=abc");
        command = new ShareCommand(provider);
    }

    @Test
    void execute_validQrCode_returnsSuccessWithImageData() {
        CommandResult result = command.execute(session, new String[0]);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("二维码"));
        Object imageData = result.getData().get(CommandResult.IMAGE_DATA_KEY);
        assertTrue(imageData instanceof byte[]);
        byte[] png = (byte[]) imageData;
        assertTrue(png.length > 100);
        assertEquals(0x89, png[0] & 0xFF);
        assertEquals(0x50, png[1] & 0xFF);
    }

    @Test
    void execute_qrCodeNull_returnsError() {
        provider = new StubQrCodeProvider(null);
        command = new ShareCommand(provider);

        CommandResult result = command.execute(session, new String[0]);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("不可用"));
    }

    @Test
    void execute_qrCodeEmpty_returnsError() {
        provider = new StubQrCodeProvider("");
        command = new ShareCommand(provider);

        CommandResult result = command.execute(session, new String[0]);

        assertFalse(result.isSuccess());
    }

    @Test
    void execute_cooldownActive_returnsError() {
        command.execute(session, new String[0]);

        CommandResult result = command.execute(session, new String[0]);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("频繁"));
    }

    @Test
    void execute_differentUsers_noCooldown() {
        PlayerSession user1 = new PlayerSession("user1");
        PlayerSession user2 = new PlayerSession("user2");

        CommandResult r1 = command.execute(user1, new String[0]);
        CommandResult r2 = command.execute(user2, new String[0]);

        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
    }

    private static class StubQrCodeProvider implements QrCodeProvider {
        private final String data;

        StubQrCodeProvider(String data) {
            this.data = data;
        }

        @Override
        public String getQrCodeUrl() {
            return data;
        }
    }
}
