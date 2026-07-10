# 农场游戏设计

## 概述

帮帮农场是框架的首个游戏实现，提供 27 个命令，包括种植、收获、商店、背包、偷菜、排行榜、昵称、分享等功能。社交玩法（偷菜 / 排行 / 昵称）的设计见 [farm-social.md](farm-social.md)。

## 领域模型

### Crop（作物类型）

| 属性 | 说明 |
|------|------|
| name | 作物名称（小麦、胡萝卜等） |
| growTimeMinutes | 生长时间（分钟） |
| buyPrice | 种子购买价格 |
| sellPrice | 收获后出售价格 |
| stages | 生长阶段列表 |

### CropStage（生长阶段）

```
播种(SEED) → 发芽(SPROUT) → 生长(GROWING) → 成熟(MATURE) → 枯萎(WITHERED)
```

- 成熟后可收获
- 超时未收获会枯萎（plantedAt + growTimeMinutes + bufferTime）
- 浇水加速生长，施肥增加产量

### FarmPlot（地块）

| 属性 | 说明 |
|------|------|
| plotId | 地块编号 |
| crop | 当前作物（null 表示空地） |
| plantedAt | 种植时间戳 |
| wateredAt | 浇水时间戳 |
| fertilized | 是否施肥 |

### FarmGrid（地块网格）

- 6x6 = 36 块地块，初始解锁 4 块，其余通过升级解锁
- 通过 `FarmPlot[]` 数组管理

### Inventory（背包）

| 属性 | 说明 |
|------|------|
| items | 物品列表 Map<ItemType, Integer> |
| maxSize | 最大容量 |

### PlayerStats（玩家数据）

| 属性 | 说明 |
|------|------|
| coins | 金币 |
| level | 等级 |
| exp | 经验值 |
| consecutiveSignInDays | 连续签到天数 |
| lastSignInDate | 上次签到日期 |

## 命令映射（27 个）

### 基础信息
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `USER_INFO` | `我的信息`, `info`, `信息` | 无 | 查看玩家数据 |
| `VIEW_FARM` | `查看农场`, `农场`, `查看` | 无 | 查看农场布局 |
| `HELP` | `帮助`, `菜单` | 无 | 显示命令帮助菜单 |
| `SEED_SHOP` | `种子商店`, `商店` | 无 | 查看可购买的种子 |
| `COUPON_SHOP` | `点券商店`, `点券` | 无 | 查看点券商品 |

### 商店
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `BUY_SEED` | `购买`, `买种子`, `买` | <作物名> [x数量] | 购买种子，如 `#购买 小麦 x3` |

### 背包
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `FARM_BAG` | `农场背包`, `背包` | 无 | 查看农场背包 |
| `TOOL_BAG` | `道具背包`, `道具` | 无 | 查看道具背包 |

### 日常
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `CHECKIN` | `农场签到`, `签到`, `打卡` | 无 | 每日签到 |
| `WEATHER` | `农场天气查询`, `天气` | 无 | 查看今日天气 |

### 批量操作
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `PLANT_ALL` | `一键种植`, `种植` | <作物> | 在所有空地种植 |
| `HARVEST_ALL` | `一键收获`, `收获` | 无 | 收获所有成熟作物 |
| `SELL_ALL` | `一键卖菜`, `卖菜` | 无 | 出售所有收获物 |
| `CLEAR_ALL` | `一键锄地`, `锄地` | 无 | 清理所有枯萎作物 |
| `WATER_ALL` | `一键浇水`, `浇水` | 无 | 给所有作物浇水 |
| `PEST_ALL` | `一键除虫`, `除虫` | 无 | 给所有作物除虫 |
| `FERTILIZE` | `施肥` | <作物名> | 施肥，如 `#施肥 小麦` |

