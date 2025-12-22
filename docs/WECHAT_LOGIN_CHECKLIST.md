# 微信登录配置检查单

## 后端配置检查 ✅

### 1. 配置文件
- [ ] 已在微信开放平台获得 AppID 和 AppSecret
- [ ] 已修改 `application.properties` 配置:
  ```properties
  oauth.wechat.app-id=您的AppID
  oauth.wechat.app-secret=您的AppSecret
  ```
- [ ] AppSecret 已妥善保管,未提交到代码仓库

### 2. 代码文件
已实现的文件:
- ✅ `OAuthConfig.java` - 配置类
- ✅ `OAuthService.java` - 微信登录验证服务
- ✅ `AuthService.java` - 认证服务
- ✅ `AuthController.java` - 登录接口
- ✅ `WechatLoginRequest.java` - 请求DTO
- ✅ `WechatUserInfo.java` - 用户信息DTO

### 3. 数据库
需要的表:
- ✅ `user` - 用户基本信息表
- ✅ `user_auth` - 第三方登录关联表
- ✅ `user_token` - token 管理表

### 4. 依赖检查
- ✅ OkHttp - HTTP客户端 (用于调用微信API)
- ✅ Jackson - JSON解析
- ✅ Nimbus JOSE JWT - JWT处理

## 客户端集成检查 📱

### Android
- [ ] 已集成微信 SDK
- [ ] 已配置 WXEntryActivity
- [ ] 已在 AndroidManifest.xml 中声明权限和 Activity
- [ ] 已在微信开放平台配置应用签名
- [ ] 已测试微信是否已安装

### iOS
- [ ] 已通过 CocoaPods 安装 WechatOpenSDK
- [ ] 已在 Info.plist 配置 URL Scheme
- [ ] 已在微信开放平台配置 Bundle ID
- [ ] 已配置 Universal Link (可选)
- [ ] 已实现 WXApiDelegate

## API接口测试 🧪

### 接口信息
- **URL**: `POST /api/app/auth/login/wechat`
- **请求体**: 
  ```json
  {
    "code": "微信返回的授权码"
  }
  ```

### 测试场景
- [ ] 正常登录流程
- [ ] code 过期(5分钟后)
- [ ] code 重复使用
- [ ] code 格式错误
- [ ] AppID/AppSecret 配置错误
- [ ] 网络异常处理

### 预期响应
成功:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "...",
    "refresh_token": "...",
    "expires_in": 7200,
    "user": {
      "id": "user_xxx",
      "nickname": "微信昵称",
      "avatar": "头像URL"
    }
  }
}
```

失败:
```json
{
  "code": 401,
  "message": "微信授权失败: invalid code"
}
```

## 上线前检查 🚀

### 安全性
- [ ] AppSecret 使用环境变量管理
- [ ] 接口使用 HTTPS
- [ ] JWT token 设置了合理的过期时间
- [ ] 日志中不输出敏感信息

### 性能
- [ ] 微信API调用设置了超时时间(已设置10秒)
- [ ] HTTP客户端连接池已配置

### 监控
- [ ] 记录登录成功/失败日志
- [ ] 记录微信API调用耗时
- [ ] 记录异常情况

### 用户体验
- [ ] 首次登录自动创建账号
- [ ] 支持已有账号绑定微信
- [ ] 错误提示清晰友好

## 常见错误排查 🔍

### 错误1: "微信登录配置未设置"
**原因**: application.properties 中的配置未修改或配置错误
**解决**: 检查配置是否正确,不能是 `YOUR_WECHAT_APP_ID`

### 错误2: "微信授权失败: invalid code"
**原因**: 
- code 已过期(超过5分钟)
- code 已被使用过
- code 不是当前 AppID 生成的

**解决**: 让用户重新授权,获取新的 code

### 错误3: "微信授权请求失败"
**原因**: 网络问题或微信API服务异常
**解决**: 检查网络连接,稍后重试

### 错误4: 无法获取用户信息
**原因**: 
- 用户拒绝授权
- scope 设置为 snsapi_base

**解决**: 确保使用 snsapi_userinfo scope

## 下一步 📋

配置完成后:
1. ✅ 填写真实的 AppID 和 AppSecret
2. ✅ 启动服务器测试接口
3. ✅ 客户端集成微信 SDK
4. ✅ 端到端测试完整登录流程
5. ✅ 测试各种异常场景
6. ✅ 准备上线

## 联系支持 💬

如有问题:
- 微信开放平台文档: https://developers.weixin.qq.com/doc/oplatform/
- 微信开发者社区: https://developers.weixin.qq.com/community/
