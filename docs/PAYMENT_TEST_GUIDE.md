# 支付功能测试指南

## 当前配置状态

### ✅ 可直接测试（Mock模式）
以下功能无需真实密钥即可测试：
- Google Play支付 - 完全Mock模式
- 微信支付 - 失败时自动降级到Mock
- Apple支付 - 可传入测试数据

### ⚠️ 需要配置密钥
要进行真实支付测试，需要配置：
- 微信支付商户号和密钥
- Apple共享密钥（可选）

## 测试流程

### 1. 创建订单
```bash
POST /api/app/payment/create
Authorization: Bearer {token}

{
  "product_type": "cloud_storage",
  "product_id": "plan_7day",
  "device_id": "your_device_id",
  "payment_method": "wechat"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "order_id": "order_1234567890",
    "amount": 9.90,
    "currency": "CNY",
    "created_at": "2025-12-22T08:00:00Z"
  }
}
```

### 2. 微信支付（沙盒/Mock）
```bash
POST /api/app/payment/wechat
Authorization: Bearer {token}

{
  "order_id": "order_1234567890"
}
```

**响应示例**（Mock数据）：
```json
{
  "code": 200,
  "data": {
    "appid": "wx07cab42b39acdf75",
    "partnerid": "1234567890",
    "prepayid": "sandbox_prepay_order_1234567890",
    "package": "Sign=WXPay",
    "noncestr": "abc123...",
    "timestamp": "1703232000",
    "sign": "sandbox_sign_..."
  }
}
```

### 3. Apple支付（沙盒）
```bash
POST /api/app/payment/apple
Authorization: Bearer {token}

{
  "order_id": "order_1234567890",
  "receipt_data": "base64_encoded_receipt_data"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "transaction_id": "apple_transaction_123",
    "status": "completed"
  }
}
```

### 4. Google Play支付（Mock）
```bash
POST /api/app/payment/google
Authorization: Bearer {token}

{
  "order_id": "order_1234567890",
  "purchase_token": "test_purchase_token",
  "product_id": "plan_7day"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "order_id": "order_1234567890",
    "status": "completed",
    "google_order_id": "mock_google_order_1234567890"
  }
}
```

### 5. 查询订单状态
```bash
GET /api/app/payment/order_1234567890/status
Authorization: Bearer {token}
```

## 密钥配置指南

### 微信支付密钥获取

1. **注册微信支付商户**
   - 访问：https://pay.weixin.qq.com/
   - 需要：营业执照、对公账户等

2. **获取商户号和密钥**
   - 登录商户平台
   - 进入"账户中心" → "API安全"
   - 记录商户号(mch_id)
   - 设置API密钥(32位字符串)

3. **配置证书**（可选，用于退款等高级功能）
   - 下载商户证书apiclient_cert.p12
   - 放到 `src/main/resources/cert/`
   - 配置证书路径

4. **配置项**
```properties
wechat.pay.app-id=wx07cab42b39acdf75
wechat.pay.mch-id=1234567890
wechat.pay.api-key=your_32_character_api_key_here
wechat.pay.cert-path=classpath:cert/apiclient_cert.p12
wechat.pay.notify-url=https://your-domain.com/api/callback/wechat
wechat.pay.use-sandbox=true
```

### Apple支付密钥获取

1. **登录App Store Connect**
   - 访问：https://appstoreconnect.apple.com/
   - 选择你的App

2. **创建App内购买项目**
   - 功能 → App内购买项目
   - 创建消耗型/非消耗型产品

3. **生成共享密钥**
   - App内购买项目 → App专用共享密钥
   - 点击"生成"
   - 复制共享密钥

4. **配置项**
```properties
apple.pay.sandbox-url=https://sandbox.itunes.apple.com/verifyReceipt
apple.pay.production-url=https://buy.itunes.apple.com/verifyReceipt
apple.pay.shared-secret=your_shared_secret_here
apple.pay.use-sandbox=true
```

### Google Play密钥获取（可选）

1. **Google Cloud Console设置**
   - 访问：https://console.cloud.google.com/
   - 创建项目或选择现有项目
   - 启用"Google Play Android Developer API"

2. **创建服务账号**
   - IAM和管理 → 服务账号
   - 创建服务账号
   - 下载JSON密钥文件

3. **关联Google Play Console**
   - 访问：https://play.google.com/console/
   - 设置 → API访问权限
   - 关联服务账号

4. **配置项**
```properties
google.pay.package-name=com.pura365.camera
google.pay.service-account-key=classpath:google-service-account.json
```

**注意**：当前Google Play使用Mock模式，暂时无需配置。

## 常见问题

### Q: 微信支付返回错误怎么办？
A: 代码会自动降级到Mock模式，返回模拟数据用于测试。检查日志中的错误信息。

### Q: Apple收据验证失败？
A: 
- 确保使用的是沙盒环境的收据
- 检查shared-secret是否正确
- 验证失败会返回status=failed，不会崩溃

### Q: Google Play如何测试？
A: 当前使用Mock模式，直接调用接口即可，会返回成功的模拟数据。

### Q: 支付回调如何测试？
A: 
- 微信支付：需要外网可访问的回调URL，或使用内网穿透工具
- Apple/Google：客户端直接调用验证接口，无需回调
- **推荐方式**：使用测试接口直接模拟支付成功（见下方「模拟支付成功测试」章节）

## 模拟支付成功测试（无需真实支付）

当支付渠道还未申请下来时，可以使用以下测试接口直接模拟支付成功，用于测试：
- 订单生成是否正确
- 报表数据是否正确
- 导出功能是否正常

### 1. 创建订单并直接模拟支付成功
```bash
POST /api/internal/payment-test/create-and-pay
Authorization: Bearer {token}

{
  "product_type": "cloud_storage",
  "product_id": "plan_7day",
  "device_id": "your_device_id",
  "payment_method": "wechat"
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "order_id": "order_1735134000000",
    "amount": 9.90,
    "currency": "CNY",
    "payment_method": "wechat",
    "mock_transaction_id": "TEST_1735134000000",
    "payment_success": true,
    "message": "模拟支付成功，已激活云存储"
  }
}
```

### 2. 对已有订单模拟支付成功
```bash
POST /api/internal/payment-test/mock-pay/{orderId}?payment_method=wechat
Authorization: Bearer {token}
```

### 3. 批量创建测试订单（用于测试报表/导出）
```bash
POST /api/internal/payment-test/batch-create
Authorization: Bearer {token}

{
  "device_id": "your_device_id",
  "product_id": "plan_7day",
  "payment_method": "wechat",
  "count": 10
}
```

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "total_requested": 10,
    "success_count": 10,
    "fail_count": 0,
    "message": "批量创建完成：成功 10 条，失败 0 条"
  }
}
```

### 4. 检查测试接口状态
```bash
GET /api/internal/payment-test/status
```

### 生产环境禁用测试接口
在 `application.properties` 中添加以下配置禁用测试接口：
```properties
payment.test.enabled=false
```

## 生产环境检查清单

部署到生产环境前，确保：

- [ ] 微信支付配置正式环境密钥
- [ ] 修改 `wechat.pay.use-sandbox=false`
- [ ] 配置Apple正式环境URL
- [ ] 修改 `apple.pay.use-sandbox=false`
- [ ] 配置正确的支付回调域名
- [ ] 测试所有支付流程
- [ ] 配置支付成功后的业务逻辑
- [ ] 添加支付异常监控和告警
- [ ] 禁用支付测试接口 `payment.test.enabled=false`
