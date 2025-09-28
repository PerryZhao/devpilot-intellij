package com.zhongan.devpilot.listener;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.zhongan.devpilot.agents.BinaryManager;
import com.zhongan.devpilot.sse.SSEClient;
import com.zhongan.devpilot.util.ProjectUtil;

import org.jetbrains.annotations.NotNull;

public class MultiProjectManagerListener implements ProjectManagerListener {

    private static final Logger LOG = Logger.getInstance(MultiProjectManagerListener.class);

    public MultiProjectManagerListener() {
    }

    public void projectClosing(@NotNull Project project) {
        SSEClient.removeInstance(project);
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 1) {
            LOG.warn("Last project: " + ProjectUtil.getProjectIdentifier(project) + " closed, kill the agent process");
            BinaryManager.INSTANCE.findProcessAndKill();
        }
    }
}