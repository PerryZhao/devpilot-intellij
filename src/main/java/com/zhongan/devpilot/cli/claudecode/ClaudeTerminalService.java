package com.zhongan.devpilot.cli.claudecode;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.jediterm.terminal.ui.TerminalWidget;
import com.zhongan.devpilot.cli.CliService;
import com.zhongan.devpilot.cli.PlatformCompatibilityUtil;
import com.zhongan.devpilot.cli.TerminalUtil;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;

import static com.zhongan.devpilot.util.LoginUtils.getLoginType;
import static com.zhongan.devpilot.util.LoginUtils.isAuthOn;

/**
 * Service for managing Claude terminal operations
 * Contains Claude-specific terminal logic and operations
 */
public class ClaudeTerminalService implements CliService {
    private static final Logger LOG = Logger.getInstance(ClaudeTerminalService.class);

    public static final String CLAUDE_CODE_CLI_TITLE = "Claude Code";

    // Reduced debounce time to improve responsiveness while still preventing duplicates
    private static final long IGNORE_DUPLICATE_OPEN_CLAUDE_ELAPSED_TIME_MS = 3000L;

    // Use AtomicLong for thread safety
    private static final AtomicLong lastOpenedClaudeTimestampMs = new AtomicLong(0);

    // Lock object for synchronizing Claude terminal operations
    private static final Object CLAUDE_TERMINAL_LOCK = new Object();

    private final TerminalUtil terminalUtil;

    public static final ClaudeTerminalService INSTANCE = new ClaudeTerminalService();

    private ClaudeTerminalService() {
        this.terminalUtil = TerminalUtil.INSTANCE;
    }

    /**
     * Open Claude Code in terminal with Claude-specific logic
     *
     * @param project Current project
     * @return true if terminal opened successfully, false otherwise
     */
    private boolean openClaudeInTerminal(Project project) {
        if (!validateClaudeAvailability(project)) {
            return false;
        }

        try {
            LOG.info("Opening Claude Code terminal");
            TerminalWidget widget = openClaudeInTerminalInternal(project);

            if (widget == null) {
                LOG.warn("Failed to open Claude Code terminal");
                showWarning(project,
                        "Failed to open Claude Code terminal. Please try again later.",
                        "Claude Code Warning");
                return false;
            }

            LOG.info("Claude Code terminal opened successfully");
            return true;

        } catch (Exception e) {
            LOG.error("Error opening Claude Code in terminal", e);
            showError(project,
                    "Failed to open Claude Code: " + e.getMessage(),
                    "Claude Code Error");
            return false;
        }
    }

