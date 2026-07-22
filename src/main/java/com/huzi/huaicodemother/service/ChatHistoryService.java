package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.model.dto.chathistory.ChatHistoryQueryRequest;
import com.huzi.huaicodemother.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.huzi.huaicodemother.model.entity.ChatHistory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话历史
     *
     * @param appId   应用 id
     * @param message 消息内容
     * @param userId  用户 id
     * @return {@code true} 添加成功
     */

    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用 id 删除所有对话历史（逻辑删除）
     *
     * @param appId 应用 id
     * @return {@code true} 删除成功
     */
    boolean deleteByAppId(Long appId);
    /**
     * 分页查询某个应用的对话历史（按时间降序，最新的在前）
     *
     * @param appId    应用 id
     * @param pageSize 每页大小
     * @param lastCreateTime 最后创建时间
     * @param loginUser 登录用户
     * @return 分页对话历史
     */

    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 根据查询请求构造 QueryWrapper
     *
     * @param queryRequest 查询请求
     * @return QueryWrapper
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest queryRequest);
}
