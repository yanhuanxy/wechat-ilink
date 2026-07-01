package com.github.wechat.ilink.bot.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeGeneratorTest {

    @Test
    void generatePng_validUrl_returnsPngBytes() throws Exception {
        String url = "https://liteapp.weixin.qq.com/q/test?qrcode=abc&bot_type=3";
        byte[] png = QrCodeGenerator.generatePng(url);

        assertNotNull(png);
        assertTrue(png.length > 100);
        assertEquals(0x89, png[0] & 0xFF);
        assertEquals(0x50, png[1] & 0xFF);
        assertEquals(0x4E, png[2] & 0xFF);
        assertEquals(0x47, png[3] & 0xFF);
    }

    @Test
    void generatePng_nullContent_throwsException() {
        assertThrows(Exception.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                QrCodeGenerator.generatePng(null);
            }
        });
    }

    @Test
    void generatePng_emptyString_throwsException() {
        assertThrows(IllegalArgumentException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() throws Throwable {
                QrCodeGenerator.generatePng("");
            }
        });
    }
}
