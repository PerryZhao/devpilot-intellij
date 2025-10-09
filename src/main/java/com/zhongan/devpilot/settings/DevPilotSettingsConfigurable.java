package com.zhongan.devpilot.settings;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.NlsContexts;
import com.zhongan.devpilot.actions.editor.popupmenu.PopupMenuEditorActionGroupUtil;
import com.zhongan.devpilot.agents.AgentsRunner;
import com.zhongan.devpilot.agents.BinaryManager;
import com.zhongan.devpilot.integrations.llms.aigateway.AIGatewayServiceProvider;
import com.zhongan.devpilot.settings.state.AIGatewaySettingsState;
import com.zhongan.devpilot.settings.state.AvailabilityCheck;
import com.zhongan.devpilot.settings.state.ChatShortcutSettingState;
import com.zhongan.devpilot.settings.state.CompletionSettingsState;
import com.zhongan.devpilot.settings.state.DevPilotLlmSettingsState;
import com.zhongan.devpilot.settings.state.LanguageSettingsState;
import com.zhongan.devpilot.settings.state.LocalRagSettingsState;
import com.zhongan.devpilot.settings.state.PersonalAdvancedSettingsState;
import com.zhongan.devpilot.util.ConfigChangeUtils;
import com.zhongan.devpilot.util.ConfigurableUtils;
import com.zhongan.devpilot.util.DevPilotMessageBundle;
import com.zhongan.devpilot.util.LoginUtils;

import java.io.File;
import java.util.List;

