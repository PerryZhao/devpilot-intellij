package com.zhongan.devpilot.actions.toolbar;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.zhongan.devpilot.actions.notifications.DevPilotNotification;
import com.zhongan.devpilot.gui.toolwindows.chat.DevPilotChatToolWindowService;
import com.zhongan.devpilot.util.DevPilotMessageBundle;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import org.jetbrains.annotations.NotNull;

public class ToolbarReloadWebViewAction extends AnAction {
    public ToolbarReloadWebViewAction() {
        super(DevPilotMessageBundle.get("devpilot.toolbarReloadWebViewAction.text"),
                DevPilotMessageBundle.get("devpilot.toolbarReloadWebViewAction.text"),
                AllIcons.Javaee.UpdateRunningApplication);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        var project = e.getProject();
        if (project == null) return;

        var toolWindowManager = ToolWindowManager.getInstance(project);
        var window = toolWindowManager.getToolWindow("DevPilot");

        if (window == null) {
            DevPilotNotification.error("无法获取DevPilot工具窗口");
            return;
        }

        var chatService = project.getService(DevPilotChatToolWindowService.class);
        if (chatService == null) {
            DevPilotNotification.error("无法获取DevPilotChatToolWindowService服务");
            return;
        }

        var chatToolWindow = chatService.getDevPilotChatToolWindow();
        if (chatToolWindow == null) {
            DevPilotNotification.error("无法获取DevPilotChatToolWindow实例");
            return;
        }

        chatToolWindow.reload();

        window.getContentManager().removeAllContents(true);

        var contentFactory = ContentFactory.getInstance();
        var webPanel = new JPanel(new BorderLayout());

        var webComponent = chatToolWindow.getDevPilotChatToolWindowPanel();
        if (webComponent != null) {
            webPanel.add(webComponent);
            Content content = contentFactory.createContent(webPanel, "", false);
            window.getContentManager().addContent(content);
            window.getContentManager().setSelectedContent(content);
        }
    }
}
