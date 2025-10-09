package com.zhongan.devpilot.exception;

import com.intellij.codeInsight.hints.presentation.InputHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.zhongan.devpilot.DevPilotIcons;
import com.zhongan.devpilot.constant.DefaultConst;
import com.zhongan.devpilot.enums.EditorActionEnum;
import com.zhongan.devpilot.gui.toolwindows.chat.DevPilotChatToolWindowService;
import com.zhongan.devpilot.gui.toolwindows.components.EditorInfo;
import com.zhongan.devpilot.util.DevPilotMessageBundle;
import com.zhongan.devpilot.util.PromptDataMapUtils;
import com.zhongan.devpilot.webview.model.CodeReferenceModel;
import com.zhongan.devpilot.webview.model.MessageModel;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.swing.Icon;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 异常信息旁的DevPilot图标渲染器
 */
public class DevPilotPresentation implements EditorCustomElementRenderer, InputHandler {

    private final Editor editor;

    private final Project project;

    private final int startOffset;

    public DevPilotPresentation(Editor editor, Project project, int startOffset) {
        this.editor = editor;
        this.project = project;
        this.startOffset = startOffset;
    }

    /**
     * 获取异常堆栈信息
     */
    private String getErrorStacktrace(Document document, int startOffset, int line) {
        String errorHeader = document.getText(new TextRange(startOffset, document.getLineEndOffset(line)));
        StringBuilder sb = new StringBuilder(errorHeader);
        line++;

        // 收集异常堆栈信息
        while (line < document.getLineCount()) {
            String lineContent = document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)));
            String trimmedLine = lineContent.trim();

            // 判断是否是堆栈信息的一部分
            if (trimmedLine.startsWith("at ") || trimmedLine.startsWith("Caused by") || trimmedLine.startsWith("...")) {
                sb.append("\n");
                sb.append(lineContent);
                line++;
            } else {
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 处理鼠标点击事件
     */
    @Override
    public void mouseClicked(@NotNull MouseEvent mouseEvent, @NotNull Point point) {
        // 获取异常信息
        int line = editor.getDocument().getLineNumber(startOffset);
        String errorInformation = getErrorStacktrace(editor.getDocument(), startOffset, line);

        // 获取代码上下文
        String codeContext = getCodeContext(line);

        // 显示DevPilot工具窗口
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("DevPilot");
        if (toolWindow == null) {
            return;
        }
        toolWindow.show();

        // 创建提示文本
        String promptText = createPromptText(errorInformation, codeContext);

        // 获取DevPilotChatToolWindowService服务
        DevPilotChatToolWindowService service = project.getService(DevPilotChatToolWindowService.class);
        if (service == null) {
            return;
        }

        // 创建EditorInfo对象
        EditorInfo editorInfo = new EditorInfo(editor);

        // 准备完整内容（异常信息+代码上下文）
        String fullContent = errorInformation;
        if (StringUtils.isNotBlank(codeContext)) {
            fullContent += "\n\n代码上下文：\n" + codeContext;
        }

        // 设置EditorInfo的sourceCode为完整内容
        editorInfo.setSourceCode(fullContent);

        // 创建CodeReferenceModel对象
        CodeReferenceModel codeReference = CodeReferenceModel.getCodeRefFromEditor(editorInfo, EditorActionEnum.EXPLAIN_CODE);

        // 确保fileUrl不为null
        if (codeReference.getFileUrl() == null) {
            codeReference.setFileUrl("console");
        }

        // 设置引用范围（从第1行到最后一行）
        int lineCount = fullContent.split("\n").length;
        codeReference.setSelectedStartLine(1);
        codeReference.setSelectedStartColumn(0);
        codeReference.setSelectedEndLine(lineCount);
        codeReference.setSelectedEndColumn(0);

        // 创建代码消息列表
        List<CodeReferenceModel> list = new ArrayList<>();

        list.add(codeReference);

        // 创建消息模型
        String username = "User";
        String showText = DevPilotMessageBundle.get("devpilot.action.explain.exception");
        MessageModel codeMessage = MessageModel.buildCodeMessage(UUID.randomUUID().toString(), System.currentTimeMillis(), showText, username, list, 2);

        // 构建聊天数据映射
        Map<String, String> data = new HashMap<>();
        // 使用包含异常信息和代码上下文的完整内容
        data.put("SELECTED_CODE", fullContent);
        data.put("LANGUAGE", "java");
        data.put("CODE_REFERENCE", fullContent);

        ApplicationManager.getApplication().invokeAndWait(() -> {
            PromptDataMapUtils.buildChatDataMap(project, null, list, data);
        });

        // 确保切换到工程上下文模式
        service.clearRequestSessionAndChangeChatMode(DefaultConst.SMART_CHAT_TYPE);

        // 调用DevPilot服务解释异常
        service.chat(EditorActionEnum.EXPLAIN_CODE.name(), data, promptText, result -> {
        }, codeMessage);
    }

    /**
     * 获取代码上下文
     * 提取异常发生行周围的代码片段，以便更好地理解异常原因
     *
     * @param line 异常发生的行号
     * @return 包含异常上下文的代码片段
     */
    private String getCodeContext(int line) {
        Document document = editor.getDocument();
        int contextLines = 5;

        // 计算上下文范围
        int startLine = Math.max(0, line - contextLines);
        int endLine = Math.min(document.getLineCount() - 1, line + contextLines);

        StringBuilder contextBuilder = new StringBuilder();

        // 提取上下文代码
        for (int i = startLine; i <= endLine; i++) {
            try {
                int lineStart = document.getLineStartOffset(i);
                int lineEnd = document.getLineEndOffset(i);
                String lineContent = document.getText(new TextRange(lineStart, lineEnd));

                // 标记异常发生行
                if (i == line) {
                    contextBuilder.append(">>> ");
                } else {
                    contextBuilder.append("    ");
                }

                // 添加行号和内容
                contextBuilder.append(String.format("%4d: ", i + 1));
                contextBuilder.append(lineContent);
                contextBuilder.append("\n");
            } catch (Exception e) {
                // 如果提取某行出现问题，跳过该行继续处理
                continue;
            }
        }

        return contextBuilder.toString();
    }

    /**
     * 创建提示文本
     */
    private String createPromptText(String errorInformation, String codeContext) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("请分析以下异常信息，解释原因并提供修复建议：\n\n");
        promptBuilder.append(errorInformation);

        if (StringUtils.isNotBlank(codeContext)) {
            promptBuilder.append("\n\n代码上下文：\n");
            promptBuilder.append(codeContext);
        }

        return promptBuilder.toString();
    }

    @Override
    public void mouseExited() {
        ((EditorImpl) editor).setCustomCursor(this, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
    }

    @Override
    public void mouseMoved(@NotNull MouseEvent mouseEvent, @NotNull Point point) {
        ((EditorImpl) editor).setCustomCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
        return DevPilotIcons.SYSTEM_ICON_INLAY.getIconWidth();
    }

    @Override
    public int calcHeightInPixels(@NotNull Inlay inlay) {
        return DevPilotIcons.SYSTEM_ICON_INLAY.getIconHeight();
    }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
        // 获取编辑器颜色
        Color color = EditorColorsManager.getInstance().getGlobalScheme().getColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);

        // 获取DevPilot图标
        Icon icon = DevPilotIcons.SYSTEM_ICON_INLAY;

        // 计算图标位置
        int curX = r.x + r.width / 2 - icon.getIconWidth() / 2;
        int curY = r.y + r.height / 2 - icon.getIconHeight() / 2;

        // 确保位置有效
        if (curX < 0 || curY < 0) {
            return;
        }

        // 绘制图标
        icon.paintIcon(inlay.getEditor().getComponent(), g, curX, curY);
    }
}
