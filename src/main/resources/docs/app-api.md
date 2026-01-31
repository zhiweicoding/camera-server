# 智能摄像头 App 后端接口文档（按当前实现）
统一说明
- 所有接口统一返回结构：
```json
{
  "code": 0,
  "message": "success",
  "data": {...}
}
```
- 所有业务接口（除 /auth/**、/api/mqtt/**、/api/webrtc/**）均要求 Header 携带：
  - Authorization: Bearer <access_token>
- 时间字段统一使用 UTC ISO8601 格式，例如：2025-09-06T19:15:00Z

## 一、认证模块（Auth）

### 1.1 账号密码登录
POST /auth/login/password
请求体
```json
{
  "account": "手机号 / 邮箱 / 用户名",
  "password": "明文密码"
}
```
响应 data
```json
{
  "token": "access_token_jwt",
  "refresh_token": "refresh_token_xxx",
  "expires_in": 7200,
  "user": {
    "id": "user_001",
    "phone": "158****9999",
    "nickname": "用户昵称",
    "avatar": "https://xxx/avatar.jpg",
    "email": "user@example.com"
  }
}
```

### 1.2 刷新 Token
POST /auth/token/refresh
请求体
```json
{
  "refresh_token": "refresh_token_xxx"
}
```
响应 data
```json
{
  "token": "new_access_token",
  "refresh_token": "new_refresh_token",
  "expires_in": 7200,
  "user": { ... 同登录返回 ... }
}
```

### 1.3 登出
POST /auth/logout
Header
- Authorization: Bearer <token>
请求体（可选）
```json
{
  "refresh_token": "refresh_token_xxx"
}
```
响应
```json
{
  "code": 0,
  "message": "登出成功",
  "data": null
}
```

### 1.4 第三方 / 短信登录（占位，未实现）
以下接口当前仅返回 501 未实现，预留给后续对接：
- POST /auth/login/wechat
- POST /auth/login/apple
- POST /auth/login/google
- POST /auth/sms/send
- POST /auth/login/sms

## 二、用户模块（User）

### 2.1 获取用户信息
GET /user/info
响应 data
```json
{
  "id": "user_001",
  "phone": "15888889999",
  "nickname": "用户昵称",
  "avatar": "https://xxx/avatar.jpg",
  "email": "user@example.com",
  "created_at": "2025-01-01T00:00:00Z"
}
```

### 2.2 更新用户信息
PUT /user/update
请求体
```json
{
  "nickname": "新昵称",
  "avatar": "https://xxx/new_avatar.jpg"
}
```
响应 data
```json
{
  "id": "user_001",
  "phone": "15888889999",
  "nickname": "新昵称",
  "avatar": "https://xxx/new_avatar.jpg",
  "email": "user@example.com"
}
```

### 2.3 上传头像
POST /user/avatar
Content-Type: multipart/form-data
表单字段
- file: 图片文件
响应 data
```json
{
  "url": "/uploads/avatars/xxx.jpg"
}
```

## 三、设备模块（Device）

### 3.1 获取设备列表
GET /devices
响应 data（数组）
```json
[
  {
    "id": "device_001",
    "name": "客厅",
    "model": null,
    "status": "online",
    "has_cloud_storage": true,
    "cloud_expire_at": "2025-12-31T23:59:59Z",
    "thumbnail_url": null,
    "last_online_at": "2025-09-06T10:00:00Z"
  }
]
```

### 3.2 获取设备详情
GET /devices/{id}/info
响应 data
```json
{
  "id": "device_001",
  "name": "客厅",
  "model": null,
  "firmware_version": "v2.1.3",
  "status": "online",
  "has_cloud_storage": true,
  "cloud_expire_at": "2025-12-31T23:59:59Z",
  "thumbnail_url": null,
  "wifi_ssid": "ACCGE-5G",
  "wifi_signal": null,
  "sd_card": {
    "total": 0,
    "used": 0,
    "available": 0
  },
  "settings": {
    "motion_detection": true,
    "night_vision": true,
    "audio_enabled": true,
    "flip_image": false,
    "sensitivity": "medium"
  },
  "last_online_at": "2025-09-06T10:00:00Z"
}
```

### 3.3 添加设备（立即绑定）
POST /devices/add
请求体
```json
{
  "device_id": "设备序列号",
  "name": "客厅摄像头",
  "wifi_ssid": "WiFi名称",
  "wifi_password": "WiFi密码"
}
```
响应 data
```json
{
  "id": "device_003",
  "name": "客厅摄像头",
  "model": null,
  "status": "online"
}
```

### 3.4 删除 / 解绑设备
DELETE /devices/{id}
说明
- 仅删除当前用户与该设备的绑定关系，不删除设备本身。

响应
```json
{
  "code": 0,
  "message": "success",
  "data": null
}
```

### 3.5 更新设备名称
PUT /devices/{id}
请求体
```json
{
  "name": "新设备名称"
}
```
响应 data
```json
{
  "id": "device_001",
  "name": "新设备名称"
}
```

### 3.6 更新设备设置
PUT /devices/{id}/settings
请求体
```json
{
  "motion_detection": true,
  "night_vision": true,
  "audio_enabled": false,
  "flip_image": true,
  "sensitivity": "high"
}
```
响应 data（更新后的设置）
```json
{
  "motion_detection": true,
  "night_vision": true,
  "audio_enabled": false,
  "flip_image": true,
  "sensitivity": "high"
}
```

### 3.7 获取本地录像列表
GET /devices/{id}/local-videos
Query 参数
- date: 可选，YYYY-MM-DD
- page: 可选，默认 1
- page_size: 可选，默认 20

响应 data
```json
{
  "list": [
    {
      "id": "local_video_001",
      "device_id": "device_001",
      "type": "alarm",
      "title": "检测到移动",
      "thumbnail_url": "https://xxx/thumb.jpg",
      "video_url": "https://xxx/video.mp4",
      "duration": 30,
      "size": 15000000,
      "created_at": "2025-09-06T19:15:00Z"
    }
  ],
  "total": 50,
  "page": 1,
  "page_size": 20
}
```

### 3.8 云台控制（HTTP 通道，可选使用）
POST /devices/{id}/ptz
请求体
```json
{
  "direction": "up",
  "speed": 5
}
```
说明
- direction: up/down/left/right/stop
- 后端通过 MQTT 发送指令到设备
- 推荐在 WebRTC 成功后，仍然优先使用 DataChannel 文本命令控制 PTZ，这个接口作为补充

响应
```json
{
  "code": 0,
  "message": "success"
}
```

### 3.9 清除云录像
DELETE /devices/{id}/cloud-videos
说明
- 异步删除指定设备的所有云存储视频文件
- 接口立即返回，后台异步执行删除
- 此操作不可逆，请谨慎调用

响应
```json
{
  "code": 0,
  "message": "清除任务已提交，正在后台删除中",
  "data": null
}
```

## 四、WiFi 配网模块

### 4.1 WiFi 列表（历史记录）
GET /wifi/scan
响应 data
```json
[
  {
    "ssid": "ACCGE-5G",
    "signal": 85,
    "security": "WPA2",
    "is_connected": true
  },
  {
    "ssid": "HomeNetwork",
    "signal": 70,
    "security": "WPA2",
    "is_connected": false
  }
]
```

### 4.2 绑定设备（配网流程）
POST /devices/bind
请求体
```json
{
  "device_sn": "设备序列号",
  "device_name": "客厅摄像头",
  "wifi_ssid": "ACCGE-5G",
  "wifi_password": "password123"
}
```
响应 data
```json
{
  "id": "device_003",
  "name": "客厅摄像头",
  "status": "binding",
  "binding_progress": 0
}
```

### 4.3 查询绑定进度
GET /devices/{id}/binding-status
响应 data
```json
{
  "status": "success",
  "progress": 100,
  "message": "设备绑定成功"
}
```

## 五、消息模块（消息中心）

### 5.1 获取消息列表
GET /messages
Query 参数
- device_id: 可选
- date: 可选，YYYY-MM-DD
- type: 可选 alarm/system/promotion
- page: 可选，默认 1
- page_size: 可选，默认 20

响应 data
```json
{
  "list": [
    {
      "id": 1,
      "type": "alarm",
      "title": "检测到移动",
      "content": "客厅摄像头检测到异常移动",
      "device_id": "device_001",
      "device_name": "客厅",
      "thumbnail_url": "https://xxx/thumb.jpg",
      "video_url": "https://xxx/video.mp4",
      "is_read": false,
      "created_at": "2025-09-06T19:15:00Z"
    }
  ],
  "total": 100,
  "page": 1,
  "page_size": 20
}
```

### 5.2 标记消息已读
POST /messages/{id}/read
响应
```json
{
  "code": 0,
  "message": "标记成功",
  "data": null
}
```

### 5.3 删除消息
DELETE /messages/{id}
响应
```json
{
  "code": 0,
  "message": "删除成功",
  "data": null
}
```

### 5.4 未读消息数量
GET /messages/unread/count
响应 data
```json
{
  "count": 5
}
```

## 六、云存储模块

### 6.1 获取套餐列表
GET /cloud/plans
响应 data
```json
[
  {
    "id": "plan_001",
    "name": "7天循环存储",
    "description": "所有数据，循环存储7天",
    "storage_days": 7,
    "price": 98.00,
    "original_price": 198.00,
    "period": "month",
    "features": ["全天候录制", "移动侦测", "高清存储"]
  }
]
```

### 6.2 订阅云存（创建订单 + 支付信息）
POST /cloud/subscribe
请求体
```json
{
  "device_id": "device_001",
  "plan_id": "plan_001",
  "payment_method": "wechat"
}
```
响应 data
```json
{
  "order_id": "order_001",
  "payment_info": {
    "prepay_id": "mock_prepay_order_001",
    "sign": "mock_sign_order_001"
  }
}
```

### 6.3 云存视频列表
GET /cloud/videos
Query 参数
- device_id: 必填
- date: 可选，YYYY-MM-DD
- page: 可选，默认 1
- page_size: 可选，默认 20

响应 data
```json
{
  "list": [
    {
      "id": "cloud_video_001",
      "device_id": "device_001",
      "type": "alarm",
      "title": "检测到移动",
      "thumbnail_url": "https://xxx/thumb.jpg",
      "video_url": "https://xxx/video.mp4",
      "duration": 30,
      "created_at": "2025-09-06T19:15:00Z"
    }
  ],
  "total": 100,
  "page": 1,
  "page_size": 20
}
```

### 6.4 设备云存订阅状态
GET /cloud/subscription/{deviceId}
响应 data
```json
{
  "is_subscribed": true,
  "plan_id": "plan_001",
  "plan_name": "7天循环存储",
  "expire_at": "2025-12-31T23:59:59Z",
  "auto_renew": true
}
```

## 七、支付模块

### 7.1 创建支付订单
POST /payment/create
请求体
```json
{
  "product_type": "cloud_storage",
  "product_id": "plan_001",
  "device_id": "device_001",
  "payment_method": "wechat"
}
```
响应 data
```json
{
  "order_id": "order_001",
  "amount": 98.00,
  "currency": "CNY",
  "created_at": "2025-09-06T19:15:00Z"
}
```

### 7.2 查询支付状态
GET /payment/{id}/status
说明
- {id} 为 order_id

响应 data
```json
{
  "order_id": "order_001",
  "status": "paid",
  "amount": 98.00,
  "paid_at": "2025-09-06T19:16:00Z"
}
```

### 7.3 微信支付参数
POST /payment/wechat
请求体
```json
{
  "order_id": "order_001"
}
```
响应 data
```json
{
  "appid": "wx_app_id_mock",
  "partnerid": "partner_mock",
  "prepayid": "mock_prepay_order_001",
  "package": "Sign=WXPay",
  "noncestr": "随机字符串",
  "timestamp": "1630934400",
  "sign": "mock_sign_order_001"
}
```

### 7.4 PayPal 支付
POST /payment/paypal
请求体
```json
{
  "order_id": "order_001"
}
```
响应 data
```json
{
  "approval_url": "https://www.paypal.com/checkout?token=order_001",
  "paypal_order_id": "paypal_order_001"
}
```

### 7.5 Apple Pay 支付
POST /payment/apple
请求体
```json
{
  "order_id": "order_001",
  "payment_token": "Apple Pay token"
}
```
响应 data
```json
{
  "transaction_id": "apple_order_001",
  "status": "completed"
}
```

## 八、视频流与回放模块

### 8.1 开始直播流
POST /devices/{id}/stream/start
请求体
```json
{
  "quality": "hd",
  "protocol": "webrtc"
}
```
响应 data
```json
{
  "stream_id": "stream_001",
  "protocol": "webrtc",
  "signaling_url": "http://<server>/api/webrtc",
  "ice_servers": [
    { "urls": "stun:stun.l.google.com:19302" }
  ],
  "expires_at": "2025-09-06T20:15:00Z"
}
```
说明
- 真正的 WebRTC 建链和 PTZ 控制走 WEBRTC-API 文档中的 /api/mqtt + /api/webrtc

### 8.2 停止直播流
POST /devices/{id}/stream/stop
请求体
```json
{
  "stream_id": "stream_001"
}
```
响应
```json
{
  "code": 0,
  "message": "直播流已停止",
  "data": null
}
```

### 8.3 获取回放视频播放信息
GET /videos/{id}/playback
说明
- {id} 可以是 local_video.video_id / cloud_video.video_id / device_record.record_id

响应 data
```json
{
  "video_url": "https://xxx/video.mp4",
  "hls_url": null,
  "duration": 120,
  "expires_at": "2025-09-06T20:15:00Z"
}
```
说明
- 本地录像和手动录像 expires_at 为 null
- 云存录像 expires_at 为对应云存订阅的过期时间（如有）

## 九、WebRTC 信令与 MQTT 控制（给播放器/调试用）

以下接口主要给前端 WebRTC 播放器使用，详细流程参见 WEBRTC-API.md，这里只列出路径：
- POST /api/mqtt/device/{deviceId}/webrtc/offer
- GET  /api/webrtc/offer/{sid}
- POST /api/mqtt/device/{deviceId}/webrtc/answer
- POST /api/mqtt/device/{deviceId}/webrtc/candidate
- GET  /api/webrtc/candidates/{sid}

以及其他控制：
- POST /api/mqtt/device/{deviceId}/info
- POST /api/mqtt/device/{deviceId}/format
- POST /api/mqtt/device/{deviceId}/reboot
- POST /api/mqtt/device/{deviceId}/rotate?enable=0|1
- POST /api/mqtt/device/{deviceId}/whiteled?enable=0|1
- POST /api/mqtt/device/{deviceId}/register-ssid?ssid=XXX

## 十、App 其他模块（版本 & 反馈）

### 10.1 检查应用版本
GET /app/version
Query 参数
- platform: ios/android
- current_version: 当前 App 版本

响应 data
```json
{
  "latest_version": "1.1.0",
  "min_version": "1.0.0",
  "download_url": "https://xxx/app.apk",
  "release_notes": "1. 修复已知问题\n2. 性能优化",
  "force_update": false
}
```

### 10.2 提交反馈
POST /feedback
请求体
```json
{
  "content": "反馈内容",
  "contact": "联系方式（可选）",
  "images": ["https://xxx/img1.jpg", "https://xxx/img2.jpg"]
}
```
响应 data
```json
{
  "feedback_id": "feedback_001",
  "created_at": "2025-09-06T19:15:00Z"
}
```