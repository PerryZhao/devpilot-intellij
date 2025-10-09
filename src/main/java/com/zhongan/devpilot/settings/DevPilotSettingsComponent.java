package com.zhongan.devpilot.settings;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.zhongan.devpilot.DevPilotIcons;
import com.zhongan.devpilot.cli.claudecode.ClaudeTerminalService;
import com.zhongan.devpilot.integrations.llms.aigateway.AIGatewayServiceProvider;
import com.zhongan.devpilot.integrations.llms.entity.ModelInfo;
import com.zhongan.devpilot.settings.cache.ModelAutoPreferenceCache;
import com.zhongan.devpilot.settings.state.AIGatewaySettingsState;
import com.zhongan.devpilot.settings.state.AvailabilityCheck;
import com.zhongan.devpilot.settings.state.ChatShortcutSettingState;
import com.zhongan.devpilot.settings.state.CompletionSettingsState;
import com.zhongan.devpilot.settings.state.DevPilotLlmSettingsState;
import com.zhongan.devpilot.settings.state.LanguageSettingsState;
import com.zhongan.devpilot.settings.state.LocalRagSettingsState;
import com.zhongan.devpilot.settings.state.PersonalAdvancedSettingsState;
import com.zhongan.devpilot.util.DevPilotMessageBundle;
import com.zhongan.devpilot.util.LoginUtils;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.NotNull;

public class DevPilotSettingsComponent {

    private final JPanel mainPanel;

    private final JBTextField fullNameField;

    private final JBRadioButton localRagRadio;

    private final JBRadioButton autoCompletionRadio;

    private final JBRadioButton statusCheckRadio;

    // 异常助手功能开关
    private final JBRadioButton exceptionAssistantRadio;

    private Integer index;

    private ComboBox<String> languageComboBox;

    private Integer gitLogLanguageIndex;

    private ComboBox<String> gitLogLanguageComboBox;

    private Integer methodInlayPresentationDisplayIndex;

    private ComboBox<String> methodInlayPresentationDisplayComboBox;

    private final JBTextField localStorageField;

    // CLI related components
    private final JBRadioButton autoAuthenticationRadio;

    private final JBRadioButton syncMcpServerConfigRadio;

    private final ClaudeTerminalService claudeTerminalService;

    private JBList<String> preferenceModelsList;

    private DefaultListModel<String> preferenceModelsListModel;

    private JButton syncPreferenceModelsButton;

