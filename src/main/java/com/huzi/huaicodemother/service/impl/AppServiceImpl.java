package com.huzi.huaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingService;
import com.huzi.huaicodemother.ai.AiCodeGenTypeRoutingServiceFactory;
import com.huzi.huaicodemother.constant.AppConstant;
import com.huzi.huaicodemother.core.AiCodeGeneratorFacade;
import com.huzi.huaicodemother.core.builder.VueProjectBuilder;
import com.huzi.huaicodemother.core.handler.StreamHandlerExecutor;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.exception.ThrowUtils;
import com.huzi.huaicodemother.model.dto.app.AppAddRequest;
import com.huzi.huaicodemother.model.dto.app.AppQueryRequest;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.huzi.huaicodemother.model.enums.CodeGenTypeEnum;
import com.huzi.huaicodemother.model.vo.AppVO;
import com.huzi.huaicodemother.model.vo.UserVO;
import com.huzi.huaicodemother.monitor.MonitorContext;
import com.huzi.huaicodemother.monitor.MonitorContextHolder;
import com.huzi.huaicodemother.service.ChatHistoryService;
import com.huzi.huaicodemother.service.ScreenshotService;
import com.huzi.huaicodemother.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {
    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;
    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;


//    @Resource
//    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不合法");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "请输入对话内容");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限对话该应用");
        //4.获取应用代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        //5.先保存用户消息到数据库
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(),loginUser.getId());
        //6.设置监控上下文
        MonitorContextHolder.setContext(
                MonitorContext.builder()
                        .userId(loginUser.getId().toString())
                        .appId(appId.toString())
                        .build()
        );
        //7。调用AI生成代码
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
//                .subscribe(System.out::println);
        //8.收集AI响应的内容，并且在完成对话后保存记录到对话历史
        return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                .doFinally(signalType -> {
                    // 流结束时清理（无论成功/失败/取消）
                    MonitorContextHolder.clearContext();
                });
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型
        // 使用 AI 智能选择代码生成类型（多例模式）
        AiCodeGenTypeRoutingService routingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = routingService.routeCodeGenType(initPrompt);
        //        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1.校验参数
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不合法");
        ThrowUtils.throwIf(loginUser==null, ErrorCode.NOT_LOGIN_ERROR, "未登录");
        //2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        //3.权限校验，仅本人可以部署自己的应用
        if (!app.getUserId().equals(loginUser.getId()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        //4.检查是否已有deployKey
        String deployKey = app.getDeployKey();
        //如果没有，则生成一个6位deployKey（字母加数字）
        if (StrUtil.isBlank(deployKey)){
            deployKey = RandomUtil.randomString(6);
        }
        //5.获取代码生成类型，获取原始代码生成路径(应用访问目录)
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType+"_"+appId;
        String sourceDirPath= AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        //6.检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists()||!sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        //7.Vue项目特殊处理。构建部署
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 将 dist 目录作为部署源
            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
        }
        //8.复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败"+ e.getMessage());
        }
        //9.更新数据库
        App  updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);//操作数据库
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        //10.返回可访问的 URL 本地访问地址
       // String appDeployUrl = String.format("%s/%s", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 10. 构建应用访问 URL 服务器访问地址
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        //11.异步生成截图并且更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }
    /**
     * 异步生成应用截图并更新封面
     * @param appId 应用 ID
     * @param appUrl 应用 URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        //使用虚拟线程并执行截图生成
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的应用封面
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updateResult = this.updateById(updateApp);
            ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用封面失败");
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }



    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题 代码优雅
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }
/**
 * 删除应用时，关联删除对话历史
 * @param id
 * @return
 */
@Override
    public boolean removeById(Serializable id) {
    if (id == null) {
        return false;
    }
    // 转换为 Long 类型
    long appId = Long.parseLong(id.toString());
    if (appId <= 0) {
        return false;
    }
    //先删除关联的对话历史
    try {
        chatHistoryService.deleteByAppId(appId);
    } catch (Exception e) {
        //记录日志但不阻止应用删除
        log.error("删除应用时，关联删除对话历史失败", e.getMessage());
    }
    // 删除应用
    return super.removeById(id);
}
}


//    @Override
//    public long addApp(AppAddRequest appAddRequest, User loginUser) {
//        // 校验参数
//        if (StrUtil.isBlank(appAddRequest.getInitPrompt())) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "初始 Prompt 不能为空");
//        }
//        // 创建应用
//        App app = new App();
//        BeanUtil.copyProperties(appAddRequest, app);
//        app.setUserId(loginUser.getId());
//        boolean saveResult = this.save(app);
//        if (!saveResult) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建应用失败，数据库错误");
//        }
//        return app.getId();
//    }
//
//    @Override
//    public boolean updateMyApp(AppUpdateRequest appUpdateRequest, User loginUser) {
//        Long appId = appUpdateRequest.getId();
//        if (appId == null || appId <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        // 查询应用是否存在
//        App oldApp = this.getById(appId);
//        if (oldApp == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
//        }
//        // 校验所有权
//        if (!oldApp.getUserId().equals(loginUser.getId())) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权修改该应用");
//        }
//        // 仅允许修改应用名称
//        App app = new App();
//        app.setId(appId);
//        app.setAppName(appUpdateRequest.getAppName());
//        return this.updateById(app);
//    }
//
//    @Override
//    public boolean deleteMyApp(Long id, User loginUser) {
//        if (id == null || id <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        // 查询应用是否存在
//        App oldApp = this.getById(id);
//        if (oldApp == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
//        }
//        // 校验所有权
//        if (!oldApp.getUserId().equals(loginUser.getId())) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除该应用");
//        }
//        return this.removeById(id);
//    }

    //    @Override
