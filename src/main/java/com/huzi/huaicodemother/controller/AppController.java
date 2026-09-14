package com.huzi.huaicodemother.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingService;
import com.huzi.huaicodemother.annotation.AuthCheck;
import com.huzi.huaicodemother.common.BaseResponse;
import com.huzi.huaicodemother.common.DeleteRequest;
import com.huzi.huaicodemother.common.ResultUtils;
import com.huzi.huaicodemother.constant.AppConstant;
import com.huzi.huaicodemother.constant.UserConstant;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.exception.ThrowUtils;
import com.huzi.huaicodemother.model.dto.app.*;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;
import com.huzi.huaicodemother.model.sse.AppStreamEvent;
import com.huzi.huaicodemother.model.vo.AppVO;
import com.huzi.huaicodemother.ratelimter.annotation.RateLimit;
import com.huzi.huaicodemother.ratelimter.enums.RateLimitType;
import com.huzi.huaicodemother.service.AppService;
import com.huzi.huaicodemother.service.ProjectDownloadService;
import com.huzi.huaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
@RestController
@RequestMapping("/app")
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;
    @Resource
    private ProjectDownloadService projectDownloadService;
    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;


//    @Resource
//    private ChatHistoryService chatHistoryService;

    /**
     * 应用聊天生成代码（流式 SSE）声明MediaType.TEXT_EVENT_STREAM_VALUE
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    //每个用户在 60秒内最多只能发起5 次 AI 对‍‍话请求，超过限制会返回友好的错误提示‍
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<AppStreamEvent> appStream = appService.chatToGenCode(appId, message, loginUser);
        Flux<ServerSentEvent<String>> responseStream = appStream
                .map(this::toServerSentEvent)
                .concatWith(Mono.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("")
                        .build()))
                .onErrorResume(error -> Mono.just(toBusinessErrorEvent(error)));
        // 构建期间可能长时间没有业务消息，通过注释心跳保持连接，不触发前端 message 事件。
        return responseStream.publish(shared -> {
            Flux<ServerSentEvent<String>> heartbeat = Flux.interval(Duration.ofSeconds(15))
                    .map(ignored -> ServerSentEvent.<String>builder().comment("heartbeat").build())
                    .takeUntilOther(shared.ignoreElements());
            return Flux.merge(shared, heartbeat);
        });
    }

    private ServerSentEvent<String> toServerSentEvent(AppStreamEvent streamEvent) {
        if (streamEvent.type() == AppStreamEvent.Type.PHASE) {
            String data = JSONUtil.toJsonStr(Map.of(
                    "phase", streamEvent.phase(),
                    "message", streamEvent.data()
            ));
            return ServerSentEvent.<String>builder()
                    .event("phase")
                    .data(data)
                    .build();
        }
        String data = JSONUtil.toJsonStr(Map.of("d", streamEvent.data()));
        return ServerSentEvent.<String>builder().data(data).build();
    }

    private ServerSentEvent<String> toBusinessErrorEvent(Throwable error) {
        Throwable actualError = error;
        while (actualError.getCause() != null && actualError != actualError.getCause()) {
            actualError = actualError.getCause();
        }
        int errorCode = ErrorCode.SYSTEM_ERROR.getCode();
        String errorMessage = "生成过程中出现错误，请稍后重试";
        if (actualError instanceof BusinessException businessException) {
            errorCode = businessException.getCode();
            errorMessage = businessException.getMessage();
        }
        String data = JSONUtil.toJsonStr(Map.of(
                "error", true,
                "code", errorCode,
                "message", errorMessage
        ));
        return ServerSentEvent.<String>builder()
                .event("business-error")
                .data(data)
                .build();
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }





    // ==================== 用户接口 ====================

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
//        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
//        // 参数校验
//        String initPrompt = appAddRequest.getInitPrompt();
//        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
//        // 获取当前登录用户
//        User loginUser = userService.getLoginUser(request);
//        // 构造入库对象
//        App app = new App();
//        BeanUtil.copyProperties(appAddRequest, app);
//        app.setUserId(loginUser.getId());
//        // 应用名称暂时为 initPrompt 前 12 位
//        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
////        // 暂时设置为多文件生成
////        app.setCodeGenType(CodeGenTypeEnum.MULTI_FILE.getValue());
//        // 暂时设置为 VUE 工程生成
//        //app.setCodeGenType(CodeGenTypeEnum.VUE_PROJECT.getValue());
//        // 使用 AI 智能选择代码生成类型
//        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
//        app.setCodeGenType(selectedCodeGenType.getValue());
//        // 插入数据库
//        boolean result = appService.save(app);
//        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
//        return ResultUtils.success(app.getId());
    }

//    /**
//     * 创建应用（须填写 initPrompt）
//     *
//     * @param appAddRequest 应用创建请求
//     * @param request       HttpServletRequest
//     * @return 新应用 id
//     */
//    @PostMapping("/add")
//    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
//        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
//        User loginUser = userService.getLoginUser(request);
//        long appId = appService.addApp(appAddRequest, loginUser);
//        return ResultUtils.success(appId);
//    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