### 社交/排行
| 命令 | 别名 | 参数 | 说明 |
|------|------|------|------|
| `STEAL` | `偷菜`, `偷`, `偷菜查询` | 无 / `<序号>` | 全服随机池偷菜（两步交互），详见 [farm-social.md](farm-social.md) |
| `RENAME` | `改名`, `昵称`, `起名` | `<昵称>` | 设置农场昵称（榜单/偷菜展示） |
| `RANK_MENU` | `排行`, `排行榜` | 无 | 排行榜总入口 |
| `WEALTH_RANK` | `财富榜`, `金币榜` | 无 | 财富排行（按金币） |
| `LEVEL_RANK` | `等级榜` | 无 | 等级排行（按等级/经验） |
| `STEAL_RANK` | `偷菜榜`, `大盗榜` | 无 | 偷菜大盗排行 |
| `PEST_RANK` | `驱虫`, `驱虫排行` | 无 | 除虫排行榜 |
| `WEED_RANK` | `除草`, `除草排行` | 无 | 除草排行榜 |
| `WATER_RANK` | `浇水排行` | 无 | 浇水排行榜 |
| `SHARE` | `分享`, `分享二维码`, `邀请` | 无 | 生成邀请二维码（需 `QrCodeProvider`） |

## 时间机制

作物基于真实时间生长，采用惰性计算：

```java
public CropStage getCurrentStage(FarmPlot plot) {
    long elapsedMinutes = (System.currentTimeMillis() - plot.getPlantedAt()) / 60000;
    Crop crop = plot.getCrop();

    if (elapsedMinutes >= crop.getGrowTimeMinutes() + crop.getWitherBuffer()) {
        return CropStage.WITHERED;
    }
    if (elapsedMinutes >= crop.getGrowTimeMinutes()) {
        return CropStage.MATURE;
    }
    // 按比例计算阶段...
    double progress = (double) elapsedMinutes / crop.getGrowTimeMinutes();
    if (progress < 0.3) return CropStage.SEED;
    if (progress < 0.7) return CropStage.SPROUT;
    return CropStage.GROWING;
}
```

**不使用后台计时器线程**，玩家下次交互时计算当前状态。

## 作物表（示例）

| 作物 | 生长时间 | 购买价 | 出售价 |
|------|---------|--------|--------|
| 小麦 | 10 min | 10 | 25 |
| 胡萝卜 | 20 min | 20 | 50 |
| 番茄 | 30 min | 30 | 80 |
| 玉米 | 60 min | 50 | 120 |
| 南瓜 | 120 min | 100 | 250 |

## 经济系统

- 初始金币：500
- 收获获得金币 + 经验
- 升级解锁更多地块和作物
- 签到奖励金币和道具

## FarmRenderer

农场专属的渲染类，负责将 6x6 地块网格格式化为用户友好的文本：

```
╔════════════════════════╗
║     帮帮农场 - 我的土地     ║
╠════════════════════════╣
║ 1: 🌾 小麦 [生长中] 60%  ║
║ 2: 🥕 胡萝卜 [成熟] ✓    ║
║ 3: 🟫 空地              ║
║ 4: 🍅 番茄 [枯萎] ✗     ║
║ ... (6x6 = 36 块)       ║
╚════════════════════════╝
💰 金币: 1200  📦 背包: 5/20
```

`FarmRenderer` 作为独立的渲染类，由 `ResponseRenderer` 在检测到 `farmGrid` 数据时调用。

## 持久化

游戏数据通过 SQLite 持久化，由 `DatabaseManager` 管理：
- 玩家数据（PlayerStats）通过 `PlayerRepository` 读写
- 地块数据（FarmPlot）通过 `FarmPlotRepository` 读写
- 背包数据（Inventory）通过 `InventoryRepository` 读写
- 排行榜数据通过 `ActionRankRepository` 读写
- 会话缓存使用 `ConcurrentHashMap`，SQLite 为唯一数据源

`FarmGame` 构造器接收 `CommandRegistry` 和 `ActionRankRepository`，排行榜命令（PestRankCommand、WeedRankCommand、WaterRankCommand）需要 `ActionRankRepository` 进行数据查询和更新。
