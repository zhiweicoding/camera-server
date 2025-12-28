package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.Dealer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 经销商数据访问接口
 */
@Mapper
public interface DealerRepository extends BaseMapper<Dealer> {
}