import javax.swing.JComponent;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class DevPilotSettingsConfigurable implements Configurable, Disposable {
    private static final Logger LOG = Logger.getInstance(DevPilotSettingsConfigurable.class);

    private DevPilotSettingsComponent settingsComponent;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return DevPilotMessageBundle.get("devpilot.settings");
    }

    @Override
    public @Nullable JComponent createComponent() {
        var settings = DevPilotLlmSettingsState.getInstance();
        settingsComponent = new DevPilotSettingsComponent(this, settings);
        ConfigurableUtils.setConfigurableCache(settingsComponent);
        return settingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        var settings = DevPilotLlmSettingsState.getInstance();
        var languageSettings = LanguageSettingsState.getInstance();
        var chatShortcutSettings = ChatShortcutSettingState.getInstance();
        var languageIndex = settingsComponent.getLanguageIndex();
        var logLanguageIndex = settingsComponent.getGitLogLanguageIndex();
        var personalAdvancedSettings = PersonalAdvancedSettingsState.getInstance();

        var methodInlayPresentationDisplayIndex = settingsComponent.getMethodInlayPresentationDisplayIndex();
        var completionEnable = CompletionSettingsState.getInstance().getEnable();
        Boolean enable = AvailabilityCheck.getInstance().getEnable();
        Boolean localRagEnabled = LocalRagSettingsState.getInstance().getEnable();
        Boolean exceptionAssistantEnabled = personalAdvancedSettings.isExceptionAssistantEnabled();

        // Check CLI settings and preference models
        boolean cliSettingsModified = false;
        boolean preferenceModelsModified = false;
        if (settingsComponent.isCliAvailable()) {
            var aiGatewaySettings = AIGatewaySettingsState.getInstance();
            cliSettingsModified = settingsComponent.getAutoAuthentication() != aiGatewaySettings.isAutoAuthentication()
                    || settingsComponent.getSyncMcpServerConfig() != aiGatewaySettings.isSyncMcpServerConfig();
            preferenceModelsModified = !settingsComponent.getPreferenceModels().equals(aiGatewaySettings.getPreferenceModels());
        }

        return !settingsComponent.getFullName().equals(settings.getFullName())
                || !languageIndex.equals(languageSettings.getLanguageIndex())
                || !logLanguageIndex.equals(languageSettings.getGitLogLanguageIndex())
                || !methodInlayPresentationDisplayIndex.equals(chatShortcutSettings.getDisplayIndex())
                || !settingsComponent.getCompletionEnabled() == (completionEnable)
                || !settingsComponent.getStatusCheckEnabled() == (enable)
                || !settingsComponent.getLocalRagEnabled() == (localRagEnabled)
                || !settingsComponent.getExceptionAssistantEnabled() == (exceptionAssistantEnabled)
                || !settingsComponent.getLocalStoragePath().equals(personalAdvancedSettings.getLocalStorage())
                || cliSettingsModified
                || preferenceModelsModified;
    }

    private String verifyLocalStorage(String path) {
        if (StringUtils.isBlank(path)) {
            return DevPilotMessageBundle.get("devpilot.settings.localStorageLabel.blank");
        }

        File localStoragePathFile = new File(path);
        if (!localStoragePathFile.exists() && !localStoragePathFile.mkdirs()) {
            return DevPilotMessageBundle.get("devpilot.settings.localStorageLabel.illegal");
        }
        try {
            File testFile = new File(localStoragePathFile, ".permission.txt");
            testFile.createNewFile();
            if (!testFile.exists()) {
                testFile.delete();
                return DevPilotMessageBundle.get("devpilot.settings.localStorageLabel.no.permission");
            } else {
                testFile.delete();
                return StringUtils.EMPTY;
            }
        } catch (Exception e) {
            LOG.warn("Exception occurred while verifying local storage path.", e);
            return DevPilotMessageBundle.get("devpilot.settings.localStorageLabel.no.permission");
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        String localStoragePath = settingsComponent.getLocalStoragePath();
        // 校验是否是目录，是否有写权限
        String localStorageValidated = verifyLocalStorage(localStoragePath);
        if (!StringUtils.isEmpty(localStorageValidated)) {
            throw new ConfigurationException(localStorageValidated);
        }

        var settings = DevPilotLlmSettingsState.getInstance();
        settings.setFullName(settingsComponent.getFullName());

        var languageSettings = LanguageSettingsState.getInstance();
        Integer languageIndex = settingsComponent.getLanguageIndex();
        Integer logLanguageIndex = settingsComponent.getGitLogLanguageIndex();

        // if language changed, refresh webview
        if (!languageIndex.equals(languageSettings.getLanguageIndex())) {
            ConfigChangeUtils.localeChanged(languageIndex);
        }

        languageSettings.setLanguageIndex(languageIndex);
        languageSettings.setGitLogLanguageIndex(logLanguageIndex);

        var chatShortcutSettings = ChatShortcutSettingState.getInstance();
        var methodInlayPresentationDisplayIndex = settingsComponent.getMethodInlayPresentationDisplayIndex();
        chatShortcutSettings.setDisplayIndex(methodInlayPresentationDisplayIndex);

        PopupMenuEditorActionGroupUtil.refreshActions(null);

        var personalAdvancedSettings = PersonalAdvancedSettingsState.getInstance();

        try {
            if (!settingsComponent.getLocalStoragePath().equals(personalAdvancedSettings.getLocalStorage())) {
                String oldPath = personalAdvancedSettings.getLocalStorage();
                personalAdvancedSettings.setLocalStorage(localStoragePath);
                BinaryManager.INSTANCE.findProcessAndKill(new File(oldPath));
                AgentsRunner.INSTANCE.runAsync(Boolean.TRUE);
            }
        } catch (Exception e) {
            LOG.warn("Error occurred while running agents.", e);
        }

        CompletionSettingsState completionSettings = CompletionSettingsState.getInstance();
        completionSettings.setEnable(settingsComponent.getCompletionEnabled());

        AvailabilityCheck availabilityCheck = AvailabilityCheck.getInstance();
        availabilityCheck.setEnable(settingsComponent.getStatusCheckEnabled());

        // 保存异常助手功能设置
        personalAdvancedSettings.setExceptionAssistantEnabled(settingsComponent.getExceptionAssistantEnabled());

        LocalRagSettingsState localRagSettings = LocalRagSettingsState.getInstance();
        var pastEnable = localRagSettings.getEnable();
        localRagSettings.setEnable(settingsComponent.getLocalRagEnabled());

        if (LoginUtils.isLogonNonWXUser()) {
            var aiGatewaySettings = AIGatewaySettingsState.getInstance();
            aiGatewaySettings.setAutoAuthentication(settingsComponent.getAutoAuthentication());
            aiGatewaySettings.setSyncMcpServerConfig(settingsComponent.getSyncMcpServerConfig());

            var currentPreferenceModels = aiGatewaySettings.getPreferenceModels();
            var newPreferenceModels = settingsComponent.getPreferenceModels();
            customizedUserPreferenceModels(aiGatewaySettings, currentPreferenceModels, newPreferenceModels);
        }
    }

    private void customizedUserPreferenceModels(AIGatewaySettingsState aiGatewaySettings, List<String> currentPreferenceModels, List<String> newPreferenceModels) {
        if (!newPreferenceModels.equals(currentPreferenceModels)) {
            aiGatewaySettings.setPreferenceModels(newPreferenceModels);
            try {
                Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
                if (openProjects.length > 0) {
                    var llmProvider = openProjects[0].getService(AIGatewayServiceProvider.class);
                    if (llmProvider != null) {
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            try {
                                llmProvider.customizedUserPreferenceModels(newPreferenceModels);
                            } catch (Exception e) {
                                LOG.warn("Failed to update preference models on server", e);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                LOG.warn("Failed to update preference models on server", e);
            }
        }
    }

    @Override
    public void reset() {
        var settings = DevPilotLlmSettingsState.getInstance();
        settingsComponent.setFullName(settings.getFullName());

        var languageSettings = LanguageSettingsState.getInstance();
        settingsComponent.setLanguageIndex(languageSettings.getLanguageIndex());
        settingsComponent.setGitLogLanguageIndex(languageSettings.getGitLogLanguageIndex());

        var chatShortcutSettings = ChatShortcutSettingState.getInstance();
        settingsComponent.setMethodInlayPresentationDisplayIndex(chatShortcutSettings.getDisplayIndex());

        var personalAdvancedSettings = PersonalAdvancedSettingsState.getInstance();
        String localStorage = personalAdvancedSettings.getLocalStorage();
        settingsComponent.setLocalStoragePath(StringUtils.defaultString(localStorage, BinaryManager.INSTANCE.getDefaultHomePath().toFile().getAbsolutePath()));

        CompletionSettingsState completionSettings = CompletionSettingsState.getInstance();
        settingsComponent.setCompletionEnabled(completionSettings.getEnable());

        AvailabilityCheck availabilityCheck = AvailabilityCheck.getInstance();
        settingsComponent.setStatusCheckEnabled(availabilityCheck.getEnable());

        // 重置异常助手功能设置
        settingsComponent.setExceptionAssistantEnabled(personalAdvancedSettings.isExceptionAssistantEnabled());

        LocalRagSettingsState localRagSettings = LocalRagSettingsState.getInstance();
        settingsComponent.setLocalRagRadioEnabled(localRagSettings.getEnable());

        // Reset CLI settings if CLI is available
        if (settingsComponent.isCliAvailable()) {
            var aiGatewaySettings = AIGatewaySettingsState.getInstance();
            settingsComponent.setAutoAuthentication(aiGatewaySettings.isAutoAuthentication());
            settingsComponent.setSyncMcpServerConfig(aiGatewaySettings.isSyncMcpServerConfig());
            // Reset preference models
            settingsComponent.setPreferenceModels(aiGatewaySettings.getPreferenceModels());
        }
    }

    @Override
    public void dispose() {
    }
}
