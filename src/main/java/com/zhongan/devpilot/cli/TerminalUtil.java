package com.zhongan.devpilot.cli;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.jediterm.terminal.ui.TerminalWidget;

import java.awt.KeyboardFocusManager;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowFactory;
import org.jetbrains.plugins.terminal.TerminalView;

/**
 * Generic terminal utility class for common terminal operations
 * CLI-specific implementations should be in their respective packages
 */
public final class TerminalUtil {
    @NotNull
    public static final TerminalUtil INSTANCE = new TerminalUtil();

    private static final Logger LOG = Logger.getInstance(TerminalUtil.class);

    // Lock object for synchronizing terminal operations
    private static final Object TERMINAL_LOCK = new Object();

    /**
     * Run a command in terminal with specified title
     *
     * @param project Current project
     * @param command Command to execute
     * @param workingDirectory Working directory for the command
     * @param title Terminal tab title
     * @param activateToolWindow Whether to activate the terminal tool window
     */
    public void runCommandInTerminal(
            @NotNull Project project,
            @NotNull String command,
            @NotNull String workingDirectory,
            @NotNull String title,
            boolean activateToolWindow) {

        synchronized (TERMINAL_LOCK) {
            try {
                TerminalView terminalView = TerminalView.getInstance(project);
                if (terminalView == null) {
                    LOG.error("TerminalView is null, cannot run command");
                    return;
                }

                // Get the terminal tool window but don't activate it yet
                ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
                if (window == null) {
                    LOG.error("Terminal tool window is null, cannot run command");
                    return;
                }

                ContentManager contentManager = window.getContentManager();
                Content existingContent = null;
                ShellTerminalWidget existingWidget = null;

                // First check for existing terminal with the same title
                for (Content content : contentManager.getContents()) {
                    if (content != null && title.equals(content.getDisplayName())) {
                        ShellTerminalWidget widget = (ShellTerminalWidget) TerminalView.getWidgetByContent(content);
                        boolean hasRunningCommands = PlatformCompatibilityUtil.safeHasRunningCommands(widget);
                        if (!hasRunningCommands) {
                            existingContent = content;
                            existingWidget = widget;
                            break;
                        }
                    }
                }

                if (existingWidget != null) {
                    LOG.info("Found existing terminal tab with title '" + title + "', reusing it");

                    // Now activate the tool window after finding the existing terminal
                    if (activateToolWindow) {
                        ShellTerminalWidget finalExistingWidget = existingWidget;
                        window.activate(() -> {
                            // This runs after activation is complete
                            if (finalExistingWidget.getComponent().isShowing()) {
                                // Ensure terminal panel gets focus
                                finalExistingWidget.getTerminalPanel().requestFocusInWindow();
                                LOG.debug("Focus requested for terminal panel after activation");
                            }
                        });
                    }

                    contentManager.setSelectedContent(existingContent);

                    // Wait a bit for the terminal to be ready
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Request focus again to ensure it's maintained
                    existingWidget.getComponent().requestFocusInWindow();
                    existingWidget.getTerminalPanel().requestFocusInWindow();

                    LOG.info("Executing command in existing terminal: " + command);
                    existingWidget.executeCommand(command);
                    return;
                }

                // No suitable existing terminal found, create a new one directly with the specified title
                LOG.info("Creating new terminal tab with title '" + title + "' for command execution");

                // Create a new terminal widget with the specified title
                // Note: createLocalShellWidget will not activate the window by itself
                ShellTerminalWidget widget = terminalView.createLocalShellWidget(
                        workingDirectory,
                        title,  // Use the provided title directly
                        false,  // Don't activate tool window yet
                        false   // Don't request focus yet
                );

                // Wait a bit for the terminal to initialize
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Now activate the tool window after creating the terminal
                if (activateToolWindow) {
                    window.activate(null);

                    // Select our content
                    for (Content content : contentManager.getContents()) {
                        if (content != null && title.equals(content.getDisplayName())) {
                            contentManager.setSelectedContent(content);
                            
                            // Get the widget for this content and ensure it has focus
                            ShellTerminalWidget contentWidget = (ShellTerminalWidget) TerminalView.getWidgetByContent(content);
                            if (contentWidget != null && contentWidget.getComponent().isShowing()) {
                                contentWidget.getTerminalPanel().requestFocusInWindow();
                            }
                            break;
                        }
                    }

                    // Wait a bit for activation to complete
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                LOG.info("Executing command in new terminal: " + command);
                widget.executeCommand(command);

            } catch (IOException e) {
                LOG.warn("Failed to execute command: " + command, e);
            } catch (Exception e) {
                LOG.error("Unexpected error executing terminal command", e);
            }
        }
    }

    /**
     * Find existing terminal by title
     *
     * @param project Current project
     * @param title Terminal title to search for
     * @return TerminalWidget if found, null otherwise
     */
    @Nullable
    public TerminalWidget findExistingTerminalByTitle(@NotNull Project project, @NotNull String title) {
        try {
            ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
            if (window == null) {
                LOG.info("Terminal tool window not found");
                return null;
            }

            ContentManager contentManager = window.getContentManager();

            for (Content content : contentManager.getContents()) {
                if (content != null && title.equals(content.getDisplayName())) {
                    ShellTerminalWidget widget = (ShellTerminalWidget) TerminalView.getWidgetByContent(content);
                    if (widget != null) {
                        LOG.info("Found existing terminal tab with title: " + title);

                        // Select and activate the terminal
                        contentManager.setSelectedContent(content);
                        window.activate(() -> {
                            // This runs after activation is complete
                            if (widget.getComponent().isShowing()) {
                                // Ensure terminal panel gets focus
                                widget.getTerminalPanel().requestFocusInWindow();
                                LOG.debug("Focus requested for terminal panel after activation");
                            }
                        });

                        // Wait a bit for activation to complete
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }

                        return widget;
                    }
                }
            }

            LOG.info("No existing terminal tab found with title: " + title);
            return null;
        } catch (Exception e) {
            LOG.warn("Error finding existing terminal with title: " + title, e);
            return null;
        }
    }

