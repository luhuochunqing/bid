package com.xiyu.bid.casework.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * BidCaseSlice 精排策略权重配置。
 *
 * <p>通过 application.yml 的 {@code xiyu.case-slice.match-policy} 前缀配置，
 * 支持动态调整精排各维度权重，无需修改代码。</p>
 */
@Component
@ConfigurationProperties(prefix = "xiyu.case-slice.match-policy")
public class BidCaseSliceMatchPolicyConfig {

    private int recallTopN = 50;

    private int cosineWeight = 40;
    private int titleJaccardWeight = 25;
    private int labelWeight = 15;
    private int richnessWeight = 10;
    private int levelWeight = 10;

    private int richnessThresholdHigh = 5;
    private int richnessThresholdMedium = 3;

    private int levelPriorityThreshold = 2;

    public int getRecallTopN() {
        return recallTopN;
    }

    public void setRecallTopN(int recallTopN) {
        this.recallTopN = recallTopN;
    }

    public int getCosineWeight() {
        return cosineWeight;
    }

    public void setCosineWeight(int cosineWeight) {
        this.cosineWeight = cosineWeight;
    }

    public int getTitleJaccardWeight() {
        return titleJaccardWeight;
    }

    public void setTitleJaccardWeight(int titleJaccardWeight) {
        this.titleJaccardWeight = titleJaccardWeight;
    }

    public int getLabelWeight() {
        return labelWeight;
    }

    public void setLabelWeight(int labelWeight) {
        this.labelWeight = labelWeight;
    }

    public int getRichnessWeight() {
        return richnessWeight;
    }

    public void setRichnessWeight(int richnessWeight) {
        this.richnessWeight = richnessWeight;
    }

    public int getLevelWeight() {
        return levelWeight;
    }

    public void setLevelWeight(int levelWeight) {
        this.levelWeight = levelWeight;
    }

    public int getRichnessThresholdHigh() {
        return richnessThresholdHigh;
    }

    public void setRichnessThresholdHigh(int richnessThresholdHigh) {
        this.richnessThresholdHigh = richnessThresholdHigh;
    }

    public int getRichnessThresholdMedium() {
        return richnessThresholdMedium;
    }

    public void setRichnessThresholdMedium(int richnessThresholdMedium) {
        this.richnessThresholdMedium = richnessThresholdMedium;
    }

    public int getLevelPriorityThreshold() {
        return levelPriorityThreshold;
    }

    public void setLevelPriorityThreshold(int levelPriorityThreshold) {
        this.levelPriorityThreshold = levelPriorityThreshold;
    }
}
