package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.DeviceTransfer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备转让记录数据访问接口
 */
@Mapper
public interface DeviceTransferRepository extends BaseMapper<DeviceTransfer> {
}