    public DevPilotSettingsComponent(DevPilotSettingsConfigurable devPilotSettingsConfigurable, DevPilotLlmSettingsState settings) {
        fullNameField = new JBTextField(settings.getFullName(), 20);

        var instance = LanguageSettingsState.getInstance();
        index = instance.getLanguageIndex();

        Integer languageIndex = LanguageSettingsState.getInstance().getLanguageIndex();
        Integer logLanguageIndex = LanguageSettingsState.getInstance().getGitLogLanguageIndex();

        localRagRadio = new JBRadioButton(
                DevPilotMessageBundle.get("devpilot.settings.local.rag.desc"),
                LocalRagSettingsState.getInstance().getEnable());
        autoCompletionRadio = new JBRadioButton(
                DevPilotMessageBundle.get("devpilot.settings.service.code.completion.desc"),
                CompletionSettingsState.getInstance().getEnable());
        statusCheckRadio = new JBRadioButton(DevPilotMessageBundle.get("devpilot.settings.service.status.check.enable.desc"),
                AvailabilityCheck.getInstance().getEnable());

        var personalAdvancedSettings = PersonalAdvancedSettingsState.getInstance();
        exceptionAssistantRadio = new JBRadioButton(
                DevPilotMessageBundle.get("devpilot.settings.exception.assistant.desc"),
                personalAdvancedSettings.isExceptionAssistantEnabled());

        methodInlayPresentationDisplayIndex = ChatShortcutSettingState.getInstance().getDisplayIndex();
        Integer inlayPresentationDisplayIndex = ChatShortcutSettingState.getInstance().getDisplayIndex();
        localStorageField = new JBTextField(personalAdvancedSettings.getLocalStorage(), 20);

        // Initialize CLI related components
        claudeTerminalService = ClaudeTerminalService.INSTANCE;
        var aiGatewaySettings = AIGatewaySettingsState.getInstance();
        autoAuthenticationRadio = new JBRadioButton(
                DevPilotMessageBundle.get("devpilot.settings.cli.auto-authentication"),
                aiGatewaySettings.isAutoAuthentication());
        syncMcpServerConfigRadio = new JBRadioButton(
                DevPilotMessageBundle.get("devpilot.settings.cli.sync-mcp-configuration"),
                aiGatewaySettings.isSyncMcpServerConfig());

        FormBuilder formBuilder = FormBuilder.createFormBuilder()
                .addComponent(UI.PanelFactory.panel(fullNameField)
                        .withLabel(DevPilotMessageBundle.get("devpilot.setting.displayNameFieldLabel"))
                        .resizeX(false)
                        .createPanel())
                .addComponent(createLanguageSectionPanel(languageIndex))
                .addComponent(createGitLogLanguageSectionPanel(logLanguageIndex))
                .addComponent(createMethodShortcutDisplayModeSectionPanel(inlayPresentationDisplayIndex))
                .addComponent(UI.PanelFactory.panel(localStorageField)
                        .withLabel(DevPilotMessageBundle.get("devpilot.settings.localStorageLabel"))
                        .resizeX(false)
                        .createPanel())
                .addComponent(new TitledSeparator(
                        DevPilotMessageBundle.get("devpilot.settings.local.rag.title")))
                .addComponent(localRagRadio)
                .addComponent(createTextArea(DevPilotMessageBundle.get("devpilot.settings.local.rag.explain")))
                .addVerticalGap(8)
                .addComponent(new TitledSeparator(
                        DevPilotMessageBundle.get("devpilot.settings.service.code.completion.title")))
                .addComponent(autoCompletionRadio)
                .addVerticalGap(8)
                .addComponent(new TitledSeparator(
                        DevPilotMessageBundle.get("devpilot.settings.service.status.check.title")))
                .addComponent(statusCheckRadio)
                .addVerticalGap(8)
                .addComponent(new TitledSeparator(
                        DevPilotMessageBundle.get("devpilot.settings.exception.assistant.title")))
                .addComponent(exceptionAssistantRadio);

        if (LoginUtils.isLogonNonWXUser()) {
            formBuilder.addVerticalGap(8)
                    .addComponent(new TitledSeparator(DevPilotMessageBundle.get("devpilot.settings.cli.settings")))
                    .addComponent(autoAuthenticationRadio)
                    .addComponent(syncMcpServerConfigRadio);

            preferenceModelsListModel = new DefaultListModel<>();
            for (String model : aiGatewaySettings.getPreferenceModels()) {
                preferenceModelsListModel.addElement(model);
            }
            preferenceModelsList = new JBList<>(preferenceModelsListModel);
            syncPreferenceModelsButton = new JButton(DevPilotMessageBundle.get("devpilot.settings.preference.models.sync"));
            syncPreferenceModelsButton.addActionListener(e -> syncPreferenceModels());

            formBuilder.addVerticalGap(8)
                    .addComponent(createPreferenceModelsTitlePanel())
                    .addComponent(createPreferenceModelsListPanel());
        }

        mainPanel = formBuilder
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private JComponent createPreferenceModelsTitlePanel() {
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.X_AXIS));

