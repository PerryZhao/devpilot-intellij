package com.zhongan.devpilot.cli;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import org.jetbrains.plugins.terminal.ShellTerminalWidget;

/**
 * Utility class for handling platform-specific compatibility issues
 * Particularly for Windows JNA library issues with terminal operations
 */
public final class PlatformCompatibilityUtil {
    private static final Logger LOG = Logger.getInstance(PlatformCompatibilityUtil.class);

    private PlatformCompatibilityUtil() {
        // Utility class
    }

    /**
     * Safely check if a terminal widget has running commands with platform-specific fallbacks
     *
     * @param widget The terminal widget to check
     * @return true if commands are running, false if no commands or unable to determine
     */
    public static boolean safeHasRunningCommands(ShellTerminalWidget widget) {
        if (widget == null) {
            return false;
        }

        // First try the standard JetBrains API
        try {
            boolean hasRunningCommands = widget.hasRunningCommands();
            LOG.debug("Successfully checked running commands status using JetBrains API: " + hasRunningCommands);
            return hasRunningCommands;
        } catch (Throwable e) {
            // Using Throwable instead of Exception to catch JNI errors on Windows
            LOG.warn("JetBrains API failed to check running commands, trying platform-specific fallback", e);

            // Use platform-specific fallback methods
            if (SystemInfo.isWindows) {
                return checkRunningCommandsWindows(widget);
            } else if (SystemInfo.isLinux) {
                return checkRunningCommandsLinux(widget);
            } else if (SystemInfo.isMac) {
                // For macOS, just try to use terminal busy state since JNA usually works fine
                return checkTerminalBusyState(widget);
            } else {
                LOG.warn("No fallback method available for platform, assuming no running commands");
                return false;
            }
        }
    }

    /**
     * Windows-specific fallback to check for running commands
     */
    private static boolean checkRunningCommandsWindows(ShellTerminalWidget widget) {
        try {
            // Method 1: Try to get process ID through reflection
            Integer pid = getTerminalProcessId(widget);
            if (pid != null) {
                return checkWindowsProcessHasChildren(pid);
            }

            // Method 2: Check terminal state through widget properties
            return checkTerminalBusyState(widget);

        } catch (Exception e) {
            LOG.debug("Windows fallback method failed", e);
            return false;
        }
    }

    /**
     * Linux-specific fallback to check for running commands
     */
    private static boolean checkRunningCommandsLinux(ShellTerminalWidget widget) {
        try {
            // Method 1: Try to get process ID through reflection
            Integer pid = getTerminalProcessId(widget);
            if (pid != null) {
                return checkLinuxProcessHasChildren(pid);
            }

            // Method 2: Check terminal state through widget properties
            return checkTerminalBusyState(widget);

        } catch (Exception e) {
            LOG.debug("Linux fallback method failed", e);
            return false;
        }
    }

