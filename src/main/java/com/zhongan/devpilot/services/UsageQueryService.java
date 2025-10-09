package com.zhongan.devpilot.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.zhongan.devpilot.agents.BinaryManager;
import com.zhongan.devpilot.util.DevPilotMessageBundle;
import com.zhongan.devpilot.util.LoginUtils;
import com.zhongan.devpilot.util.OkhttpUtils;
import com.zhongan.devpilot.util.UserAgentUtils;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

import org.jetbrains.annotations.NotNull;

import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;

import static com.zhongan.devpilot.constant.DefaultConst.REMOTE_RAG_DEFAULT_HOST;

public class UsageQueryService {
    private static final Logger LOG = Logger.getInstance(UsageQueryService.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void queryUsage(Project project) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, DevPilotMessageBundle.get("devpilot.usage.query.loading"), false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    indicator.setText(DevPilotMessageBundle.get("devpilot.usage.query.fetching"));
                    indicator.setIndeterminate(true);

                    Pair<Integer, Long> portPId = BinaryManager.INSTANCE.retrieveAlivePort();
                    if (null != portPId) {
                        String url = REMOTE_RAG_DEFAULT_HOST + portPId.first + "/model/budget-usage";

                        var request = new Request.Builder()
                                .url(url)
                                .header("User-Agent", UserAgentUtils.buildUserAgent())
                                .header("Auth-Type", LoginUtils.getLoginType())
                                .get()
                                .build();

                        Call call = OkhttpUtils.getClient().newCall(request);
                        try (Response response = call.execute()) {
                            if (response.isSuccessful() && response.body() != null) {
                                indicator.setText(DevPilotMessageBundle.get("devpilot.usage.query.parsing"));
                                String responseBody = response.body().string();
                                JsonNode jsonNode = objectMapper.readTree(responseBody);

                                // 解析用量信息
                                String usageInfo = parseUsageInfo(jsonNode);

                                // 在UI线程中显示对话框
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    showUsageDialog(project, usageInfo);
                                });
                            } else {
                                ApplicationManager.getApplication().invokeLater(() -> {
                                    Messages.showWarningDialog(project,
                                            DevPilotMessageBundle.get("devpilot.usage.query.failed"),
                                            DevPilotMessageBundle.get("devpilot.usage.info.title"));
                                });
                            }
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            Messages.showWarningDialog(project,
                                    DevPilotMessageBundle.get("devpilot.usage.query.no.service"),
                                    DevPilotMessageBundle.get("devpilot.usage.info.title"));
                        });
                    }
                } catch (IOException e) {
                    LOG.warn("Failed to query usage", e);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        Messages.showErrorDialog(project,
                                DevPilotMessageBundle.get("devpilot.usage.query.error"),
                                DevPilotMessageBundle.get("devpilot.usage.info.title"));
                    });
                }
            }
        });
    }

    private static String parseUsageInfo(JsonNode jsonNode) {
        StringBuilder usage = new StringBuilder();
        usage.append(DevPilotMessageBundle.get("devpilot.usage.info.title")).append("\n");

        try {
            // 直接遍历根节点的所有字段，每个字段名就是模型名
            jsonNode.fieldNames().forEachRemaining(modelName -> {
                try {
                    JsonNode modelData = jsonNode.get(modelName);

                    // 检查cost字段是否存在且不为0
                    if (modelData.has("cost")) {
                        double cost = modelData.get("cost").asDouble();
                        if (cost != 0) {
                            // 统一使用原始模型名，不转换大小写，保持一致性
                            usage.append("\n").append(modelName).append(":\n");

                            if (modelData.has("requestTimes")) {
                                usage.append("  请求次数: ").append(modelData.get("requestTimes").asText()).append("\n");
                            }

                            if (modelData.has("promptTokens")) {
                                usage.append("  输入Token: ").append(modelData.get("promptTokens").asText()).append("\n");
                            }

                            if (modelData.has("completionTokens")) {
                                usage.append("  输出Token: ").append(modelData.get("completionTokens").asText()).append("\n");
                            }

                            if (modelData.has("cacheCreationInputTokens") && modelData.get("cacheCreationInputTokens").asLong() > 0) {
                                usage.append("  缓存创建Token: ").append(modelData.get("cacheCreationInputTokens").asText()).append("\n");
                            }

                            if (modelData.has("cacheReadInputTokens") && modelData.get("cacheReadInputTokens").asLong() > 0) {
                                usage.append("  缓存读取Token: ").append(modelData.get("cacheReadInputTokens").asText()).append("\n");
                            }

                            usage.append("  费用: ").append(String.format("%.6f", cost));
                            if (modelData.has("currency")) {
                                usage.append(" ").append(modelData.get("currency").asText());
                            }
                            usage.append("\n");
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to parse model data for: " + modelName, e);
                }
            });

            // 如果没有找到任何有费用的模型
            if (usage.length() == DevPilotMessageBundle.get("devpilot.usage.info.title").length() + 1) {
                usage.append(DevPilotMessageBundle.get("devpilot.usage.no.data"));
            }

        } catch (Exception e) {
            LOG.warn("Failed to parse usage info", e);
            usage.setLength(0);
            usage.append(DevPilotMessageBundle.get("devpilot.usage.parse.error"));
        }

        return usage.toString();
    }

    private static void showUsageDialog(Project project, String usageInfo) {
        new UsageInfoDialog(project, usageInfo).show();
    }

    private static class UsageInfoDialog extends DialogWrapper {
        private final String usageInfo;

        UsageInfoDialog(Project project, String usageInfo) {
            super(project);
            this.usageInfo = usageInfo;
            setTitle(DevPilotMessageBundle.get("devpilot.usage.info.title"));
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(JBUI.Borders.empty(10));
            panel.setBackground(UIManager.getColor("Panel.background"));

            // 创建主要内容面板
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setBorder(JBUI.Borders.empty(8));
            contentPanel.setBackground(UIManager.getColor("Panel.background"));

            // 解析并格式化显示用量信息
            String[] lines = usageInfo.split("\n");
            boolean isFirstModel = true;

            // 计算模型数量来调整样式
            int modelCount = 0;
            for (String line : lines) {
                if (line.endsWith(":") && !line.startsWith("  ") && !line.equals(DevPilotMessageBundle.get("devpilot.usage.info.title"))) {
                    modelCount++;
                }
            }

            for (String line : lines) {
                if (line.trim().isEmpty()) {
                    contentPanel.add(Box.createVerticalStrut(6));
                    continue;
                }

                if (line.endsWith(":") && !line.startsWith("  ")) {
                    // 模型名称 - 紧凑样式
                    if (!isFirstModel) {
                        contentPanel.add(Box.createVerticalStrut(8));
                    }
                    isFirstModel = false;

                    JPanel modelPanel = new JPanel(new BorderLayout());
                    modelPanel.setBackground(UIManager.getColor("SettingsTree.rowBackground"));
                    if (modelPanel.getBackground() == null) {
                        modelPanel.setBackground(UIManager.getColor("Table.gridColor"));
                    }

                    modelPanel.setBorder(JBUI.Borders.compound(
                            JBUI.Borders.customLineBottom(UIManager.getColor("Separator.foreground")),
                            JBUI.Borders.empty(4, 8, 4, 8)
                    ));
                    modelPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

                    JLabel modelLabel = new JLabel();
                    String modelName = line.replace(":", "");
                    String titleColor = String.format("#%06X",
                            UIManager.getColor("TitledBorder.titleColor") != null ?
                                    UIManager.getColor("TitledBorder.titleColor").getRGB() & 0xFFFFFF :
                                    UIManager.getColor("Label.foreground").getRGB() & 0xFFFFFF);

                    modelLabel.setText("<html>" +
                            "<div style='font-family: sans-serif; font-size: 11px;'>" +
                            "<b><font color='" + titleColor + "'>" + modelName + "</font></b>" +
                            "</div></html>");
                    modelPanel.add(modelLabel, BorderLayout.WEST);

                    contentPanel.add(modelPanel);
                    contentPanel.add(Box.createVerticalStrut(4));
                } else if (line.startsWith("  ")) {
                    // 详细信息 - 紧凑表格布局
                    String content = line.trim();
                    JPanel detailPanel = new JPanel(new BorderLayout());
                    detailPanel.setBackground(UIManager.getColor("Panel.background"));
                    detailPanel.setBorder(JBUI.Borders.empty(2, 20, 2, 8));
                    detailPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

                    if (content.contains(":")) {
                        String[] parts = content.split(":", 2);
                        if (parts.length == 2) {
                            JLabel keyLabel = new JLabel();
                            JLabel valueLabel = new JLabel();

                            String key = parts[0].trim();
                            String value = parts[1].trim();

                            if (key.equals("费用")) {
                                keyLabel.setText("<html><b><font color='#616161'>" + key + ":</font></b></html>");
                                valueLabel.setText("<html><b><font color='#E53E3E'>" + value + "</font></b></html>");
                            } else {
                                keyLabel.setText("<html><b><font color='#616161'>" + key + ":</font></b></html>");
                                valueLabel.setText("<html><b><font color='#1976D2'>" + value + "</font></b></html>");
                            }

                            keyLabel.setPreferredSize(JBUI.size(110, 18));
                            keyLabel.setVerticalAlignment(JLabel.CENTER);
                            valueLabel.setVerticalAlignment(JLabel.CENTER);

                            detailPanel.add(keyLabel, BorderLayout.WEST);
                            detailPanel.add(valueLabel, BorderLayout.CENTER);
                        } else {
                            JLabel label = new JLabel("<html><font color='#616161'>" + content + "</font></html>");
                            detailPanel.add(label, BorderLayout.CENTER);
                        }
                    } else {
                        JLabel label = new JLabel("<html><font color='#616161'>" + content + "</font></html>");
                        detailPanel.add(label, BorderLayout.CENTER);
                    }

                    contentPanel.add(detailPanel);
                } else {
                    // 标题或其他信息
                    if (!line.equals(DevPilotMessageBundle.get("devpilot.usage.info.title"))) {
                        // 其他信息（如无数据提示）- 不显示主标题，节省空间
                        JPanel infoPanel = new JPanel(new BorderLayout());
                        infoPanel.setBackground(UIManager.getColor("Panel.background"));
                        infoPanel.setBorder(JBUI.Borders.empty(10, 0, 10, 0));

                        JLabel infoLabel = new JLabel();
                        infoLabel.setText("<html><i><font color='#757575'>" + line + "</font></i></html>");
                        infoLabel.setHorizontalAlignment(JLabel.CENTER);
                        infoPanel.add(infoLabel, BorderLayout.CENTER);

                        contentPanel.add(infoPanel);
                    }
                }
            }

            // 自适应滚动面板 - 根据内容调整大小
            JBScrollPane scrollPane = new JBScrollPane(contentPanel);

            // 计算合适的对话框大小
            int contentHeight = Math.min(350, Math.max(150, modelCount * 120 + 50));
            int contentWidth = 450;

            scrollPane.setPreferredSize(JBUI.size(contentWidth, contentHeight));
            scrollPane.setBorder(JBUI.Borders.customLine(UIManager.getColor("Component.borderColor"), 1));
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            scrollPane.getViewport().setBackground(UIManager.getColor("Panel.background"));

            panel.add(scrollPane, BorderLayout.CENTER);
            return panel;
        }

        @NotNull
        @Override
        protected Action[] createActions() {
            return new Action[]{getOKAction()};
        }
    }
}