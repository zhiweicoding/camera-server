package com.pura365.camera.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pura365.camera.domain.UserPushToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户推送Token Repository
 */
@Mapper
public interface UserPushTokenRepository extends BaseMapper<UserPushToken> {
}
