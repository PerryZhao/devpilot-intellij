package com.zhongan.devpilot.agents;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.twelvemonkeys.util.CollectionUtil;
import com.zhongan.devpilot.mcp.McpConfigurationHandler;
import com.zhongan.devpilot.util.ProcessUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;

public class AgentsRunner {
    private static final Logger LOG = Logger.getInstance(AgentsRunner.class);

    public static final AgentsRunner INSTANCE = new AgentsRunner();

    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public static volatile AtomicBoolean initialRunning = new AtomicBoolean(false);

    public synchronized boolean run(boolean force) {
        initialRunning.set(true);
        try {
            File homeDir = BinaryManager.INSTANCE.getHomeDir();
            if (homeDir == null) {
                LOG.warn("Home dir is null, skip running DevPilot-Agents.");
                return false;
            }
            BinaryManager.AgentCheckResult checkRes = BinaryManager.INSTANCE.checkIfAgentRunning(homeDir);
            if (!force && checkRes.isRunning()) {
                LOG.info("Skip running DevPilot-Agents for already running.");
                return true;
            }
            BinaryManager.INSTANCE.findProcessAndKill();
            boolean processRes = BinaryManager.INSTANCE.postProcessBeforeRunning(homeDir);
            if (!processRes) {
                LOG.info("Skip running DevPilot-Agents for failure of init binary.");
                return false;
            }
            return doRun(homeDir);
        } finally {
            initialRunning.set(false);

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                try {
                    McpConfigurationHandler.INSTANCE.initialMcpServer();
                } catch (Exception e) {
                    LOG.warn("Failed to connect to SSE server in AgentsRunner finally block.", e);
                }
            });
        }
    }

    protected boolean doRun(File homeDir) {
        if (homeDir == null) {
            return false;
        }
        try {
            int port = getAvailablePort();
            List<String> commands = createCommand(BinaryManager.INSTANCE.getBinaryPath(homeDir), port);
            ProcessBuilder builder = new ProcessBuilder(commands);
            builder.directory(homeDir);

            Map<String, String> env = builder.environment();
            String pathVariableValue = PathEnvironmentVariableUtil.getPathVariableValue();
            if (StringUtils.isNotBlank(pathVariableValue)) {
                env.put("PATH", pathVariableValue);
                LOG.info("Setting PATH env to pathVariableValue: " + pathVariableValue);
            }
            LOG.warn("++++++++++++++++++++++++++++++++++++++++++++++++");
            LOG.warn(commands.stream().collect(Collectors.joining()));
            LOG.warn("++++++++++++++++++++++++++++++++++++++++++++++++");

            Process process = builder.start();

            Thread readerThread = new Thread(() -> {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                try {
                    while ((line = reader.readLine()) != null) {
                        LOG.warn("--------------------------------------");
                        LOG.warn(line);
                        LOG.warn("--------------------------------------");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            readerThread.start();

            boolean started = waitForPortAvailable(port, 30, TimeUnit.SECONDS);

            LOG.warn("-----------------xyz---------------------" + started);

            boolean aliveFlag = process.isAlive();
            if (aliveFlag) {
                long pid = NumberUtils.LONG_ZERO;
                try {
                    pid = process.pid();
                } catch (Exception e) {
                    LOG.warn("Error occurred while getting pid from process.", e);
                }
                writeInfoFile(homeDir, ProcessUtils.findDevPilotAgentPidList(pid), port);
            }
            return aliveFlag;
        } catch (Exception e) {
            LOG.warn("Failed to run DevPilot-Agents.", e);
            return false;
        }
    }

    public synchronized boolean run() throws Exception {
        return run(false);
    }

    protected int getAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            LOG.warn("No available port", e);
            throw new RuntimeException("No available port", e);
        }
    }

    public void writeInfoFile(File homeDir, List<Long> pids, int port) {
        if (homeDir != null) {
            File infoFile = new File(homeDir, BinaryManager.INSTANCE.getIdeInfoPath());
            try (FileWriter writer = new FileWriter(infoFile)) {
                writer.write(port + System.lineSeparator());
                for (Long pid : pids) {
                    writer.write(pid + System.lineSeparator());
                }
                LOG.info(String.format("Write info file to %s with port %s success.", homeDir.getName(), port));
            } catch (IOException e) {
                LOG.warn(String.format("Failed to write info file: %s.", homeDir.getName()), e);
            }
        }
    }

    private static boolean waitForPortAvailable(int port, long timeout, TimeUnit timeUnit) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeUnit.toMillis(timeout);

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            try (Socket socket = new Socket("localhost", port)) {
                // 如果连接成功，说明端口已可用
                return true;
            } catch (IOException e) {
                // 端口不可用，稍等片刻后重试
                try {
                    Thread.sleep(500); // 每次等待500毫秒
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }

        return false; // 超时仍未启动成功
    }

    protected List<String> createCommand(@NotNull String binaryPath, int port) {
        List<String> commands = new ArrayList<>();
        if (ProcessUtils.isWindowsPlatform()) {
            String command = "cmd";
            File cmdFile = new File(System.getenv("WINDIR") + "\\system32\\cmd.exe");
            if (!cmdFile.exists()) {
                cmdFile = new File("c:\\Windows\\system32\\cmd.exe");
            }

            if (cmdFile.exists()) {
                command = "\"" + cmdFile.getAbsolutePath() + "\"";
            }

            commands.add(command);
            commands.add("/c");
        }

        if (ProcessUtils.isWindowsPlatform()) {
            binaryPath = binaryPath.replace("(", "^(").replace(")", "^)").replace("&", "^&").replace(">", "^>").replace("<", "^<").replace("|", "^|");
            binaryPath = "\"" + binaryPath + "\"";
        }

        commands.add(binaryPath);
        commands.add("--port");
        commands.add(String.valueOf(port));

        LOG.info("Starting DevPilot-Agents with command: " + commands);
        return commands;
    }

    protected void delayKillOldProcess(Long pid) {
        executorService.schedule(() -> BinaryManager.INSTANCE.killOldProcess(pid), 30, TimeUnit.SECONDS);
    }

}
