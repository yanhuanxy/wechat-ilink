package com.github.wechat.ilink.bot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按用户维度的消息去重水位线仓库：记录每个用户已处理的最大 {@code message_id}。
 *
 * <p>用途：重启后 SDK 从持久化游标 resume 拉取时会重投最后一条已处理消息，
 * 据此水位线跳过 {@code message_id ≤ 水位线} 的重复投递，避免旧问题被重跑；
 * 离线期间真正的新消息（id 更大）仍会放行处理。</p>
 *
 * <p>纯 JDBC 实现，叠一层内存缓存加速读取与 max 计算。不依赖任何 SDK 类型。</p>
 */
public class MessageDedupRepository {

    private static final Logger log = LoggerFactory.getLogger(MessageDedupRepository.class);

    private final DatabaseManager dbManager;
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<String, Long>();

    public MessageDedupRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /** 返回该用户已处理的最大 message_id；无记录返回 {@link Long#MIN_VALUE}。 */
    public long getLastMessageId(String userId) {
        Long cached = cache.get(userId);
        if (cached != null) {
            return cached.longValue();
        }
        long loaded = load(userId);
        cache.put(userId, Long.valueOf(loaded));
        return loaded;
    }

    /** 仅当 messageId 大于当前水位线时上移水位线并落库（write-through，取 max，避免回退）。 */
    public void markProcessed(String userId, long messageId) {
        if (messageId <= getLastMessageId(userId)) {
            return;
        }
        cache.put(userId, Long.valueOf(messageId));
        String sql = "INSERT OR REPLACE INTO processed_message (user_id, last_message_id, updated_at) "
                + "VALUES (?, ?, ?)";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ps.setLong(2, messageId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.error("保存消息去重水位线失败: userId={}, messageId={}", userId, messageId, e);
        }
    }

    private long load(String userId) {
        String sql = "SELECT last_message_id FROM processed_message WHERE user_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("last_message_id");
                }
            }
        } catch (SQLException e) {
            log.error("加载消息去重水位线失败: userId={}", userId, e);
        }
        return Long.MIN_VALUE;
    }
}