        JLabel titleLabel = new JLabel(DevPilotMessageBundle.get("devpilot.settings.preference.models.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titlePanel.add(titleLabel);

        titlePanel.add(Box.createHorizontalStrut(5));

        JButton helpButton = new JButton(AllIcons.General.ShowInfos);
        helpButton.setToolTipText(DevPilotMessageBundle.get("devpilot.settings.preference.models.help.tooltip"));
        helpButton.addActionListener(e -> showModelInfoDialog());
        helpButton.setFocusable(false);
        helpButton.setBorderPainted(false);
        helpButton.setContentAreaFilled(false);
        helpButton.setPreferredSize(new Dimension(16, 16));
        titlePanel.add(helpButton);

        titlePanel.add(Box.createHorizontalGlue());
        titlePanel.add(syncPreferenceModelsButton);
        return titlePanel;
    }

    private JComponent createPreferenceModelsListPanel() {
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(preferenceModelsList)
                .setMoveUpAction(anActionButton -> {
                    int selectedIndex = preferenceModelsList.getSelectedIndex();
                    if (selectedIndex > 0) {
                        String selectedModel = preferenceModelsListModel.getElementAt(selectedIndex);
                        preferenceModelsListModel.removeElementAt(selectedIndex);
                        preferenceModelsListModel.insertElementAt(selectedModel, selectedIndex - 1);
                        preferenceModelsList.setSelectedIndex(selectedIndex - 1);
                    }
                })
                .setMoveDownAction(anActionButton -> {
                    int selectedIndex = preferenceModelsList.getSelectedIndex();
                    if (selectedIndex < preferenceModelsListModel.getSize() - 1) {
                        String selectedModel = preferenceModelsListModel.getElementAt(selectedIndex);
                        preferenceModelsListModel.removeElementAt(selectedIndex);
                        preferenceModelsListModel.insertElementAt(selectedModel, selectedIndex + 1);
                        preferenceModelsList.setSelectedIndex(selectedIndex + 1);
                    }
                })
                .setAddAction(anActionButton -> {
                    if (preferenceModelsListModel.getSize() >= 5) {
                        // Show maximum limit reached message
                        JOptionPane.showMessageDialog(mainPanel,
                                DevPilotMessageBundle.get("devpilot.settings.preference.models.max.reached"),
                                "Info", javax.swing.JOptionPane.INFORMATION_MESSAGE, DevPilotIcons.SYSTEM_ICON);
                        return;
                    }

                    // Ensure cache is loaded before showing add dialog
                    ensureCacheLoadedAndShowAddDialog();
                })
                .setRemoveAction(anActionButton -> {
                    int selectedIndex = preferenceModelsList.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        if (preferenceModelsListModel.getSize() <= 1) {
                            // Show minimum limit reached message
                            JOptionPane.showMessageDialog(mainPanel,
                                    DevPilotMessageBundle.get("devpilot.settings.preference.models.min.required"),
                                    "Info", javax.swing.JOptionPane.INFORMATION_MESSAGE, DevPilotIcons.SYSTEM_ICON);
                            return;
                        }
                        preferenceModelsListModel.removeElementAt(selectedIndex);
                        if (selectedIndex < preferenceModelsListModel.getSize()) {
                            preferenceModelsList.setSelectedIndex(selectedIndex);
                        } else if (preferenceModelsListModel.getSize() > 0) {
                            preferenceModelsList.setSelectedIndex(preferenceModelsListModel.getSize() - 1);
                        }
                    }
                });

        JPanel listPanel = decorator.createPanel();
        listPanel.setPreferredSize(new Dimension(400, 150));

        return listPanel;
    }

    private JComponent createTextArea(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setEditable(false);
        textArea.setBackground(null);
        textArea.setBorder(null);
        return textArea;
    }

    private @NotNull JComponent createMethodShortcutDisplayModeSectionPanel(Integer inlayPresentationDisplayIndex) {
        methodInlayPresentationDisplayComboBox = new ComboBox<>();
        methodInlayPresentationDisplayComboBox.addItem(DevPilotMessageBundle.get("devpilot.settings.methodShortcutHidden"));
        methodInlayPresentationDisplayComboBox.addItem(DevPilotMessageBundle.get("devpilot.settings.methodShortcutInlineDisplay"));
        methodInlayPresentationDisplayComboBox.addItem(DevPilotMessageBundle.get("devpilot.settings.methodShortcutGroupDisplay"));
        methodInlayPresentationDisplayComboBox.setSelectedIndex(inlayPresentationDisplayIndex);

        methodInlayPresentationDisplayComboBox.addActionListener(e -> {
            var box = (ComboBox<?>) e.getSource();
            methodInlayPresentationDisplayIndex = box.getSelectedIndex();
        });

        var panel = UI.PanelFactory.grid()
                .add(UI.PanelFactory.panel(methodInlayPresentationDisplayComboBox)
                        .withLabel(DevPilotMessageBundle.get("devpilot.settings.methodShortcutDisplayModeLabel"))
                        .resizeX(false))
                .createPanel();
        panel.setBorder(JBUI.Borders.emptyLeft(0));
        return panel;
    }

