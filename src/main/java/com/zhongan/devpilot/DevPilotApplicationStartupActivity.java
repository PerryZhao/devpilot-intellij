package com.zhongan.devpilot;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.startup.StartupActivity;
import com.zhongan.devpilot.agents.AgentsRunner;
import com.zhongan.devpilot.agents.BinaryManager;
import com.zhongan.devpilot.session.ChatSessionManagerService;
import com.zhongan.devpilot.sse.SSEClient;
import com.zhongan.devpilot.util.ProjectUtil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;

public class DevPilotApplicationStartupActivity implements StartupActivity.Background {
    private static final Logger LOG = Logger.getInstance(DevPilotApplicationStartupActivity.class);

    private final ScheduledExecutorService agentStateMonitorScheduler = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService agentAutoUpgradeScheduler = Executors.newSingleThreadScheduledExecutor();

    private final AtomicInteger restartFailCount = new AtomicInteger(0);

    public DevPilotApplicationStartupActivity() {
        setupAgentMonitoring();
    }

    private void setupAgentMonitoring() {
        LOG.warn("Setup agent monitoring.");
        agentStateMonitorScheduler.scheduleAtFixedRate(
                () -> {
                    boolean ok = BinaryManager.INSTANCE.currentPortAvailable();
                    if (!ok) {
                        if (!BinaryManager.INSTANCE.shouldStartAgent()) {
                            return;
                        }

                        if (!BinaryManager.INSTANCE.reStarting.compareAndSet(false, true) || AgentsRunner.initialRunning.get()) {
                            LOG.info("Agent upgrading, skip monitor.");
                            return;
                        }

                        try {
                            boolean success = AgentsRunner.INSTANCE.runAsync(Boolean.FALSE).get(30, TimeUnit.SECONDS);
                            if (success) {
                                restartFailCount.set(0);
                            } else {
                                int count = restartFailCount.incrementAndGet();
                                if (count > 3) {
                                    LOG.error("Agent连续启动失败" + count + "次，可能存在严重问题");
                                    Thread.sleep(10000);
                                }
                            }
                        } catch (Exception e) {
                            LOG.error("Restart agent failed.", e);
                        } finally {
                            BinaryManager.INSTANCE.reStarting.set(false);
                        }
                    }
                }, 2 * 60, 20, TimeUnit.SECONDS);

        agentAutoUpgradeScheduler.scheduleAtFixedRate(
                () -> {
                    boolean needed = BinaryManager.INSTANCE.checkIfAutoUpgradeNeeded();
                    if (needed) {
                        if (!BinaryManager.INSTANCE.shouldStartAgent()) {
                            return;
                        }
                        if (!BinaryManager.INSTANCE.reStarting.compareAndSet(false, true) || AgentsRunner.initialRunning.get()) {
                            LOG.info("Agent is restarting, skip upgrade.");
                            return;
                        }
                        try {
                            AgentsRunner.INSTANCE.runAsync(true);
                        } catch (Exception e) {
                            LOG.error("Upgrade agent failed.", e);
                        } finally {
                            BinaryManager.INSTANCE.reStarting.set(false);
                        }
                    }
                }, 5 * 60, 2 * 60 * 60, TimeUnit.SECONDS);
    }

    @Override
    public void runActivity(@NotNull Project project) {
        try {
            Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
            if (openProjects.length == 1) {
                BinaryManager.INSTANCE.findProcessAndKill();
            }
            LOG.warn("Running agents for project:" + ProjectUtil.getProjectIdentifier(project) + ", opened project length is:" + openProjects.length + ".");
            project.getService(ChatSessionManagerService.class);
            AgentsRunner.INSTANCE.addRefreshObserver(SSEClient.getInstance(project));
            AgentsRunner.INSTANCE.runAsync(Boolean.FALSE);
        } catch (Exception e) {
            LOG.warn("Error occurred while running agents.", e);
        }
    }
}