package com.github.wechat.ilink.bot.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActionRankRepository {

    private static final Logger log = LoggerFactory.getLogger(ActionRankRepository.class);
    private final DatabaseManager dbManager;

    public ActionRankRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void incrementScore(String actionType, String userId, int delta) {
        String sql = "INSERT INTO action_rank (action_type, user_id, score, updated_at) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(action_type, user_id) DO UPDATE SET score = score + ?, updated_at = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            String now = now();
            ps.setString(1, actionType);
            ps.setString(2, userId);
            ps.setInt(3, delta);
            ps.setString(4, now);
            ps.setInt(5, delta);
            ps.setString(6, now);
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("更新排行失败: actionType={}, userId={}", actionType, userId, e);
        }
    }

    public Map<String, Integer> getTopScores(String actionType, int limit) {
        String sql = "SELECT user_id, score FROM action_rank WHERE action_type = ? ORDER BY score DESC LIMIT ?";
        Map<String, Integer> ranking = new LinkedHashMap<String, Integer>();
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, actionType);
            ps.setInt(2, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ranking.put(rs.getString("user_id"), rs.getInt("score"));
            }
        } catch (Exception e) {
            log.error("查询排行失败: actionType={}", actionType, e);
        }
        return ranking;
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    }
}
