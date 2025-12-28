package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.Installer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 装机商数据访问接口
 */
@Mapper
public interface InstallerRepository extends BaseMapper<Installer> {
}