//    /**
//     * 根据 id 修改自己的应用（目前只支持修改应用名称）
//     *
//     * @param appUpdateRequest 应用更新请求
//     * @param request          HttpServletRequest
//     * @return 是否更新成功
//     */
//    @PostMapping("/update")
//    public BaseResponse<Boolean> updateMyApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
//        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
//        User loginUser = userService.getLoginUser(request);
//        boolean result = appService.updateMyApp(appUpdateRequest, loginUser);
//        return ResultUtils.success(result);
//    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

//    /**
//     * 根据 id 删除自己的应用
//     *
//     * @param deleteRequest 删除请求
//     * @param request       HttpServletRequest
//     * @return 是否删除成功
//     */
//    @PostMapping("/delete")
//    public BaseResponse<Boolean> deleteMyApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
//        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
//        User loginUser = userService.getLoginUser(request);
//        boolean result = appService.deleteMyApp(deleteRequest.getId(), loginUser);
//        return ResultUtils.success(result);
//    }
    /**
     * 根据 id 获取应用详情
     *
     * @param id      应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }


//    /**
//     * 根据 id 查看应用详情
//     *
//     * @param id      应用 id
//     * @param request HttpServletRequest
//     * @return 脱敏后的应用视图
//     */
//    @GetMapping("/get/vo")
//    public BaseResponse<AppVO> getMyAppVOById(long id, HttpServletRequest request) {
//        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
//        User loginUser = userService.getLoginUser(request);
//        AppVO appVO = appService.getMyAppById(id, loginUser);
//        return ResultUtils.success(appVO);
//    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

//    /**
//     * 分页查询自己的应用列表（支持根据名称查询，每页最多 20 个）
//     *
//     * @param appQueryRequest 查询请求
//     * @param request         HttpServletRequest
//     * @return 分页应用视图
//     */
//    @PostMapping("/my/list/page/vo")
//    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
//        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        User loginUser = userService.getLoginUser(request);
//        Page<AppVO> appVOPage = appService.getMyAppVOPage(appQueryRequest, loginUser);
//        return ResultUtils.success(appVOPage);
//    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
//    这里使用了 SpEL（Spring Expression Language）表达式：
//
//    T(类名)：用于调用静态方法，生成缓存 key
//#参数名：用于引用方法参数
//    condition：设置缓存条件，只有前 10 页才会被缓存
//💡 建议了解下缓存注解的工作原理，执行流程如下：AH+EMPcWDeoPtEXvMXn4p2OujqG9e2R1rS1c+cTEDUk=
//
//    方法执行前：Spring 根据 key 表达式生成缓存键
//    缓存检查：检查 Redis 中是否存在该键对应的缓存数据
//    缓存命中：如果存在且未过期，直接返回缓存数据，不执行方法
//    缓存未命中：如果不存在，执行方法获取结果，并将结果存储到 Redis 中
//    返回结果：返回方法执行结果

    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(com.huzi.huaicodemother.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

//    /**
//     * 分页查询精选的应用列表（支持根据名称查询，每页最多 20 个）
//     *
//     * @param appQueryRequest 查询请求
//     * @return 分页应用视图
//     */
//    @PostMapping("/featured/list/page/vo")
//    public BaseResponse<Page<AppVO>> listFeaturedAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
//        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        Page<AppVO> appVOPage = appService.getFeaturedAppVOPage(appQueryRequest);
//        return ResultUtils.success(appVOPage);
//    }

    // ==================== 管理员接口 ====================

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 级联删除该应用的所有对话历史
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }


//    /**
//     * 管理员根据 id 删除任意应用
//     *
//     * @param deleteRequest 删除请求
//     * @return 是否删除成功
//     */
//    @PostMapping("/admin/delete")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> adminDeleteApp(@RequestBody DeleteRequest deleteRequest) {
//        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
//        boolean result = appService.adminDeleteApp(deleteRequest.getId());
//        return ResultUtils.success(result);
//    }
    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);//复制属性
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

//    /**
//     * 管理员根据 id 更新任意应用（支持更新应用名称、应用封面、优先级）
//     *
//     * @param appAdminUpdateRequest 管理员更新请求
//     * @return 是否更新成功
//     */
//    @PostMapping("/admin/update")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Boolean> adminUpdateApp(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
//        ThrowUtils.throwIf(appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
//        boolean result = appService.adminUpdateApp(appAdminUpdateRequest);
//        return ResultUtils.success(result);
//    }
    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

//    /**
//     * 管理员分页查询应用列表（支持根据除时间外的任何字段查询，每页数量不限）
//     *
//     * @param appAdminQueryRequest 管理员查询请求
//     * @return 分页应用数据
//     */
//    @PostMapping("/admin/list/page")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<Page<App>> adminListAppByPage(@RequestBody AppAdminQueryRequest appAdminQueryRequest) {
//        ThrowUtils.throwIf(appAdminQueryRequest == null, ErrorCode.PARAMS_ERROR);
//        Page<App> appPage = appService.adminGetAppPage(appAdminQueryRequest);
//        return ResultUtils.success(appPage);
//    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }

//    /**
//     * 管理员根据 id 查看应用详情
//     *
//     * @param id 应用 id
//     * @return 应用实体
//     */
//    @GetMapping("/admin/get")
//    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
//    public BaseResponse<App> adminGetAppById(long id) {
//        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
//        App app = appService.adminGetAppById(id);
//        return ResultUtils.success(app);
//    }
}
