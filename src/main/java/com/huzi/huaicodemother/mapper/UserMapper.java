package com.huzi.huaicodemother.mapper;

import com.mybatisflex.core.BaseMapper;
import com.huzi.huaicodemother.model.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 映射层。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE id = #{id} AND isDelete = 0")
    User selectActiveUser(@Param("id") Long id);

    @Select("SELECT * FROM `user` WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    User selectActiveUserForUpdate(@Param("id") Long id);

    // 必须显式写入 NULL，不能使用忽略 null 的通用更新。
    @Update("UPDATE `user` SET appLimit = #{appLimit,jdbcType=INTEGER}, editTime = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND isDelete = 0")
    int updateAppLimit(@Param("id") Long id, @Param("appLimit") Integer appLimit);
}
