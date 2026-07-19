package com.huzi.huaicodemother.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.model.dto.AppAddRequest;
import com.huzi.huaicodemother.model.dto.AppAdminQueryRequest;
import com.huzi.huaicodemother.model.dto.AppAdminUpdateRequest;
import com.huzi.huaicodemother.model.dto.AppQueryRequest;
import com.huzi.huaicodemother.model.dto.AppUpdateRequest;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.AppVO;
import com.huzi.huaicodemother.model.vo.UserVO;
import com.huzi.huaicodemother.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.service.AppService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

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
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {
    @Resource
    private UserService userService;
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

//    @Override
//    public AppVO getAppVO(App app) {
//        if (app == null) {
//            return null;
//        }
//        AppVO appVO = new AppVO();
//        BeanUtil.copyProperties(app, appVO);
//        return appVO;
//    }

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
}
