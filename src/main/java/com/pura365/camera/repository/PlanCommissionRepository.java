package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.PlanCommission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 套餐分润配置数据访问层
 */
@Mapper
public interface PlanCommissionRepository extends BaseMapper<PlanCommission> {
}
