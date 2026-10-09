package com.huzi.huaicodemother.mapper;

import com.mybatisflex.core.BaseMapper;
import com.huzi.huaicodemother.model.entity.App;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 应用 映射层。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
public interface AppMapper extends BaseMapper<App> {

    @Select("SELECT COUNT(*) FROM app WHERE userId = #{userId} AND isDelete = 0")
    long countActiveByUserId(@Param("userId") Long userId);
}
