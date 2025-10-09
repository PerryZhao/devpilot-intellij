package com.zhongan.devpilot.integrations.llms.entity;

import java.math.BigDecimal;

public class ModelInfo {

    private boolean supportsImages;

    /**
     * Price per prompt token
     */
    private BigDecimal inputTokenPrice;

    /**
     * Price per completion token
     */
    private BigDecimal outputTokenPrice;

    /**
     * Price per cache creation token
     */
    private BigDecimal cacheCreationTokenPrice;

    /**
     * Price per cache read token
     */
    private BigDecimal cacheReadTokenPrice;

    private String currency = "CNY";

    public BigDecimal getCacheReadTokenPrice() {
        return cacheReadTokenPrice;
    }

    public void setCacheReadTokenPrice(BigDecimal cacheReadTokenPrice) {
        this.cacheReadTokenPrice = cacheReadTokenPrice;
    }

    public boolean isSupportsImages() {
        return supportsImages;
    }

    public void setSupportsImages(boolean supportsImages) {
        this.supportsImages = supportsImages;
    }

    public BigDecimal getInputTokenPrice() {
        return inputTokenPrice;
    }

    public void setInputTokenPrice(BigDecimal inputTokenPrice) {
        this.inputTokenPrice = inputTokenPrice;
    }

    public BigDecimal getOutputTokenPrice() {
        return outputTokenPrice;
    }

    public void setOutputTokenPrice(BigDecimal outputTokenPrice) {
        this.outputTokenPrice = outputTokenPrice;
    }

    public BigDecimal getCacheCreationTokenPrice() {
        return cacheCreationTokenPrice;
    }

    public void setCacheCreationTokenPrice(BigDecimal cacheCreationTokenPrice) {
        this.cacheCreationTokenPrice = cacheCreationTokenPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}