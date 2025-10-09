package com.zhongan.devpilot.exception;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.JvmExceptionOccurrenceFilter;
import com.intellij.execution.impl.InlayProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.zhongan.devpilot.settings.state.PersonalAdvancedSettingsState;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 调试器过滤器，用于在异常信息旁添加DevPilot图标
 */
public class DevPilotDebuggerFilter implements JvmExceptionOccurrenceFilter {

    @Nullable
    @Override
    public Filter.ResultItem applyFilter(@NotNull String exceptionClassName, @NotNull List<PsiClass> classes, int exceptionStartOffset) {
        // 检查功能是否启用
        PersonalAdvancedSettingsState settings = PersonalAdvancedSettingsState.getInstance();
        if (settings == null || !settings.isExceptionAssistantEnabled()) {
            return null;
        }

        // 创建异常处理结果
        return new CreateExceptionBreakpointResult(
                exceptionStartOffset,
                exceptionStartOffset + exceptionClassName.length(),
                exceptionClassName,
                classes.get(0).getProject()
        );
    }

    /**
     * 创建异常断点结果，实现InlayProvider接口
     */
    private static class CreateExceptionBreakpointResult extends Filter.ResultItem implements InlayProvider {
        private final String myExceptionClassName;

        private final Project project;

        private final int startOffset;

        CreateExceptionBreakpointResult(int highlightStartOffset, int highlightEndOffset, String exceptionClassName, Project project) {
            super(highlightStartOffset, highlightEndOffset, null);

            this.myExceptionClassName = exceptionClassName;

            this.project = project;
            this.startOffset = highlightStartOffset;
        }

        @Override
        public EditorCustomElementRenderer createInlayRenderer(Editor editor) {
            return new DevPilotPresentation(editor, this.project, this.startOffset);
        }
    }
}
