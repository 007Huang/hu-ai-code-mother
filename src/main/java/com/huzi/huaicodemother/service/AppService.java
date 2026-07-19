package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.model.dto.app.AppQueryRequest;
import com.huzi.huaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.huzi.huaicodemother.model.entity.App;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
public interface AppService extends IService<App> {

//    /**
//     * 创建应用
//     *
//     * @param appAddRequest 应用创建请求
//     * @param loginUser     当前登录用户
//     * @return 新应用 id
//     */
//    long addApp(AppAddRequest appAddRequest, User loginUser);
//
//    /**
//     * 用户修改自己的应用（仅支持修改应用名称）
//     *
//     * @param appUpdateRequest 应用更新请求
//     * @param loginUser        当前登录用户
//     * @return 是否更新成功
//     */
//    boolean updateMyApp(AppUpdateRequest appUpdateRequest, User loginUser);
//
//    /**
//     * 用户删除自己的应用
//     *
//     * @param id        应用 id
//     * @param loginUser 当前登录用户
//     * @return 是否删除成功
//     */
//    boolean deleteMyApp(Long id, User loginUser);

    /**
     * 获取脱敏后的应用信息
     *
     * @param app 应用实体
     * @return 脱敏后的应用视图
     */

    AppVO getAppVO(App app);
    /**
     * 根据查询请求构造 QueryWrapper
     *
     * @param appQueryRequest 查询请求
     * @return QueryWrapper
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
    /**
     * 获取脱敏后的应用信息列表
     *
     * @param appList 应用列表
     * @return 脱敏后的应用视图列表
     */
    List<AppVO> getAppVOList(List<App> appList);

//    /**
//     * 用户根据 id 查看自己的应用详情
//     *
//     * @param id        应用 id
//     * @param loginUser 当前登录用户
//     * @return 脱敏后的应用视图
//     */
//    AppVO getMyAppById(Long id, User loginUser);
//
//    /**
//     * 用户分页查询自己的应用列表
//     *
//     * @param appQueryRequest 查询请求
//     * @param loginUser       当前登录用户
//     * @return 分页应用视图
//     */
//    Page<AppVO> getMyAppVOPage(AppQueryRequest appQueryRequest, User loginUser);
//
//    /**
//     * 用户分页查询精选的应用列表
//     *
//     * @param appQueryRequest 查询请求
//     * @return 分页应用视图
//     */
//    Page<AppVO> getFeaturedAppVOPage(AppQueryRequest appQueryRequest);
//
//    /**
//     * 管理员根据 id 删除任意应用
//     *
//     * @param id 应用 id
//     * @return 是否删除成功
//     */
//    boolean adminDeleteApp(Long id);
//
//    /**
//     * 管理员根据 id 更新任意应用
//     *
//     * @param appAdminUpdateRequest 管理员更新请求
//     * @return 是否更新成功
//     */
//    boolean adminUpdateApp(AppAdminUpdateRequest appAdminUpdateRequest);
//
//    /**
//     * 管理员分页查询应用列表
//     *
//     * @param appAdminQueryRequest 管理员查询请求
//     * @return 分页应用数据
//     */
//    Page<App> adminGetAppPage(AppAdminQueryRequest appAdminQueryRequest);
//
//    /**
//     * 管理员根据 id 查看应用详情
//     *
//     * @param id 应用 id
//     * @return 应用实体
//     */
//    App adminGetAppById(Long id);
//
//    /**
//     * 根据管理员查询请求构造 QueryWrapper
//     *
//     * @param appAdminQueryRequest 管理员查询请求
//     * @return QueryWrapper
//     */
//    QueryWrapper getAdminQueryWrapper(AppAdminQueryRequest appAdminQueryRequest);
//
//    /**
//     * 根据用户查询请求和用户 id 构造 QueryWrapper
//     *
//     * @param appQueryRequest 查询请求
//     * @param userId          用户 id
//     * @return QueryWrapper
//     */
//    QueryWrapper getMyQueryWrapper(AppQueryRequest appQueryRequest, Long userId);
}
