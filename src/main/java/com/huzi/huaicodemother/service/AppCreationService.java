package com.huzi.huaicodemother.service;

import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.exception.ThrowUtils;
import com.huzi.huaicodemother.mapper.AppMapper;
import com.huzi.huaicodemother.mapper.UserMapper;
import com.huzi.huaicodemother.model.entity.App;
import com.huzi.huaicodemother.model.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 路由结束后才进入短事务，用户行锁串行化创建和配额修改。 */
@Service
public class AppCreationService {

    private final UserMapper userMapper;
    private final AppMapper appMapper;
    private final AppQuotaService appQuotaService;

    public AppCreationService(UserMapper userMapper, AppMapper appMapper, AppQuotaService appQuotaService) {
        this.userMapper = userMapper;
        this.appMapper = appMapper;
        this.appQuotaService = appQuotaService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW,
            rollbackFor = Exception.class)
    public Long createApp(App app) {
        ThrowUtils.throwIf(app == null || app.getUserId() == null || app.getUserId() <= 0,
                ErrorCode.PARAMS_ERROR);
        User user = userMapper.selectActiveUserForUpdate(app.getUserId());
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        appQuotaService.assertCanCreate(user);
        // 与原 save 一样选择性插入，保留 priority/isDelete/时间等数据库默认值。
        ThrowUtils.throwIf(appMapper.insert(app, true) != 1, ErrorCode.OPERATION_ERROR, "创建应用失败");
        ThrowUtils.throwIf(app.getId() == null || app.getId() <= 0,
                ErrorCode.OPERATION_ERROR, "创建应用未返回 ID");
        return app.getId();
    }
}
