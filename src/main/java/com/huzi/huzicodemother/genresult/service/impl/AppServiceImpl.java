package com.huzi.huzicodemother.genresult.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.huzi.huzicodemother.genresult.entity.App;
import com.huzi.huzicodemother.genresult.mapper.AppMapper;
import com.huzi.huzicodemother.genresult.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/007Huang">hzh</a>
 * @since 2026-07-10
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
