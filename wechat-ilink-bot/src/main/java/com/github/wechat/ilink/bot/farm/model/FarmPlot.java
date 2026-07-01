package com.github.wechat.ilink.bot.farm.model;

public class FarmPlot {

    private final int index;
    private String cropType;
    private CropStage stage;
    private int waterCount;
    private Long plantedAt;
    private boolean hasPest;
    private boolean hasWeed;

    public FarmPlot(int index) {
        this.index = index;
        this.stage = CropStage.EMPTY;
        this.waterCount = 0;
    }

    public int getIndex() { return index; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public CropStage getStage() { return stage; }
    public void setStage(CropStage stage) { this.stage = stage; }

    public int getWaterCount() { return waterCount; }
    public void setWaterCount(int waterCount) { this.waterCount = waterCount; }
    public void addWater() { this.waterCount++; }

    public Long getPlantedAt() { return plantedAt; }
    public void setPlantedAt(Long plantedAt) { this.plantedAt = plantedAt; }

    public boolean isHasPest() { return hasPest; }
    public void setHasPest(boolean hasPest) { this.hasPest = hasPest; }

    public boolean isHasWeed() { return hasWeed; }
    public void setHasWeed(boolean hasWeed) { this.hasWeed = hasWeed; }

    public boolean isEmpty() {
        return stage == CropStage.EMPTY || stage == CropStage.WITHERED;
    }

    public void clear() {
        this.cropType = null;
        this.stage = CropStage.EMPTY;
        this.waterCount = 0;
        this.plantedAt = null;
        this.hasPest = false;
        this.hasWeed = false;
    }

    public void plant(String cropType) {
        this.cropType = cropType;
        this.stage = CropStage.SEED;
        this.waterCount = 0;
        this.plantedAt = System.currentTimeMillis();
        this.hasPest = false;
        this.hasWeed = false;
    }

    public void harvest() {
        clear();
    }
}
