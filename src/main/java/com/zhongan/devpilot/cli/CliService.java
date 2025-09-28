package com.zhongan.devpilot.cli;

import com.intellij.openapi.project.Project;

/**
 * Base interface for CLI service implementations
 * This interface defines the common contract for all CLI tools integration
 */
public interface CliService {
    
    /**
     * Open the CLI tool in terminal
     * 
     * @param project Current project
     * @return true if CLI tool opened successfully, false otherwise
     */
    boolean openInTerminal(Project project);

    /**
     * Get the terminal title for this CLI tool
     * 
     * @return Terminal title
     */
    String getTerminalTitle();
    
    /**
     * Check if the CLI tool is available
     * 
     * @return true if CLI tool is available, false otherwise
     */
    boolean isCliAvailable();
}
