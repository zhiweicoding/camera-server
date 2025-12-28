package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.DeviceVendor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备经销商归属数据访问接口
 */
@Mapper
public interface DeviceVendorRepository extends BaseMapper<DeviceVendor> {

    /**
     * 获取设备的完整经销商链路（从底层到顶层）
     * 用于计算多级分润
     * 
     * @param deviceId 设备ID
     * @return 经销商归属链路列表（按层级升序）
     */
    @Select("SELECT * FROM device_vendor WHERE device_id = #{deviceId} ORDER BY level ASC")
    List<DeviceVendor> getVendorChainByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 获取设备在某个经销商下的归属记录
     * 
     * @param deviceId 设备ID
     * @param vendorId 经销商ID
     * @return 归属记录
     */
    @Select("SELECT * FROM device_vendor WHERE device_id = #{deviceId} AND vendor_id = #{vendorId}")
    DeviceVendor getByDeviceAndVendor(@Param("deviceId") String deviceId, @Param("vendorId") Long vendorId);

    /**
     * 获取设备当前最终归属的经销商（最高层级）
     * 
     * @param deviceId 设备ID
     * @return 最终归属经销商
     */
    @Select("SELECT * FROM device_vendor WHERE device_id = #{deviceId} ORDER BY level DESC LIMIT 1")
    DeviceVendor getCurrentVendor(@Param("deviceId") String deviceId);
}