    public JPanel createLanguageSectionPanel(Integer languageIndex) {
        languageComboBox = new ComboBox<>();
        languageComboBox.addItem("English");
        languageComboBox.addItem("中文");
        languageComboBox.setSelectedIndex(languageIndex);

        languageComboBox.addActionListener(e -> {
            var box = (ComboBox<?>) e.getSource();
            index = box.getSelectedIndex();
        });

        var panel = UI.PanelFactory.grid()
                .add(UI.PanelFactory.panel(languageComboBox)
                        .withLabel(DevPilotMessageBundle.get("devpilot.setting.language"))
                        .resizeX(false))
                .createPanel();
        panel.setBorder(JBUI.Borders.emptyLeft(0));
        return panel;
    }

    public JPanel createGitLogLanguageSectionPanel(Integer logLanguageIndex) {
        gitLogLanguageComboBox = new ComboBox<>();
        gitLogLanguageComboBox.addItem("English");
        gitLogLanguageComboBox.addItem("中文");
        gitLogLanguageComboBox.setSelectedIndex(logLanguageIndex);

        gitLogLanguageComboBox.addActionListener(e -> {
            var box = (ComboBox<?>) e.getSource();
            gitLogLanguageIndex = box.getSelectedIndex();
        });

        var panel = UI.PanelFactory.grid()
                .add(UI.PanelFactory.panel(gitLogLanguageComboBox)
                        .withLabel(DevPilotMessageBundle.get("devpilot.setting.gitlog.language"))
                        .resizeX(false))
                .createPanel();
        panel.setBorder(JBUI.Borders.emptyLeft(0));
        return panel;
    }

    public JPanel getPanel() {
        return mainPanel;
    }

    // Getting the full name from the settings
    public String getFullName() {
        return fullNameField.getText();
    }

    public Integer getLanguageIndex() {
        return index;
    }

    public Integer getGitLogLanguageIndex() {
        return gitLogLanguageIndex;
    }

    public Integer getMethodInlayPresentationDisplayIndex() {
        return methodInlayPresentationDisplayIndex;
    }

    public boolean getCompletionEnabled() {
        return autoCompletionRadio.isSelected();
    }

    public boolean getLocalRagEnabled() {
        return localRagRadio.isSelected();
    }

    public boolean getStatusCheckEnabled() {
        return statusCheckRadio.isSelected();
    }

    public boolean getExceptionAssistantEnabled() {
        return exceptionAssistantRadio.isSelected();
    }

    public String getLocalStoragePath() {
        return localStorageField.getText();
    }

    // For reset
    public void setFullName(String text) {
        fullNameField.setText(text);
    }

    public void setLanguageIndex(Integer index) {
        this.index = index;
        languageComboBox.setSelectedIndex(index);
    }

    public void setGitLogLanguageIndex(Integer gitLogLanguageIndex) {
        this.gitLogLanguageIndex = gitLogLanguageIndex;
        gitLogLanguageComboBox.setSelectedIndex(gitLogLanguageIndex);
    }

    public void setMethodInlayPresentationDisplayIndex(Integer methodInlayPresentationDisplayIndex) {
        this.methodInlayPresentationDisplayIndex = methodInlayPresentationDisplayIndex;
        methodInlayPresentationDisplayComboBox.setSelectedIndex(methodInlayPresentationDisplayIndex);
    }

    public void setCompletionEnabled(boolean selected) {
        autoCompletionRadio.setSelected(selected);
    }

    public void setStatusCheckEnabled(boolean selected) {
        statusCheckRadio.setSelected(selected);
    }

    public void setExceptionAssistantEnabled(boolean selected) {
        exceptionAssistantRadio.setSelected(selected);
    }

    public void setLocalRagRadioEnabled(boolean selected) {
        localRagRadio.setSelected(selected);
    }

    public void setLocalStoragePath(String text) {
        localStorageField.setText(text);
    }

    // CLI related getters and setters
    public boolean getAutoAuthentication() {
        return autoAuthenticationRadio != null && autoAuthenticationRadio.isSelected();
    }

    public boolean getSyncMcpServerConfig() {
        return syncMcpServerConfigRadio != null && syncMcpServerConfigRadio.isSelected();
    }

