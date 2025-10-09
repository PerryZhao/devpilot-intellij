package com.zhongan.devpilot.settings.cache;

import com.intellij.openapi.components.Service;
import com.zhongan.devpilot.integrations.llms.entity.ModelInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service(Service.Level.APP)
public final class ModelAutoPreferenceCache {

    private final Map<String, Map<String, ModelInfo>> cachedModelInfo = new ConcurrentHashMap<>();

    private volatile boolean isLoaded = false;

    public synchronized void updateCache(Map<String, Map<String, ModelInfo>> modelInfo) {
        if (modelInfo != null) {
            this.cachedModelInfo.clear();
            this.cachedModelInfo.putAll(modelInfo);
            this.isLoaded = true;
        }
    }

    public Map<String, Map<String, ModelInfo>> getCachedModelInfo() {
        return new HashMap<>(cachedModelInfo);
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public String[] getAllModelIds() {
        return cachedModelInfo.values()
                .stream()
                .flatMap(tierMap -> tierMap.keySet().stream())
                .distinct()
                .sorted()
                .toArray(String[]::new);
    }

    public String[] getOrderedModelIds(Set<String> excludeModels) {
        List<String> orderedModels = new ArrayList<>();

        // Add models in tier order: tier1 -> tier2 -> fallback
        String[] tierOrder = {"tier1", "tier2", "fallback"};

        for (String tier : tierOrder) {
            Map<String, ModelInfo> tierModels = cachedModelInfo.get(tier);
            if (tierModels != null) {
                for (String modelId : tierModels.keySet()) {
                    if (excludeModels == null || !excludeModels.contains(modelId)) {
                        orderedModels.add(modelId);
                    }
                }
            }
        }

        return orderedModels.toArray(new String[0]);
    }

    public void clear() {
        cachedModelInfo.clear();
        isLoaded = false;
    }
}