    /**
     * Try to get terminal process ID through reflection
     */
    private static Integer getTerminalProcessId(ShellTerminalWidget widget) {
        try {
            // Try to access the terminal process through the terminal starter
            Object terminalStarter = widget.getTerminalStarter();
            if (terminalStarter != null) {
                // Try common method names for getting process
                String[] methodNames = {"getProcess", "getTtyConnector", "getShellProcess"};

                for (String methodName : methodNames) {
                    try {
                        Method method = terminalStarter.getClass().getMethod(methodName);
                        Object processObj = method.invoke(terminalStarter);

                        if (processObj != null) {
                            // Try to get PID from the process object
                            Integer pid = extractPidFromProcess(processObj);
                            if (pid != null) {
                                LOG.debug("Found terminal process PID through " + methodName + ": " + pid);
                                return pid;
                            }
                        }
                    } catch (Exception ignored) {
                        // Continue trying other methods
                    }
                }

                // Try to get process through any method that returns a Process-like object
                Method[] methods = terminalStarter.getClass().getMethods();
                for (Method method : methods) {
                    if (method.getParameterCount() == 0 && (method.getName().toLowerCase().contains("process") || method.getName().toLowerCase().contains("connector"))) {
                        try {
                            Object result = method.invoke(terminalStarter);
                            if (result != null) {
                                Integer pid = extractPidFromProcess(result);
                                if (pid != null) {
                                    LOG.debug("Found terminal process PID through " + method.getName() + ": " + pid);
                                    return pid;
                                }
                            }
                        } catch (Exception ignored) {
                            // Continue trying other methods
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to get terminal process ID through reflection", e);
        }
        return null;
    }

    /**
     * Extract PID from various process object types
     */
    private static Integer extractPidFromProcess(Object processObj) {
        if (processObj == null) {
            return null;
        }

        try {
            // Try direct pid() method (Java 9+)
            Method pidMethod = processObj.getClass().getMethod("pid");
            Object pidObj = pidMethod.invoke(processObj);
            if (pidObj instanceof Number) {
                return ((Number) pidObj).intValue();
            }
        } catch (Throwable ignored) {
            // Continue with other methods - using Throwable to catch JNI errors
        }

        try {
            // Try getPid() method
            Method getPidMethod = processObj.getClass().getMethod("getPid");
            Object pidObj = getPidMethod.invoke(processObj);
            if (pidObj instanceof Number) {
                return ((Number) pidObj).intValue();
            }
        } catch (Throwable ignored) {
            // Continue with other methods - using Throwable to catch JNI errors
        }

        try {
            // Try to get process from connector objects
            Method getProcessMethod = processObj.getClass().getMethod("getProcess");
            Object process = getProcessMethod.invoke(processObj);
            if (process != null) {
                return extractPidFromProcess(process); // Recursive call
            }
        } catch (Throwable ignored) {
            // Continue with other methods - using Throwable to catch JNI errors
        }

        return null;
    }

    /**
     * Check if a Windows process has child processes (indicating running commands)
     */
    private static boolean checkWindowsProcessHasChildren(int pid) {
        // Try multiple methods to check for child processes

        // Method 1: Use tasklist command (more reliable than wmic)
        try {
            ProcessBuilder pb = new ProcessBuilder("tasklist", "/fi", "ParentProcessId eq " + pid, "/fo", "csv");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        // Skip header line, if we have more than 1 line, there are child processes
                        if (lineCount > 1 && !line.trim().isEmpty() && !line.contains("INFO:")) {
                            LOG.debug("Found child processes for PID " + pid + " using tasklist, terminal has running commands");
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable e) {
            // Using Throwable to catch JNI errors that might occur on Windows
            LOG.debug("Failed to check Windows process children using tasklist for PID " + pid, e);
        }

        // Method 2: Use wmic as fallback
        try {
            ProcessBuilder pb = new ProcessBuilder("wmic", "process", "where", "ParentProcessId=" + pid, "get", "ProcessId", "/format:csv");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            } else {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null) {
                        lineCount++;
                        // Skip header lines, if we have more than 2 lines, there are child processes
                        if (lineCount > 2 && !line.trim().isEmpty()) {
                            LOG.debug("Found child processes for PID " + pid + " using wmic, terminal has running commands");
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to check Windows process children using wmic for PID " + pid, e);
        }

        LOG.debug("No child processes found for PID " + pid + " using Windows commands");
        return false;
    }

    /**
     * Check if a Linux process has child processes (indicating running commands)
     */
    private static boolean checkLinuxProcessHasChildren(int pid) {
        try {
            // Use pgrep to check for child processes
            ProcessBuilder pb = new ProcessBuilder("pgrep", "-P", String.valueOf(pid));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.trim().isEmpty()) {
                    LOG.debug("Found child processes for PID " + pid + ", terminal has running commands");
                    return true;
                }
            }

            LOG.debug("No child processes found for PID " + pid);
            return false;
        } catch (Exception e) {
            LOG.debug("Failed to check Linux process children for PID " + pid, e);
            return false;
        }
    }

    /**
     * Check terminal busy state through widget properties as a last resort
     */
    private static boolean checkTerminalBusyState(ShellTerminalWidget widget) {
        try {
            // Try to check terminal state through reflection on the widget itself
            Method[] methods = widget.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if ((methodName.contains("running") || methodName.contains("busy") || methodName.contains("active") || methodName.contains("executing")) && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(widget);
                        if (result instanceof Boolean) {
                            boolean isRunning = (Boolean) result;
                            LOG.debug("Terminal widget state check: " + methodName + " = " + isRunning);
                            if (isRunning) {
                                return true;
                            }
                        }
                    } catch (Throwable ignored) {
                        // Continue checking other methods - using Throwable to catch JNI errors
                    }
                }
            }

            // Try to access terminal session or connector through reflection
            try {
                // Try getTerminalSession method
                Method getSessionMethod = widget.getClass().getMethod("getTerminalSession");
                Object session = getSessionMethod.invoke(widget);
                if (session != null) {
                    return checkSessionState(session);
                }
            } catch (Throwable ignored) {
                // Method doesn't exist, continue - using Throwable to catch JNI errors
            }

            try {
                // Try getTtyConnector method
                Method getConnectorMethod = widget.getClass().getMethod("getTtyConnector");
                Object connector = getConnectorMethod.invoke(widget);
                if (connector != null) {
                    return checkConnectorState(connector);
                }
            } catch (Throwable ignored) {
                // Method doesn't exist, continue - using Throwable to catch JNI errors
            }

            // For safety, if we can't determine status, assume no running commands
            // This allows users to create new terminals when detection fails
            LOG.info("Could not reliably determine if terminal has running commands, assuming none");
            return false;

        } catch (Throwable e) {
            LOG.debug("Failed to check terminal busy state", e);
            return false;
        }
    }

    /**
     * Check session state through reflection
     */
    private static boolean checkSessionState(Object session) {
        try {
            Method[] methods = session.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if ((methodName.contains("running") || methodName.contains("busy") || methodName.contains("active")) && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(session);
                        if (result instanceof Boolean) {
                            boolean isRunning = (Boolean) result;
                            LOG.debug("Terminal session state check: " + methodName + " = " + isRunning);
                            if (isRunning) {
                                return true;
                            }
                        }
                    } catch (Exception ignored) {
                        // Continue checking other methods
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to check session state", e);
        }
        return false;
    }

    /**
     * Check connector state through reflection
     */
    private static boolean checkConnectorState(Object connector) {
        try {
            Method[] methods = connector.getClass().getMethods();
            for (Method method : methods) {
                String methodName = method.getName().toLowerCase();
                if ((methodName.contains("running") || methodName.contains("busy") || methodName.contains("active") || methodName.contains("connected")) && method.getParameterCount() == 0) {
                    try {
                        Object result = method.invoke(connector);
                        if (result instanceof Boolean) {
                            boolean isRunning = (Boolean) result;
                            LOG.debug("Terminal connector state check: " + methodName + " = " + isRunning);
                            if (isRunning) {
                                return true;
                            }
                        }
                    } catch (Exception ignored) {
                        // Continue checking other methods
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Failed to check connector state", e);
        }
        return false;
    }

    /**
     * Check terminal I/O state as final fallback
     */
    private static boolean checkTerminalIOState(ShellTerminalWidget widget) {
        try {
            // This is a heuristic approach - check if terminal seems to be waiting for input
            // In most cases, if we can't determine the state, it's safer to assume no running commands
            // to allow terminal reuse, as the user can always interrupt if needed

            LOG.debug("Using heuristic approach - assuming no running commands to allow terminal reuse");
            return false;

        } catch (Exception e) {
            LOG.debug("Terminal I/O state check failed", e);
            return false;
        }
    }

    /**
     * Check if the current platform is known to have JNA issues
     *
     * @return true if platform commonly has JNA issues in IntelliJ plugin context
     */
    public static boolean isPlatformWithJnaIssues() {
        // Windows is known to have JNA issues, Linux might have similar issues
        return SystemInfo.isWindows || SystemInfo.isLinux;
    }

    /**
     * Get platform-specific error message for JNA issues
     *
     * @return Descriptive error message for the current platform
     */
    public static String getJnaIssueDescription() {
        if (SystemInfo.isWindows) {
            return "Windows JNA library issue - using fallback process detection";
        } else if (SystemInfo.isLinux) {
            return "Linux JNA library issue - using fallback process detection";
        } else {
            return "Platform JNA library issue - using fallback process detection";
        }
    }
}
