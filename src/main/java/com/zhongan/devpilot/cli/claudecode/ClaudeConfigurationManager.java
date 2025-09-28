package com.zhongan.devpilot.cli.claudecode;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.zhongan.devpilot.cli.FileOperationHelper;
import com.zhongan.devpilot.mcp.McpConnections;
import com.zhongan.devpilot.mcp.McpServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import static com.zhongan.devpilot.constant.DefaultConst.AI_GATEWAY_DEFAULT_HOST;
import static com.zhongan.devpilot.constant.DefaultConst.CLAUDE_CODE_CLI_DIRECT_URL;

/**
 * Manages Claude configuration files and settings
 */
public class ClaudeConfigurationManager {
    private static final Logger LOG = Logger.getInstance(ClaudeConfigurationManager.class);

    private static final String CLAUDE_SETTINGS_DIR = ".claude";

    private static final String CLAUDE_SETTINGS_FILE = "settings.json";

    private static final String CLAUDE_CONFIG_FILE = ".claude.json";

    private static final String DEFAULT_BASE_URL = AI_GATEWAY_DEFAULT_HOST + CLAUDE_CODE_CLI_DIRECT_URL;

    private static final String ENV_KEY = "env";

    private static final String AUTH_TOKEN_KEY = "ANTHROPIC_AUTH_TOKEN";

    private static final String BASE_URL_KEY = "ANTHROPIC_BASE_URL";

    private static final String MCP_SERVERS_KEY = "mcpServers";

    private final FileOperationHelper fileHelper;

    public ClaudeConfigurationManager() {
        this.fileHelper = new FileOperationHelper();
    }

    /**
     * Update Claude environment settings
     */
    public void updateEnvironmentSettings(Project project, String accessKey) {
        Path settingsPath = getClaudeSettingsPath();
        if (settingsPath == null) {
            showError(project, "无法获取用户主目录，请检查系统环境配置。", "Claude Code 配置错误");
            return;
        }

        try {
            EnvironmentConfig config = prepareEnvironmentConfig(settingsPath, accessKey);
            if (config == null) {
                showWarning(project, "环境配置出错：无法获取有效的ANTHROPIC_AUTH_TOKEN，请检查CLI登录状态或~/.claude/settings.json文件配置。", "Claude Code 配置错误");
                return;
            }

            if (config.needsUpdate()) {
                writeEnvironmentConfig(settingsPath, config);
                LOG.info("更新了Claude设置：" + settingsPath);
            } else {
                LOG.info("配置值与文件中的一致，无需更新文件");
            }
        } catch (Exception e) {
            LOG.error("处理Claude设置文件时发生错误：" + settingsPath, e);
            showError(project, "处理Claude配置文件时发生错误：" + e.getMessage(), "Claude Code 配置错误");
        }
    }

    /**
     * Update MCP servers configuration
     */
    public void updateMcpConfiguration(Project project, McpConnections mcpConnections) {
        if (mcpConnections == null) {
            return;
        }

        Path configPath = getClaudeConfigPath();
        if (configPath == null) {
            showError(project, "无法获取用户主目录，请检查系统环境配置。", "Claude Code 配置错误");
            return;
        }

        try {
            Map<String, McpServer> connectedServers = extractConnectedServers(mcpConnections);
            updateMcpConfigFile(configPath, connectedServers);
            LOG.info("MCP configuration written to ~/.claude.json: " + configPath);
        } catch (Exception e) {
            LOG.error("处理MCP配置文件时发生错误：" + configPath, e);
            showError(project, "处理MCP配置文件时发生错误：" + e.getMessage(), "Claude Code 配置错误");
        }
    }

    private Path getClaudeSettingsPath() {
        String userHome = System.getProperty("user.home");
        if (StringUtils.isEmpty(userHome)) {
            LOG.error("无法获取用户主目录");
            return null;
        }
        return Paths.get(userHome, CLAUDE_SETTINGS_DIR, CLAUDE_SETTINGS_FILE);
    }

    private Path getClaudeConfigPath() {
        String userHome = System.getProperty("user.home");
        if (StringUtils.isEmpty(userHome)) {
            LOG.error("无法获取用户主目录");
            return null;
        }
        return Paths.get(userHome, CLAUDE_CONFIG_FILE);
    }

