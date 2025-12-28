package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.DeviceDealer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备经销商归属数据访问接口
 */
@Mapper
public interface DeviceDealerRepository extends BaseMapper<DeviceDealer> {

    /**
     * 获取设备的完整经销商链路（从底层到顶层）
     * 用于计算多级分润
     * 
     * @param deviceId 设备ID
     * @return 经销商归属链路列表（按层级升序）
     */
    @Select("SELECT * FROM device_dealer WHERE device_id = #{deviceId} ORDER BY level ASC")
    List<DeviceDealer> getDealerChainByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 获取设备在某个经销商下的归属记录
     * 
     * @param deviceId 设备ID
     * @param dealerId 经销商ID
     * @return 归属记录
     */
    @Select("SELECT * FROM device_dealer WHERE device_id = #{deviceId} AND dealer_id = #{dealerId}")
    DeviceDealer getByDeviceAndDealer(@Param("deviceId") String deviceId, @Param("dealerId") Long dealerId);
}
