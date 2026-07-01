package com.github.wechat.ilink.bot.farm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {

    @Test
    void farmPlot_plant_setsStage() {
        FarmPlot plot = new FarmPlot(0);
        plot.plant("wheat");
        assertEquals("wheat", plot.getCropType());
        assertEquals(CropStage.SEED, plot.getStage());
        assertNotNull(plot.getPlantedAt());
    }

    @Test
    void farmPlot_harvest_clearsPlot() {
        FarmPlot plot = new FarmPlot(0);
        plot.plant("wheat");
        plot.setStage(CropStage.MATURE);
        plot.harvest();
        assertEquals(CropStage.EMPTY, plot.getStage());
        assertNull(plot.getCropType());
    }

    @Test
    void farmPlot_clear_resetsAll() {
        FarmPlot plot = new FarmPlot(0);
        plot.plant("wheat");
        plot.setHasPest(true);
        plot.setHasWeed(true);
        plot.clear();
        assertEquals(CropStage.EMPTY, plot.getStage());
        assertFalse(plot.isHasPest());
        assertFalse(plot.isHasWeed());
    }

    @Test
    void farmPlot_isEmpty_whenEmpty() {
        FarmPlot plot = new FarmPlot(0);
        assertTrue(plot.isEmpty());
    }

    @Test
    void farmPlot_isEmpty_whenWithered() {
        FarmPlot plot = new FarmPlot(0);
        plot.plant("wheat");
        plot.setStage(CropStage.WITHERED);
        assertTrue(plot.isEmpty());
    }

    @Test
    void inventory_seedOperations() {
        Inventory inv = new Inventory();
        inv.addSeed("wheat", 5);
        assertEquals(5, inv.getSeedCount("wheat"));
        assertTrue(inv.useSeed("wheat"));
        assertEquals(4, inv.getSeedCount("wheat"));
        assertFalse(inv.useSeed("nonexist"));
        assertEquals(4, inv.totalSeedCount());
    }

    @Test
    void inventory_produceOperations() {
        Inventory inv = new Inventory();
        inv.addProduce("corn", 10);
        assertEquals(10, inv.getProduceCount("corn"));
        assertTrue(inv.useProduce("corn", 3));
        assertEquals(7, inv.getProduceCount("corn"));
        assertFalse(inv.useProduce("corn", 999));
        assertEquals(7, inv.totalProduceCount());
    }

    @Test
    void inventory_toolOperations() {
        Inventory inv = new Inventory();
        inv.addTool("fertilizer", 2);
        assertEquals(2, inv.getToolCount("fertilizer"));
        assertTrue(inv.useTool("fertilizer"));
        assertEquals(1, inv.getToolCount("fertilizer"));
        assertFalse(inv.useTool("nonexist"));
    }

    @Test
    void inventory_useLastSeed_removesKey() {
        Inventory inv = new Inventory();
        inv.addSeed("wheat", 1);
        inv.useSeed("wheat");
        assertEquals(0, inv.getSeedCount("wheat"));
    }

    @Test
    void cropRegistry_getReturnsCrop() {
        Crop crop = CropRegistry.get("wheat");
        assertNotNull(crop);
        assertEquals("小麦", crop.getName());
        assertEquals(10, crop.getBuyPrice());
    }

    @Test
    void cropRegistry_getByName_returnsCrop() {
        Crop crop = CropRegistry.getByName("小麦");
        assertNotNull(crop);
        assertEquals("wheat", crop.getKey());
    }

    @Test
    void cropRegistry_getByName_englishKey() {
        Crop crop = CropRegistry.getByName("wheat");
        assertNotNull(crop);
    }

    @Test
    void cropRegistry_all_returnsAllCrops() {
        assertEquals(6, CropRegistry.all().size());
    }

    @Test
    void weather_today_returnsWeather() {
        Weather w = Weather.today();
        assertNotNull(w);
        assertNotNull(w.getName());
    }
}
