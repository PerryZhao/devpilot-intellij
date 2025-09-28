package com.zhongan.devpilot.cli;

import com.intellij.openapi.diagnostic.Logger;
import com.zhongan.devpilot.util.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Helper class for file operations
 */
public class FileOperationHelper {
    private static final Logger LOG = Logger.getInstance(FileOperationHelper.class);
    
    /**
     * Read JSON file and return as Map
     */
    public Map<String, Object> readJsonFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }
        
        try {
            String content = Files.readString(filePath);
            return JsonUtils.fromJson(content, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to read JSON file: " + filePath, e);
            return null;
        }
    }
    
    /**
     * Write Map as JSON to file
     */
    public void writeJsonFile(Path filePath, Map<String, Object> data) throws IOException {
        String jsonContent = JsonUtils.toJson(data);
        Files.writeString(filePath, jsonContent);
    }
}
