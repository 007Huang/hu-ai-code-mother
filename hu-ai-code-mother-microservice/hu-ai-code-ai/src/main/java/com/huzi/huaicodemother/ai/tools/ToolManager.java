package com.huzi.huaicodemother.ai.tools;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具管理器
 */
@Slf4j
@Component
public class ToolManager {
    /**
     * 工具名称到工具实例的映射表
     */
    private final Map<String, BaseTool> toolMap=new HashMap<>();
    /**
     * 自动注入所有工具
     */
    @Resource
    private BaseTool[] tools;
    /**
     * 初始化工具映射
     */
    @PostConstruct
    public void initTools() {
        for (BaseTool tool : tools) {
            toolMap.put(tool.getToolName(), tool);
            log.info("已自动注入工具：{} -> {}",tool.getToolName(), tool.getDisplayName());
        }
        log.info("已自动注入 {} 个工具", toolMap.size());

    }
    /**
     * 根据工具名称获取工具实例
     *
     * @param toolName 工具英文名称
     * @return 工具实例
     */
    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    /**
     * 获取已注册的工具集合
     *
     * @return 工具实例集合
     */
    public BaseTool[] getAllTools() {
        return tools;
    }

}
