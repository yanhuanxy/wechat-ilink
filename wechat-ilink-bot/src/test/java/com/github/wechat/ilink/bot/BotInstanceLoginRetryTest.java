package com.github.wechat.ilink.bot;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class BotInstanceLoginRetryTest {

    @Test
    void describeLoginFailure_qrCodeExpired_returnsChinese() {
        Throwable t = new ExecutionException(
                new RuntimeException(new java.io.IOException("qrcode expired")));

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("二维码过期", reason);
    }

    @Test
    void describeLoginFailure_loginTimeout_returnsChinese() {
        Throwable t = new RuntimeException("login timeout");

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("登录超时", reason);
    }

    @Test
    void describeLoginFailure_loginCancelled_returnsChinese() {
        Throwable t = new RuntimeException("login cancelled");

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("登录取消", reason);
    }

    @Test
    void describeLoginFailure_networkError_returnsChinese() {
        Throwable t = new ExecutionException(new RuntimeException("login polling failed"));

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("网络错误", reason);
    }

    @Test
    void describeLoginFailure_unknownException_returnsClassName() {
        Throwable t = new IllegalStateException("something else");

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("IllegalStateException", reason);
    }

    @Test
    void describeLoginFailure_nullMessage_returnsClassName() {
        Throwable t = new NullPointerException();

        String reason = BotInstance.describeLoginFailure(t);

        assertEquals("NullPointerException", reason);
    }
}
