package com.huzi.huaicodemother.innerservice;

import com.huzi.huaicodemother.exception.BusinessException;
import com.huzi.huaicodemother.exception.ErrorCode;
import com.huzi.huaicodemother.model.entity.User;
import com.huzi.huaicodemother.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static com.huzi.huaicodemother.constant.UserConstant.USER_LOGIN_STATE;
/**
 * 内部使用的用户服务
 */
public interface InnerUserService {

    List<User> listByIds(Collection<? extends Serializable> ids);

    User getById(Serializable id);

    UserVO getUserVO(User user);

    // 静态方法，避免跨服务调用
    static User getLoginUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }
}