    /**
     * Internal method to handle Claude terminal opening with debounce and reuse logic
     */
    @Nullable
    private TerminalWidget openClaudeInTerminalInternal(@NotNull Project project) {
        // Synchronize to prevent race conditions
        synchronized (CLAUDE_TERMINAL_LOCK) {
            // Check for existing Claude terminal first
            TerminalWidget existingTerminal = findExistingClaudeTerminal(project);
            if (existingTerminal != null) {
                LOG.info("Found existing Claude Code terminal, reusing it");

                // Configure ESC key handler to keep focus in terminal
                if (existingTerminal instanceof ShellTerminalWidget shellWidget) {
                    configureClaudeTerminalKeybindings(shellWidget);

                    // Check if there's a running Claude process with platform-specific fallback
                    boolean hasRunningCommands = PlatformCompatibilityUtil.safeHasRunningCommands(shellWidget);

                    if (!hasRunningCommands) {
                        LOG.info("No running Claude process found in existing terminal, executing Claude command");
                        String projectBasePath = project.getBasePath();
                        if (projectBasePath != null) {
                            try {
                                // Execute the Claude command in the existing terminal
                                shellWidget.executeCommand("claude");
                                LOG.info("Claude command executed in existing terminal");
                            } catch (Exception e) {
                                LOG.warn("Failed to execute Claude command in existing terminal", e);
                            }
                        }
                    } else {
                        LOG.info("Claude process is already running in existing terminal, reusing without executing command");
                    }

                    LOG.info("Requesting focus for Claude terminal");
                    shellWidget.getTerminalPanel().requestFocusInWindow();
                    shellWidget.getTerminalPanel().requestFocus();
                }
                return existingTerminal;
            }

            // Check debounce time
            long currentTime = System.currentTimeMillis();
            long lastTime = lastOpenedClaudeTimestampMs.get();
            long elapsedTime = currentTime - lastTime;

            if (elapsedTime <= IGNORE_DUPLICATE_OPEN_CLAUDE_ELAPSED_TIME_MS) {
                LOG.info("Ignoring duplicate Claude Code terminal open request (within " +
                        IGNORE_DUPLICATE_OPEN_CLAUDE_ELAPSED_TIME_MS + "ms)");
                return null;
            }

            // Update timestamp atomically using compareAndSet to prevent race conditions
            while (!lastOpenedClaudeTimestampMs.compareAndSet(lastTime, currentTime)) {
                lastTime = lastOpenedClaudeTimestampMs.get();
                currentTime = System.currentTimeMillis();
                elapsedTime = currentTime - lastTime;
                if (elapsedTime <= IGNORE_DUPLICATE_OPEN_CLAUDE_ELAPSED_TIME_MS) {
                    LOG.info("Another thread opened Claude Code terminal, ignoring this request");
                    return null;
                }
            }

            String projectBasePath = project.getBasePath();
            if (projectBasePath != null) {
                try {
                    String claudeCommand = "claude";

                    LOG.info("Creating new Claude Code terminal and executing command");
                    // Use the generic terminal utility to run the command
                    terminalUtil.runCommandInTerminal(project, claudeCommand, projectBasePath, getTerminalTitle(), true);

                    // Get the widget after running the command
                    TerminalWidget widget = terminalUtil.getCurrentTerminalWidget(project);
                    if (widget == null) {
                        LOG.warn("Failed to get terminal widget after creating Claude terminal");
                        return null;
                    }

                    // Configure ESC key handler for Claude terminal
                    if (widget instanceof ShellTerminalWidget shellWidget) {
                        configureClaudeTerminalKeybindings(shellWidget);

                        shellWidget.getTerminalPanel().requestFocusInWindow();
                        shellWidget.getTerminalPanel().requestFocus();
                    }

                    return widget;
                } catch (Exception e) {
                    LOG.warn("Failed to open Claude in terminal", e);
                    lastOpenedClaudeTimestampMs.set(0);
                }
            }
            return null;
        }
    }

    /**
     * Configure Claude-specific terminal key bindings
     */
    private void configureClaudeTerminalKeybindings(ShellTerminalWidget widget) {
        // First apply the generic terminal keybindings
        terminalUtil.configureTerminalKeybindings(widget);

        // Add Claude-specific keybinding enhancements
        try {
            // Add a focus listener to ensure the terminal keeps focus
            widget.getComponent().addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    widget.getTerminalPanel().requestFocusInWindow();
                }
            });

            // Add a component listener to request focus when the terminal becomes visible
            widget.getComponent().addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    widget.getTerminalPanel().requestFocusInWindow();
                }
            });

            LOG.info("Claude-specific terminal keybindings configured");
        } catch (Exception e) {
            LOG.warn("Unable to configure Claude-specific terminal keybindings", e);
        }
    }

    /**
     * Find existing Claude terminal
     */
    @Nullable
    private TerminalWidget findExistingClaudeTerminal(@NotNull Project project) {
        return terminalUtil.findExistingTerminalByTitle(project, getTerminalTitle());
    }

    private boolean validateClaudeAvailability(Project project) {
        if (!isCliAvailable()) {
            LOG.warn("Claude Code CLI is not available");
            showWarning(project,
                    "Claude Code CLI is not available. Please make sure it's installed correctly.",
                    "Claude Code Warning");
            return false;
        }
        return true;
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

    @Override
    public boolean openInTerminal(Project project) {
        return openClaudeInTerminal(project);
    }

    @Override
    public String getTerminalTitle() {
        return CLAUDE_CODE_CLI_TITLE;
    }

    /**
     * Check if there is a running command in any existing Claude terminal
     *
     * @param project Current project
     * @return true if a Claude command is already running
     */
    public boolean hasRunningCommand(Project project) {
        try {
            TerminalWidget existingTerminal = findExistingClaudeTerminal(project);
            if (existingTerminal instanceof ShellTerminalWidget shellWidget) {
                return PlatformCompatibilityUtil.safeHasRunningCommands(shellWidget);
            }
            return false;
        } catch (Exception e) {
            LOG.warn("Error checking for running Claude commands", e);
            return false;
        }
    }

    @Override
    public boolean isCliAvailable() {
        boolean isAuthOn = isAuthOn();
        return isAuthOn && !"wx".equals(getLoginType());
    }
}
