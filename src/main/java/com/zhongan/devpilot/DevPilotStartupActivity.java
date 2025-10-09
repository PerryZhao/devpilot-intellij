package com.zhongan.devpilot;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.zhongan.devpilot.actions.editor.popupmenu.PopupMenuEditorActionGroupUtil;
import com.zhongan.devpilot.integrations.llms.aigateway.AIGatewayServiceProvider;
import com.zhongan.devpilot.listener.DevPilotLineIconListener;
import com.zhongan.devpilot.network.DevPilotAvailabilityChecker;
import com.zhongan.devpilot.settings.cache.ModelAutoPreferenceCache;
import com.zhongan.devpilot.settings.state.AIGatewaySettingsState;
import com.zhongan.devpilot.update.DevPilotUpdate;
import com.zhongan.devpilot.util.LoginUtils;

import java.util.List;

import org.jetbrains.annotations.NotNull;

public class DevPilotStartupActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        PopupMenuEditorActionGroupUtil.refreshActions(project);

        new DevPilotUpdate.DevPilotUpdateTask(project).queue();
        new DevPilotAvailabilityChecker(project).checkNetworkAndLogStatus();
        EditorFactory.getInstance().getEventMulticaster().addCaretListener(new DevPilotLineIconListener(project), project);

        syncUserPreferenceModelsOnStartup(project);
        cacheModelAutoPreferenceOnStartup(project);
    }
    
    private void syncUserPreferenceModelsOnStartup(Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (LoginUtils.isLogonNonWXUser()) {
                    var llmProvider = project.getService(AIGatewayServiceProvider.class);
                    if (llmProvider != null) {
                        List<String> fetchedModels = llmProvider.fetchUserPreferenceModels();
                        if (fetchedModels != null && !fetchedModels.isEmpty()) {
                            var aiGatewaySettings = AIGatewaySettingsState.getInstance();
                            aiGatewaySettings.setPreferenceModels(fetchedModels);
                        }
                    }
                }
            } catch (Exception e) {
                // Silently ignore errors during startup sync
            }
        });
    }

    private void cacheModelAutoPreferenceOnStartup(Project project) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (LoginUtils.isLogonNonWXUser()) {
                    var cache = ApplicationManager.getApplication().getService(ModelAutoPreferenceCache.class);
                    if (!cache.isLoaded()) {
                        var llmProvider = project.getService(AIGatewayServiceProvider.class);
                        if (llmProvider != null) {
                            var modelInfo = llmProvider.fetchModelAutoPreference();
                            if (modelInfo != null && !modelInfo.isEmpty()) {
                                cache.updateCache(modelInfo);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Silently ignore errors during startup caching
            }
        });
    }

}