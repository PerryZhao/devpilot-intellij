package com.zhongan.devpilot.actions.console;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.zhongan.devpilot.actions.notifications.DevPilotNotification;
import com.zhongan.devpilot.constant.DefaultConst;
import com.zhongan.devpilot.enums.EditorActionEnum;
import com.zhongan.devpilot.gui.toolwindows.chat.DevPilotChatToolWindowService;
import com.zhongan.devpilot.gui.toolwindows.components.EditorInfo;
import com.zhongan.devpilot.settings.state.DevPilotLlmSettingsState;
import com.zhongan.devpilot.settings.state.LanguageSettingsState;
import com.zhongan.devpilot.util.DevPilotMessageBundle;
import com.zhongan.devpilot.util.PromptDataMapUtils;
import com.zhongan.devpilot.webview.model.CodeReferenceModel;
import com.zhongan.devpilot.webview.model.MessageModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;

import static com.zhongan.devpilot.constant.PlaceholderConst.ANSWER_LANGUAGE;
import static com.zhongan.devpilot.constant.PlaceholderConst.LANGUAGE;
import static com.zhongan.devpilot.constant.PlaceholderConst.SELECTED_CODE;

/**
 * 控制台上下文菜单动作，用于解释选中的代码
 */
public class ConsoleExplainCodeAction extends AnAction {

    public static final DataKey<ConsoleView> CONSOLE_VIEW_KEY = DataKey.create("consoleView");

    public ConsoleExplainCodeAction() {
        super(DevPilotMessageBundle.get("devpilot.action.explain.console"),
                DevPilotMessageBundle.get("devpilot.action.explain.console.description"),
                AllIcons.Actions.Find);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // 检查是否在控制台中选中了文本
        boolean enabled = false;
        ConsoleView consoleView = event.getData(CONSOLE_VIEW_KEY);
        Editor editor = event.getData(CommonDataKeys.EDITOR);

        if (consoleView != null && editor != null) {
            enabled = editor.getSelectionModel().hasSelection();
        }

        event.getPresentation().setEnabled(enabled && event.getProject() != null);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        var project = event.getProject();
        if (project == null) {
            return;
        }

        // 获取DevPilot工具窗口
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DevPilot");
        if (toolWindow == null) {
            return;
        }
        toolWindow.show();

        // 获取控制台编辑器
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null || !editor.getSelectionModel().hasSelection()) {
            return;
        }

        // 创建EditorInfo对象
        var editorInfo = new EditorInfo(editor);

        // 获取选中的文本
        String selectedText = editorInfo.getSourceCode();
        if (selectedText == null || selectedText.isEmpty()) {
            return;
        }

        // 创建回调函数，用于处理结果
        Consumer<String> callback = result -> {
            DevPilotNotification.debug("result is -> [." + result + "].");
            if (validateResult(result)) {
                DevPilotNotification.info(DevPilotMessageBundle.get("devpilot.notification.input.tooLong"));
                return;
            }
        };

        // 创建数据映射，包含选中的代码和语言信息
        Map<String, String> data = new HashMap<>();
        data.put(SELECTED_CODE, selectedText);
        // 控制台文本默认为纯文本
        data.put(LANGUAGE, "text");

        // 设置回答的语言
        if (LanguageSettingsState.getInstance().getLanguageIndex() == 1) {
            data.put(ANSWER_LANGUAGE, "zh_CN");
        } else {
            data.put(ANSWER_LANGUAGE, "en_US");
        }

        // 获取DevPilotChatToolWindowService服务
        var service = project.getService(DevPilotChatToolWindowService.class);
        var username = DevPilotLlmSettingsState.getInstance().getFullName();
        service.clearRequestSessionAndChangeChatMode(DefaultConst.SMART_CHAT_TYPE);

        // 创建CodeReferenceModel对象
        var codeReference = CodeReferenceModel.getCodeRefFromEditor(editorInfo, EditorActionEnum.EXPLAIN_CODE);
        // 控制台文本默认为纯文本
        codeReference.setLanguageId("text");

        // 确保fileUrl不为null，避免NullPointerException
        if (codeReference.getFileUrl() == null) {
            // 设置一个默认值
            codeReference.setFileUrl("console");
        }

        // 创建代码消息
        List<CodeReferenceModel> list = new ArrayList<>();
        list.add(codeReference);

        var showText = DevPilotMessageBundle.get("devpilot.action.explain.console");
        var codeMessage = MessageModel.buildCodeMessage(
                UUID.randomUUID().toString(), System.currentTimeMillis(), showText, username, list, DefaultConst.SMART_CHAT_TYPE);

        // 构建聊天数据映射
        ApplicationManager.getApplication().invokeAndWait(() -> {
            PromptDataMapUtils.buildChatDataMap(project, null, list, data);
        });

        // 预设问题
        String presetQuestion = "针对以下终端信息，请解释原因并提供修复意见";

        // 调用DevPilot服务解释代码，传入预设问题
        service.chat(EditorActionEnum.EXPLAIN_CODE.name(), data, presetQuestion, callback, codeMessage);
    }

    /**
     * 检查输入长度
     */
    private boolean validateResult(String content) {
        return content.contains(DevPilotMessageBundle.get(DefaultConst.GPT_35_MAX_TOKEN_EXCEPTION_MSG));
    }
}
