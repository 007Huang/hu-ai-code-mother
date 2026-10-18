package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.config.AppQuotaProperties;
import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.exception.ThrowUtils;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.AppQuotaVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 配额仅以数据库中未删除的应用为准，不依赖 AppService 或缓存计数。 */
@Service
public class AppQuotaService {

    private final UserMapper userMapper;
    private final AppMapper appMapper;
    private final AppQuotaProperties properties;

    public AppQuotaService(UserMapper userMapper, AppMapper appMapper, AppQuotaProperties properties) {
        this.userMapper = userMapper;
        this.appMapper = appMapper;
        this.properties = properties;
    }

    public AppQuotaVO getQuota(Long userId) {
        validateUserId(userId);
        User user = userMapper.selectActiveUser(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return quotaFor(user);
    }

    /** 创建最终检查时，调用方必须先持有该用户行锁；路由前也可用于快速预检。 */
    public void assertCanCreate(User user) {
        ThrowUtils.throwIf(user == null || user.getId() == null || user.getId() <= 0,
                ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!quotaFor(user).getCanCreate(), ErrorCode.APP_QUOTA_EXCEEDED);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public AppQuotaVO updateQuota(Long userId, Integer appLimit) {
        validateUserId(userId);
        validateLimit(appLimit);
        User user = userMapper.selectActiveUserForUpdate(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        ThrowUtils.throwIf(userMapper.updateAppLimit(userId, appLimit) != 1,
                ErrorCode.OPERATION_ERROR, "更新用户配额失败");
        user.setAppLimit(appLimit);
        return quotaFor(user);
    }

    private AppQuotaVO quotaFor(User user) {
        Integer appLimit = user.getAppLimit();
        // 非法存量值不能绕过限制；配置在启动时验证。
        if (appLimit != null && (appLimit < 0 || appLimit > AppQuotaProperties.MAX_LIMIT)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户配额配置无效");
        }
        long dbCount = appMapper.countActiveByUserId(user.getId());
        if (dbCount < 0 || dbCount > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用计数超出支持范围");
        }
        int usedCount = Math.toIntExact(dbCount);
        int limitCount = appLimit == null ? properties.getDefaultLimit() : appLimit;
        AppQuotaVO quota = new AppQuotaVO();
        quota.setUsedCount(usedCount);
        quota.setLimitCount(limitCount);
        quota.setRemainingCount(Math.max(0, limitCount - usedCount));
        quota.setCanCreate(usedCount < limitCount);
        quota.setOverLimit(usedCount > limitCount);
        quota.setAppLimit(appLimit);
        quota.setDefaultLimit(properties.getDefaultLimit());
        quota.setUsesDefault(appLimit == null);
        return quota;
    }

    private void validateUserId(Long userId) {
        ThrowUtils.throwIf(userId == null || userId <= 0, ErrorCode.PARAMS_ERROR, "用户 ID 不合法");
    }

    private void validateLimit(Integer appLimit) {
        ThrowUtils.throwIf(appLimit != null && (appLimit < 0 || appLimit > AppQuotaProperties.MAX_LIMIT),
                ErrorCode.PARAMS_ERROR, "应用配额必须在 0 到 10000 之间");
    }
}
