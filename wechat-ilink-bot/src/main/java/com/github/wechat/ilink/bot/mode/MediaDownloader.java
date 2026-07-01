package com.github.wechat.ilink.bot.mode;

import com.github.wechat.ilink.sdk.core.model.MessageItem;

import java.io.IOException;

/**
 * 入向媒体下载 seam（对称于 {@link ModeSender} 的出向发送）。
 * 由持有 {@code ILinkClient} 的 {@code GameBot} 实现；SDK 内部完成 CDN 下载 + AES 解密，返回明文字节。
 */
public interface MediaDownloader {

    byte[] downloadImage(MessageItem item) throws IOException;

    byte[] downloadFile(MessageItem item) throws IOException;
}