    public void setAutoAuthentication(boolean selected) {
        if (autoAuthenticationRadio != null) {
            autoAuthenticationRadio.setSelected(selected);
        }
    }

    public void setSyncMcpServerConfig(boolean selected) {
        if (syncMcpServerConfigRadio != null) {
            syncMcpServerConfigRadio.setSelected(selected);
        }
    }

    public boolean isCliAvailable() {
        return claudeTerminalService.isCliAvailable();
    }

    public List<String> getPreferenceModels() {
        List<String> models = new ArrayList<>();
        for (int i = 0; i < preferenceModelsListModel.getSize(); i++) {
            models.add(preferenceModelsListModel.getElementAt(i));
        }
        return models;
    }

    public void setPreferenceModels(List<String> models) {
        preferenceModelsListModel.clear();
        for (String model : models) {
            preferenceModelsListModel.addElement(model);
        }
    }

    private void resetSyncButtonState() {
        syncPreferenceModelsButton.setText(DevPilotMessageBundle.get("devpilot.settings.preference.models.sync"));
        syncPreferenceModelsButton.setEnabled(true);
    }

    private void syncPreferenceModels() {
        syncPreferenceModelsButton.setEnabled(false);
        syncPreferenceModelsButton.setText(DevPilotMessageBundle.get("devpilot.settings.preference.models.syncing"));

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Project project = ProjectManager.getInstance().getDefaultProject();
                var llmProvider = project.getService(AIGatewayServiceProvider.class);
                List<String> fetchedModels = llmProvider.fetchUserPreferenceModels();
                preferenceModelsListModel.clear();
                for (String model : fetchedModels) {
                    preferenceModelsListModel.addElement(model);
                }
                AIGatewaySettingsState.getInstance().setPreferenceModels(new ArrayList<>(fetchedModels));
                resetSyncButtonState();
            } catch (Exception e) {
                resetSyncButtonState();
            }
        });
    }

    private void showModelInfoDialog() {
        // 获取当前活动的窗口作为父组件，而不是使用mainPanel
        Window parentWindow = SwingUtilities.getWindowAncestor(mainPanel);
        if (parentWindow == null) {
            parentWindow = WindowManager.getInstance().getFrame(ProjectManager.getInstance().getDefaultProject());
        }

        // 创建加载对话框
        JPanel loadingPanel = new JPanel(new FlowLayout());
        loadingPanel.add(new JLabel(DevPilotMessageBundle.get("devpilot.settings.preference.models.loading")));
        loadingPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JDialog loadingDialog = new JDialog(parentWindow, DevPilotMessageBundle.get("devpilot.settings.preference.models.info.title"), Dialog.ModalityType.MODELESS);
        loadingDialog.setContentPane(loadingPanel);
        loadingDialog.pack();
        loadingDialog.setLocationRelativeTo(parentWindow);
        loadingDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        // 立即显示加载对话框
        loadingDialog.setVisible(true);

        // 在后台线程中获取数据
        Window finalParentWindow = parentWindow;
        Window finalParentWindow1 = parentWindow;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                Project project = ProjectManager.getInstance().getDefaultProject();
                var llmProvider = project.getService(AIGatewayServiceProvider.class);
                Map<String, Map<String, ModelInfo>> modelInfo = llmProvider.fetchModelAutoPreference();

                // Update cache with latest data
                var cache = ApplicationManager.getApplication().getService(ModelAutoPreferenceCache.class);
                cache.updateCache(modelInfo);

                // 使用SwingUtilities.invokeLater而不是ApplicationManager.invokeLater
                SwingUtilities.invokeLater(() -> {
                    // 关闭加载对话框
                    loadingDialog.dispose();

                    // 创建内容面板
                    JPanel contentPanel = new JPanel();
                    contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

                    addModelTierPanel(contentPanel, DevPilotMessageBundle.get("devpilot.settings.preference.models.tier1"), modelInfo, "tier1");
                    addModelTierPanel(contentPanel, DevPilotMessageBundle.get("devpilot.settings.preference.models.tier2"), modelInfo, "tier2");
                    addModelTierPanel(contentPanel, DevPilotMessageBundle.get("devpilot.settings.preference.models.fallback"), modelInfo, "fallback");

                    JScrollPane scrollPane = new JScrollPane(contentPanel);
                    scrollPane.setPreferredSize(new Dimension(800, 600));

                    // 创建新的对话框显示结果，使用相同的父窗口
                    JDialog resultDialog = new JDialog(finalParentWindow1, DevPilotMessageBundle.get("devpilot.settings.preference.models.info.title"), Dialog.ModalityType.MODELESS);
                    resultDialog.setContentPane(scrollPane);
                    resultDialog.pack();
                    resultDialog.setLocationRelativeTo(finalParentWindow1);
                    resultDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    resultDialog.setVisible(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    // 关闭加载对话框
                    loadingDialog.dispose();

                    // 显示错误对话框
                    JDialog errorDialog = new JDialog(finalParentWindow, DevPilotMessageBundle.get("devpilot.common.error"), Dialog.ModalityType.MODELESS);
                    JPanel errorPanel = new JPanel(new BorderLayout());
                    errorPanel.add(new JLabel(DevPilotMessageBundle.get("devpilot.settings.preference.models.info.error") + ": " + e.getMessage()), BorderLayout.CENTER);
                    errorPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
                    errorDialog.setContentPane(errorPanel);
                    errorDialog.pack();
                    errorDialog.setLocationRelativeTo(finalParentWindow);
                    errorDialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    errorDialog.setVisible(true);
                });
            }
        });
    }

    private void addModelTierPanel(JPanel parent, String tierName, Map<String, Map<String, ModelInfo>> modelInfo, String tierKey) {
        if (modelInfo == null || !modelInfo.containsKey(tierKey)) {
            return;
        }

        Map<String, ModelInfo> tierModels = modelInfo.get(tierKey);
        if (tierModels == null || tierModels.isEmpty()) {
            return;
        }

        // 创建层级面板，使用更优雅的边框样式
        JPanel tierPanel = new JPanel();
        tierPanel.setLayout(new BoxLayout(tierPanel, BoxLayout.Y_AXIS));
        tierPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                        tierName,
                        0,
                        0,
                        JBUI.Fonts.label().deriveFont(Font.BOLD)
                ),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        for (Map.Entry<String, ModelInfo> entry : tierModels.entrySet()) {
            String modelId = entry.getKey();
            ModelInfo modelDetails = entry.getValue();

            if (modelDetails == null) {
                continue;
            }

            // 创建模型面板，添加悬停效果
            JPanel modelPanel = new JPanel(new BorderLayout());
            modelPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false), 1),
                    BorderFactory.createEmptyBorder(12, 15, 12, 15)
            ));
            modelPanel.setBackground(JBUI.CurrentTheme.List.background(false, false));

            // 详情面板
            JPanel detailsPanel = new JPanel();
            detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
            detailsPanel.setOpaque(false);

            String supportsImages = String.valueOf(modelDetails.isSupportsImages());
            String inputPrice = formatPrice(modelDetails.getInputTokenPrice());
            String outputPrice = formatPrice(modelDetails.getOutputTokenPrice());
            String currency = modelDetails.getCurrency() != null ? modelDetails.getCurrency() : "USD";

            // 使用更清晰的HTML格式
            String details = String.format(
                    "<html>" +
                            "<div style='font-family: %s; line-height: 1.4;'>" +
                            "<b style='font-size: 13px; color: #%s;'>%s</b><br>" +
                            "<span style='font-size: 11px; color: #%s;'>%s: <b>%s</b></span><br>" +
                            "<span style='font-size: 11px; color: #%s;'>%s: <b>%s %s</b></span><br>" +
                            "<span style='font-size: 11px; color: #%s;'>%s: <b>%s %s</b></span>" +
                            "</div>" +
                            "</html>",
                    JBUI.Fonts.label().getFamily(),
                    Integer.toHexString(JBUI.CurrentTheme.Label.foreground().getRGB() & 0xFFFFFF),
                    modelId,
                    Integer.toHexString(JBUI.CurrentTheme.Label.disabledForeground().getRGB() & 0xFFFFFF),
                    DevPilotMessageBundle.get("devpilot.settings.preference.models.supports.images"), supportsImages,
                    Integer.toHexString(JBUI.CurrentTheme.Label.disabledForeground().getRGB() & 0xFFFFFF),
                    DevPilotMessageBundle.get("devpilot.settings.preference.models.input.price"), inputPrice, currency,
                    Integer.toHexString(JBUI.CurrentTheme.Label.disabledForeground().getRGB() & 0xFFFFFF),
                    DevPilotMessageBundle.get("devpilot.settings.preference.models.output.price"), outputPrice, currency
            );

            JLabel detailsLabel = new JLabel(details);
            detailsPanel.add(detailsLabel);

            // 组装面板
            modelPanel.add(detailsPanel, BorderLayout.CENTER);

            // 添加面板间距
            tierPanel.add(modelPanel);
            tierPanel.add(Box.createVerticalStrut(8));
        }

        parent.add(tierPanel);
        parent.add(Box.createVerticalStrut(15));
    }

    private void ensureCacheLoadedAndShowAddDialog() {
        var cache = ApplicationManager.getApplication().getService(ModelAutoPreferenceCache.class);

        if (cache.isLoaded()) {
            // Cache is already loaded, show add dialog directly
            showAddModelDialog();
        } else {
            // Cache is not loaded, load it first
            refreshCacheAndShowAddDialog();
        }
    }

    private void showAddModelDialog() {
        var cache = ApplicationManager.getApplication().getService(ModelAutoPreferenceCache.class);

        // Get currently selected models to exclude them
        Set<String> existingModels = new HashSet<>();
        for (int i = 0; i < preferenceModelsListModel.getSize(); i++) {
            existingModels.add(preferenceModelsListModel.getElementAt(i));
        }

        String[] availableModels = cache.getOrderedModelIds(existingModels);
        if (availableModels.length > 0) {
            // Show selection dialog with available models in tier order
            String selectedModel = (String) JOptionPane.showInputDialog(mainPanel,
                    DevPilotMessageBundle.get("devpilot.settings.preference.models.add"),
                    DevPilotMessageBundle.get("devpilot.settings.preference.models.add.title"),
                    javax.swing.JOptionPane.QUESTION_MESSAGE, DevPilotIcons.SYSTEM_ICON,
                    availableModels, availableModels[0]);

            if (selectedModel != null) {
                preferenceModelsListModel.addElement(selectedModel);
            }
        } else {
            // No available models to select, show manual input
            showManualInputDialog();
        }
    }

    private void showManualInputDialog() {
        String newModel = (String) JOptionPane.showInputDialog(mainPanel,
                DevPilotMessageBundle.get("devpilot.settings.preference.models.add"),
                DevPilotMessageBundle.get("devpilot.settings.preference.models.add.title"),
                javax.swing.JOptionPane.QUESTION_MESSAGE, DevPilotIcons.SYSTEM_ICON, null, null);

        if (newModel != null && !newModel.trim().isEmpty()) {
            String modelId = newModel.trim();
            if (!preferenceModelsListModel.contains(modelId)) {
                preferenceModelsListModel.addElement(modelId);
            }
        }
    }

    private void refreshCacheAndShowAddDialog() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                if (LoginUtils.isLogonNonWXUser()) {
                    Project project = ProjectManager.getInstance().getDefaultProject();
                    var llmProvider = project.getService(AIGatewayServiceProvider.class);
                    if (llmProvider != null) {
                        var modelInfo = llmProvider.fetchModelAutoPreference();
                        if (modelInfo != null && !modelInfo.isEmpty()) {
                            var cache = ApplicationManager.getApplication().getService(ModelAutoPreferenceCache.class);
                            cache.updateCache(modelInfo);

                            // Show add dialog on EDT
                            SwingUtilities.invokeLater(this::showAddModelDialog);
                            return;
                        }
                    }
                }

                // If we reach here, cache loading failed, show manual input
                SwingUtilities.invokeLater(this::showManualInputDialog);
            } catch (Exception e) {
                // Cache loading failed, show manual input
                SwingUtilities.invokeLater(this::showManualInputDialog);
            }
        });
    }

    private String formatPrice(Object priceObj) {
        if (priceObj == null) {
            return "0.000";
        }
        try {
            double price = Double.parseDouble(priceObj.toString());
            return String.format("%.3f", price);
        } catch (NumberFormatException e) {
            return "0.000";
        }
    }

}