    private EnvironmentConfig prepareEnvironmentConfig(Path settingsPath, String cliAccessKey) throws IOException {
        Map<String, Object> existingConfig = fileHelper.readJsonFile(settingsPath);

        String existingAuthToken = null;
        String existingBaseUrl = null;

        if (existingConfig != null) {
            Map<String, Object> existingEnv = (Map<String, Object>) existingConfig.get(ENV_KEY);
            if (existingEnv != null) {
                existingAuthToken = (String) existingEnv.get(AUTH_TOKEN_KEY);
                existingBaseUrl = (String) existingEnv.get(BASE_URL_KEY);
            }
        }

        String finalAuthToken = StringUtils.isNotEmpty(cliAccessKey) ? cliAccessKey : existingAuthToken;
        String finalBaseUrl = StringUtils.isNotEmpty(existingBaseUrl) ? existingBaseUrl : DEFAULT_BASE_URL;

        if (StringUtils.isEmpty(finalAuthToken)) {
            return null;
        }

        return new EnvironmentConfig(finalAuthToken, finalBaseUrl, existingAuthToken, existingBaseUrl, existingConfig);
    }

    private void writeEnvironmentConfig(Path settingsPath, EnvironmentConfig config) throws IOException {
        Files.createDirectories(settingsPath.getParent());

        Map<String, String> envMap = new HashMap<>();
        envMap.put(AUTH_TOKEN_KEY, config.getAuthToken());
        envMap.put(BASE_URL_KEY, config.getBaseUrl());

        Map<String, Object> rootJson;
        if (config.getExistingConfig() != null) {
            rootJson = config.getExistingConfig();
            Map<String, Object> existingEnv = (Map<String, Object>) rootJson.getOrDefault(ENV_KEY, new HashMap<>());
            existingEnv.putAll(envMap);
            rootJson.put(ENV_KEY, existingEnv);
        } else {
            rootJson = new HashMap<>();
            rootJson.put(ENV_KEY, envMap);
        }

        fileHelper.writeJsonFile(settingsPath, rootJson);
    }

    private Map<String, McpServer> extractConnectedServers(McpConnections mcpConnections) {
        Map<String, McpServer> connectedServers = new HashMap<>();

        if (mcpConnections.getConnections() != null && mcpConnections.getMcpServers() != null) {
            Map<String, McpConnections.ServerStatus> connections = mcpConnections.getConnections();
            Map<String, McpServer> allMcpServers = mcpConnections.getMcpServers();

            for (Map.Entry<String, McpConnections.ServerStatus> entry : connections.entrySet()) {
                String serverId = entry.getKey();
                McpConnections.ServerStatus status = entry.getValue();
                McpServer server = allMcpServers.get(serverId);

                if ("connected".equals(status.getStatus()) && server != null && server.isEnabled()) {
                    connectedServers.put(serverId, server);
                }
            }
        }

        return connectedServers;
    }

    private void updateMcpConfigFile(Path configPath, Map<String, McpServer> newMcpServers) throws IOException {
        Map<String, Object> existingConfig = fileHelper.readJsonFile(configPath);
        if (existingConfig == null) {
            existingConfig = new HashMap<>();
        }

        Map<String, Object> existingMcpServers = (Map<String, Object>) existingConfig.getOrDefault(MCP_SERVERS_KEY, new HashMap<>());

        // Merge new MCP servers into existing configuration
        existingMcpServers.putAll(newMcpServers);

        existingConfig.put(MCP_SERVERS_KEY, existingMcpServers);
        fileHelper.writeJsonFile(configPath, existingConfig);
    }

    private void showError(Project project, String message, String title) {
        if (project != null) {
            Messages.showErrorDialog(project, message, title);
        }
    }

    private void showWarning(Project project, String message, String title) {
        if (project != null) {
            Messages.showWarningDialog(project, message, title);
        }
    }

    /**
     * Environment configuration data holder
     */
    private static class EnvironmentConfig {
        private final String authToken;

        private final String baseUrl;

        private final String existingAuthToken;

        private final String existingBaseUrl;

        private final Map<String, Object> existingConfig;

        EnvironmentConfig(String authToken, String baseUrl, String existingAuthToken,
                          String existingBaseUrl, Map<String, Object> existingConfig) {
            this.authToken = authToken;
            this.baseUrl = baseUrl;
            this.existingAuthToken = existingAuthToken;
            this.existingBaseUrl = existingBaseUrl;
            this.existingConfig = existingConfig;
        }

        public boolean needsUpdate() {
            return !StringUtils.equals(authToken, existingAuthToken) ||
                    !StringUtils.equals(baseUrl, existingBaseUrl);
        }

        public String getAuthToken() {
            return authToken;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public Map<String, Object> getExistingConfig() {
            return existingConfig;
        }
    }
}
