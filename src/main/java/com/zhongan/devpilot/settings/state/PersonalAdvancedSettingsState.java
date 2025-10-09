package com.zhongan.devpilot.settings.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(name = "DevPilot_Personal_Advanced_Settings", storages = @Storage("DevPilot_Personal_Advanced_Settings.xml"))
public class PersonalAdvancedSettingsState implements PersistentStateComponent<PersonalAdvancedSettingsState> {

    private String localStorage;
    
    // 异常助手功能开关，默认启用
    private boolean exceptionAssistantEnabled = true;

    public static PersonalAdvancedSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(PersonalAdvancedSettingsState.class);
    }

    @Override
    public PersonalAdvancedSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(PersonalAdvancedSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public String getLocalStorage() {
        return localStorage;
    }

    public void setLocalStorage(String localStorage) {
        this.localStorage = localStorage;
    }
    
    /**
     * 获取异常助手功能是否启用
     * @return 是否启用异常助手功能
     */
    public boolean isExceptionAssistantEnabled() {
        return exceptionAssistantEnabled;
    }
    
    /**
     * 设置异常助手功能是否启用
     * @param exceptionAssistantEnabled 是否启用异常助手功能
     */
    public void setExceptionAssistantEnabled(boolean exceptionAssistantEnabled) {
        this.exceptionAssistantEnabled = exceptionAssistantEnabled;
    }
}
