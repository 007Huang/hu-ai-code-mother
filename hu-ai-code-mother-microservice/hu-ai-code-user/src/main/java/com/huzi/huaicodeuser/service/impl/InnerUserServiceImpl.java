package com.huzi.huaicodeuser.service.impl;

import com.huzi.huaicodemother.innerservice.InnerUserService;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.UserVO;
import com.huzi.huaicodeuser.service.UserService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
/**
 *内部服务实现类，内外部服务隔离开
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserService userService;

    @Override
    public List<User> listByIds(Collection<? extends Serializable> ids) {

        return userService.listByIds(ids);
    }

    @Override
    public User getById(Serializable id) {
        return userService.getById(id);
    }

    @Override
    public UserVO getUserVO(User user) {
        return userService.getUserVO(user);
    }
}
