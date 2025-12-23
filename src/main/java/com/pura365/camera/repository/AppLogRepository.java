package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.AppLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * App日志 Repository
 */
@Mapper
public interface AppLogRepository extends BaseMapper<AppLog> {
}
