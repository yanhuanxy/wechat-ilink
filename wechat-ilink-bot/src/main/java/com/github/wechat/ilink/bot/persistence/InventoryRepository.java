package com.github.wechat.ilink.bot.persistence;

import com.github.wechat.ilink.bot.farm.model.Inventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public class InventoryRepository {

    private static final Logger log = LoggerFactory.getLogger(InventoryRepository.class);
    private final DatabaseManager dbManager;

    public InventoryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public Inventory findByUserId(String userId) {
        Inventory inventory = new Inventory();
        String sql = "SELECT * FROM inventory WHERE user_id = ?";
        try (PreparedStatement ps = dbManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String itemType = rs.getString("item_type");
                String itemKey = rs.getString("item_key");
                int quantity = rs.getInt("quantity");
                Map<String, Integer> target = getTargetMap(inventory, itemType);
                if (target != null) {
                    target.put(itemKey, quantity);
                }
            }
        } catch (Exception e) {
            log.error("查询背包失败: userId={}", userId, e);
        }
        return inventory;
    }

    public void replaceByUserId(String userId, Inventory inventory) {
        String deleteSql = "DELETE FROM inventory WHERE user_id = ?";
        String insertSql = "INSERT INTO inventory (user_id, item_type, item_key, quantity) VALUES (?, ?, ?, ?)";
        try {
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(deleteSql)) {
                ps.setString(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = dbManager.getConnection().prepareStatement(insertSql)) {
                writeItems(ps, userId, "SEED", inventory.getSeeds());
                writeItems(ps, userId, "PRODUCE", inventory.getProduce());
                writeItems(ps, userId, "TOOL", inventory.getTools());
                ps.executeBatch();
            }
        } catch (Exception e) {
            log.error("保存背包失败: userId={}", userId, e);
        }
    }

    private void writeItems(PreparedStatement ps, String userId, String type, Map<String, Integer> items) throws Exception {
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (entry.getValue() > 0) {
                ps.setString(1, userId);
                ps.setString(2, type);
                ps.setString(3, entry.getKey());
                ps.setInt(4, entry.getValue());
                ps.addBatch();
            }
        }
    }

    private Map<String, Integer> getTargetMap(Inventory inventory, String itemType) {
        switch (itemType) {
            case "SEED": return inventory.getSeeds();
            case "PRODUCE": return inventory.getProduce();
            case "TOOL": return inventory.getTools();
            default: return null;
        }
    }
}
