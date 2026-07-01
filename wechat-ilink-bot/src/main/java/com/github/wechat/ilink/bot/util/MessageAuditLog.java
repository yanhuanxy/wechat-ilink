package com.github.wechat.ilink.bot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 微信收发消息审计日志。
 *
 * <p>通过 MDC key {@code userId} 配合 logback 的 SiftingAppender，将每个用户的收发内容
 * 落地到 {@code logs/io/<userId>/io.<date>.log}。仅记录消息文本，<b>绝不记录 botToken / contextToken
 * 等敏感凭证</b>。</p>
 */
public final class MessageAuditLog {

    /** 专用 logger，名称与 logback.xml 中的 {@code <logger name="MSG_IO">} 对应。 */
    private static final Logger LOG = LoggerFactory.getLogger("MSG_IO");

    public static final String MDC_USER_ID = "userId";
    private static final String DEFAULT_USER = "unknown";

    private MessageAuditLog() {
    }

    /** 记录一条入向（用户 → bot）消息。 */
    public static void inbound(String userId, String text) {
        write(userId, "IN ", text);
    }

    /**
     * 记录一条出向（bot → 用户）消息。
     *
     * @param kind 内容类型，如 {@code text} / {@code image} / {@code file} / {@code video}
     */
    public static void outbound(String userId, String kind, String text) {
        write(userId, "OUT[" + kind + "]", text);
    }

    private static void write(String userId, String direction, String text) {
        MDC.put(MDC_USER_ID, sanitize(userId));
        try {
            LOG.info("{} {}", direction, text == null ? "" : text);
        } finally {
            MDC.remove(MDC_USER_ID);
        }
    }

    /** 将 userId 规整为文件系统安全的目录名，防止 {@code /}、{@code :} 等破坏路径。 */
    static String sanitize(String userId) {
        if (userId == null || userId.isEmpty()) {
            return DEFAULT_USER;
        }
        return userId.replaceAll("[^A-Za-z0-9_-]", "_");
    }
}
