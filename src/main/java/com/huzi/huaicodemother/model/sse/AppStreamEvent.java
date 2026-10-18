package com.huzi.huaicodemother.model.sse;

/**
 * 应用生成流事件。
 *
 * @param type  事件类型
 * @param data  内容或阶段提示
 * @param phase 阶段标识，仅阶段事件使用
 */
public record AppStreamEvent(Type type, String data, String phase) {

    public enum Type {
        CONTENT,
        PHASE
    }

    public static AppStreamEvent content(String content) {
        return new AppStreamEvent(Type.CONTENT, content, null);
    }

    public static AppStreamEvent phase(String phase, String message) {
        return new AppStreamEvent(Type.PHASE, message, phase);
    }
}