    // Flag to track if ESC is being handled to prevent duplicate ESC characters
    private static final java.util.concurrent.atomic.AtomicBoolean escHandlingInProgress = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * Configure terminal key bindings (generic implementation)
     *
     * @param widget Terminal widget to configure
     */
    public void configureTerminalKeybindings(ShellTerminalWidget widget) {
        // Prevent ESC from moving focus out of the terminal by adding a key listener
        try {
            // Add a custom key listener that intercepts the ESC key
            widget.getTerminalPanel().addKeyListener(new java.awt.event.KeyAdapter() {
                @Override
                public void keyPressed(java.awt.event.KeyEvent e) {
                    // Intercept ESC key (KeyEvent.VK_ESCAPE = 27)
                    if (e.getKeyCode() == 27) {
                        // Only handle if not already being handled
                        if (escHandlingInProgress.compareAndSet(false, true)) {
                            try {
                                e.consume(); // Prevent default ESC behavior

                                // Send the ESC character to the terminal instead of letting it bubble up
                                // This ensures the terminal processes the ESC key rather than the IDE
                                try {
                                    widget.getTerminalStarter().sendString("\u001B", false);
                                    LOG.debug("ESC character sent to terminal from key listener");
                                } catch (Exception ex) {
                                    LOG.warn("Failed to send ESC character to terminal", ex);
                                }
                            } finally {
                                // Reset the flag after a short delay to prevent key repeat issues
                                Timer timer = new Timer();
                                timer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        escHandlingInProgress.set(false);
                                    }
                                }, 50); // 50ms delay
                            }
                        }
                    }
                }
            });

            // Register a key dispatcher at a higher level to ensure ESC is captured
            widget.getComponent().setFocusTraversalKeysEnabled(false);
            widget.getComponent().setFocusCycleRoot(true);

            // Create a key event dispatcher for Windows-specific handling
            // This is especially important for IDEA 2024.2+ on Windows
            java.awt.KeyEventDispatcher escKeyDispatcher = e -> {
                // Only process when our terminal panel has focus and it's an ESC key press
                if (e.getID() == KeyEvent.KEY_PRESSED &&
                    e.getKeyCode() == KeyEvent.VK_ESCAPE &&
                    e.getComponent() != null &&
                    widget.getTerminalPanel().isAncestorOf(e.getComponent())) {

                    e.consume(); // Prevent default ESC behavior

                    // Request focus to ensure terminal keeps focus
                    widget.getTerminalPanel().requestFocusInWindow();

                    // Only send ESC character if not already being handled by the key listener
                    if (escHandlingInProgress.compareAndSet(false, true)) {
                        try {
                            // Send ESC character to terminal
                            try {
                                widget.getTerminalStarter().sendString("\u001B", false);
                                LOG.debug("ESC character sent to terminal from dispatcher");
                            } catch (Exception ex) {
                                LOG.warn("Failed to send ESC character to terminal from dispatcher", ex);
                            }
                        } finally {
                            // Reset the flag after a short delay
                            Timer timer = new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    escHandlingInProgress.set(false);
                                }
                            }, 50); // 50ms delay
                        }
                    }

                    return true; // Event handled
                }
                return false; // Continue with event dispatch
            };

            // Add the dispatcher to the KeyboardFocusManager
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escKeyDispatcher);

            // Add a component listener to clean up the dispatcher when the terminal is closed
            widget.getComponent().addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    if (!widget.getComponent().isShowing()) {
                        // Terminal is no longer showing, remove the dispatcher
                        LOG.info("Terminal closed, removing ESC key dispatcher");
                        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                            .removeKeyEventDispatcher(escKeyDispatcher);
                    }
                }
            });

            LOG.info("Enhanced ESC key handler configured for terminal (Windows-compatible)");
        } catch (Exception e) {
            LOG.warn("Unable to configure ESC key handler for terminal", e);
        }
    }

    /**
     * Get current terminal widget
     *
     * @param project Current project
     * @return Current terminal widget or null
     */
    @Nullable
    public TerminalWidget getCurrentTerminalWidget(Project project) {
        ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(TerminalToolWindowFactory.TOOL_WINDOW_ID);
        if (window == null) {
            return null;
        }

        // Activate with a callback to ensure focus is properly set
        window.activate(() -> {
            Content selectedContent = window.getContentManager().getSelectedContent();
            if (selectedContent != null) {
                ShellTerminalWidget widget = (ShellTerminalWidget) TerminalView.getWidgetByContent(selectedContent);
                if (widget != null && widget.getComponent().isShowing()) {
                    // Ensure terminal panel gets focus
                    widget.getTerminalPanel().requestFocusInWindow();
                    LOG.debug("Focus requested for terminal panel in getCurrentTerminalWidget");
                }
            }
        });

        // Wait a bit for activation to complete
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Content selectedContent = window.getContentManager().getSelectedContent();
        if (selectedContent == null) {
            return null;
        }

        ShellTerminalWidget widget = (ShellTerminalWidget) TerminalView.getWidgetByContent(selectedContent);
        if (widget != null) {
            // Request focus again to ensure it's maintained
            widget.getComponent().requestFocusInWindow();
            widget.getTerminalPanel().requestFocusInWindow();
        }

        return widget;
    }

}
