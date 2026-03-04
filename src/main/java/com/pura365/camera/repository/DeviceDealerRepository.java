package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.DeviceDealer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 根据经销商代码查询其关联链路中的所有设备ID（去重）
     *
     * @param dealerCode 经销商代码
     * @return 设备ID列表
     */
    @Select("SELECT DISTINCT device_id FROM device_dealer WHERE dealer_code = #{dealerCode}")
    List<String> listDeviceIdsByDealerCode(@Param("dealerCode") String dealerCode);

    /**
     * 设备ID重写后，同步更新分销链路记录里的 device_id
     *
     * @param oldDeviceId 旧设备ID
     * @param newDeviceId 新设备ID
     * @return 更新行数
     */
    @Update("UPDATE device_dealer SET device_id = #{newDeviceId}, updated_at = NOW() WHERE device_id = #{oldDeviceId}")
    int updateDeviceId(@Param("oldDeviceId") String oldDeviceId, @Param("newDeviceId") String newDeviceId);
}
