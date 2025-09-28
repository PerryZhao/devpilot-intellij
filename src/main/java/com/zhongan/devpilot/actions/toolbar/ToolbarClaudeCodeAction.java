package com.zhongan.devpilot.actions.toolbar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.IconUtil;
import com.zhongan.devpilot.DevPilotIcons;
import com.zhongan.devpilot.agents.AgentRefreshedObserver;
import com.zhongan.devpilot.cli.claudecode.ClaudeConfigurationManager;
import com.zhongan.devpilot.cli.claudecode.ClaudeTerminalService;
import com.zhongan.devpilot.cli.security.AccessKeyService;
import com.zhongan.devpilot.mcp.McpConfigurationHandler;
import com.zhongan.devpilot.mcp.McpConnections;
import com.zhongan.devpilot.settings.state.AIGatewaySettingsState;
import com.zhongan.devpilot.util.DevPilotMessageBundle;

import org.jetbrains.annotations.NotNull;

/**
 * Toolbar action for opening Claude Code in terminal
 * 
 * This action handles:
 * - UI state management
 * - Configuration updates
 * - Terminal initialization
 * - Execution flow coordination
 */
public class ToolbarClaudeCodeAction extends AnAction implements AgentRefreshedObserver {
    private static final Logger LOG = Logger.getInstance(ToolbarClaudeCodeAction.class);
    
    // Services
    private final AccessKeyService accessKeyService;

    private final ClaudeConfigurationManager configurationManager;

    private final ClaudeTerminalService terminalService;
    
    // Execution control
    private volatile boolean isExecuting = false;
    
    public ToolbarClaudeCodeAction() {
        super(DevPilotMessageBundle.get("devpilot.toolbarClaudeCodeAction.text"),
                DevPilotMessageBundle.get("devpilot.toolbarClaudeCodeAction.description"),
                IconUtil.resizeSquared(DevPilotIcons.TERMINAL_CLI, 16));
        
        this.accessKeyService = AccessKeyService.INSTANCE;
        this.configurationManager = new ClaudeConfigurationManager();
        this.terminalService = ClaudeTerminalService.INSTANCE;
    }
    
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        Project project = e.getProject();
        
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }
        
        // Only show the action if Claude Code CLI is available
        e.getPresentation().setEnabledAndVisible(terminalService.isCliAvailable());
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        
        // Prevent multiple simultaneous executions
        if (isExecuting) {
            LOG.info("Claude Code terminal action is already in progress, ignoring duplicate request");
            return;
        }
        
        ApplicationManager.getApplication().invokeLater(() -> executeAction(project));
    }
    
    private void executeAction(Project project) {
        try {
            isExecuting = true;
            LOG.info("Starting Claude Code terminal initialization");
            
            // Check for existing Claude terminals with running commands first
            boolean hasRunningCommand = terminalService.hasRunningCommand(project);
            
            // Get CLI settings
            var aiGatewaySettings = AIGatewaySettingsState.getInstance();
            boolean autoAuthentication = aiGatewaySettings.isAutoAuthentication();
            boolean syncMcpServerConfig = aiGatewaySettings.isSyncMcpServerConfig();
            
            // Only update environment and MCP configurations if no command is running
            if (!hasRunningCommand) {
                LOG.info("No running Claude command detected, updating configurations");
                
                // Step 1: Update environment configuration (only if auto authentication is enabled)
                if (autoAuthentication) {
                    String accessKey = accessKeyService.getAccessKey();
                    configurationManager.updateEnvironmentSettings(project, accessKey);
                    LOG.info("Environment settings updated");
                } else {
                    LOG.info("Auto authentication is disabled, skipping environment settings update");
                }
                
                // Step 2: Update MCP server configuration (only if sync MCP server config is enabled)
                if (syncMcpServerConfig) {
                    McpConnections mcpConnections = McpConfigurationHandler.INSTANCE
                            .loadMcpServersWithConnectionStatus(Boolean.TRUE);
                    configurationManager.updateMcpConfiguration(project, mcpConnections);
                    LOG.info("MCP server configuration updated");
                } else {
                    LOG.info("Sync MCP server configuration is disabled, skipping MCP configuration update");
                }
            } else {
                LOG.info("Claude command is already running, skipping configuration updates");
            }
            
            // Step 3: Open terminal (reusing existing one if there's a running command)
            boolean success = terminalService.openInTerminal(project);
            if (success) {
                LOG.info("Claude Code action completed successfully");
            } else {
                LOG.warn("Claude Code action completed with warnings");
            }
            
        } catch (Exception ex) {
            LOG.error("Error during Claude Code action execution", ex);
        } finally {
            // Reset execution flag
            isExecuting = false;
        }
    }
    
    @Override
    public void onRefresh() {
        // Refresh access key when agents are refreshed
        accessKeyService.refreshAccessKey();
        LOG.info("Access key refreshed due to agent refresh");
    }
}