//    public AppVO getAppVO(App app) {
//        if (app == null) {
//            return null;
//        }
//        AppVO appVO = new AppVO();
//        BeanUtil.copyProperties(app, appVO);
//        return appVO;
//    }

//    @Override
//    public List<AppVO> getAppVOList(List<App> appList) {
//        if (CollUtil.isEmpty(appList)) {
//            return new ArrayList<>();
//        }
//        return appList.stream()
//                .map(this::getAppVO)
//                .collect(Collectors.toList());
//    }

//    @Override
//    public AppVO getMyAppById(Long id, User loginUser) {
//        if (id == null || id <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        App app = this.getById(id);
//        if (app == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
//        }
//        // 校验所有权
//        if (!app.getUserId().equals(loginUser.getId())) {
//            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该应用");
//        }
//        return this.getAppVO(app);
//    }
//
//    @Override
//    public Page<AppVO> getMyAppVOPage(AppQueryRequest appQueryRequest, User loginUser) {
//        if (appQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        long pageNum = appQueryRequest.getPageNum();
//        long pageSize = appQueryRequest.getPageSize();
//        // 每页最多 20 个
//        if (pageSize > 20) {
//            pageSize = 20;
//        }
//        QueryWrapper queryWrapper = getMyQueryWrapper(appQueryRequest, loginUser.getId());
//        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
//        // 数据脱敏
//        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
//        List<AppVO> appVOList = getAppVOList(appPage.getRecords());
//        appVOPage.setRecords(appVOList);
//        return appVOPage;
//    }
//
//    @Override
//    public Page<AppVO> getFeaturedAppVOPage(AppQueryRequest appQueryRequest) {
//        if (appQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        long pageNum = appQueryRequest.getPageNum();
//        long pageSize = appQueryRequest.getPageSize();
//        // 每页最多 20 个
//        if (pageSize > 20) {
//            pageSize = 20;
//        }
//        // 精选应用：按优先级降序排列
//        QueryWrapper queryWrapper = QueryWrapper.create()
//                .like("appName", appQueryRequest.getAppName())
//                .orderBy("priority", false);
//        Page<App> appPage = this.page(Page.of(pageNum, pageSize), queryWrapper);
//        // 数据脱敏
//        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
//        List<AppVO> appVOList = getAppVOList(appPage.getRecords());
//        appVOPage.setRecords(appVOList);
//        return appVOPage;
//    }
//
//    @Override
//    public boolean adminDeleteApp(Long id) {
//        if (id == null || id <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        return this.removeById(id);
//    }
//
//    @Override
//    public boolean adminUpdateApp(AppAdminUpdateRequest request) {
//        Long appId = request.getId();
//        if (appId == null || appId <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        // 查询应用是否存在
//        App oldApp = this.getById(appId);
//        if (oldApp == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
//        }
//        App app = new App();
//        BeanUtil.copyProperties(request, app);
//        return this.updateById(app);
//    }
//
//    @Override
//    public Page<App> adminGetAppPage(AppAdminQueryRequest appAdminQueryRequest) {
//        if (appAdminQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        long pageNum = appAdminQueryRequest.getPageNum();
//        long pageSize = appAdminQueryRequest.getPageSize();
//        QueryWrapper queryWrapper = getAdminQueryWrapper(appAdminQueryRequest);
//        return this.page(Page.of(pageNum, pageSize), queryWrapper);
//    }
//
//    @Override
//    public App adminGetAppById(Long id) {
//        if (id == null || id <= 0) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不合法");
//        }
//        App app = this.getById(id);
//        if (app == null) {
//            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
//        }
//        return app;
//    }
//
//    @Override
//    public QueryWrapper getAdminQueryWrapper(AppAdminQueryRequest appAdminQueryRequest) {
//        if (appAdminQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        Long id = appAdminQueryRequest.getId();
//        String appName = appAdminQueryRequest.getAppName();
//        String cover = appAdminQueryRequest.getCover();
//        String initPrompt = appAdminQueryRequest.getInitPrompt();
//        String codeGenType = appAdminQueryRequest.getCodeGenType();
//        String deployKey = appAdminQueryRequest.getDeployKey();
//        Integer priority = appAdminQueryRequest.getPriority();
//        Long userId = appAdminQueryRequest.getUserId();
//        String sortField = appAdminQueryRequest.getSortField();
//        String sortOrder = appAdminQueryRequest.getSortOrder();
//        return QueryWrapper.create()
//                .eq("id", id)
//                .like("appName", appName)
//                .like("cover", cover)
//                .like("initPrompt", initPrompt)
//                .eq("codeGenType", codeGenType)
//                .eq("deployKey", deployKey)
//                .eq("priority", priority)
//                .eq("userId", userId)
//                .orderBy(sortField, "ascend".equals(sortOrder));
//    }
//
//    @Override
//    public QueryWrapper getMyQueryWrapper(AppQueryRequest appQueryRequest, Long userId) {
//        if (appQueryRequest == null) {
//            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
//        }
//        String appName = appQueryRequest.getAppName();
//        String sortField = appQueryRequest.getSortField();
//        String sortOrder = appQueryRequest.getSortOrder();
//        return QueryWrapper.create()
//                .eq("userId", userId)
//                .like("appName", appName)
//                .orderBy(sortField, "ascend".equals(sortOrder));
//    }
//}
