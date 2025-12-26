# PayPal支付集成指南

## 获取PayPal沙盒密钥

### 1. 登录PayPal开发者平台
访问：https://developer.paypal.com/

使用你的PayPal账号登录（已有账号：`sb-vxxoc34264340@business.example.com`）

### 2. 创建应用获取凭证

1. 进入 **Dashboard** → **My Apps & Credentials**
2. 切换到 **Sandbox** 标签
3. 点击 **Create App** 按钮
4. 输入应用名称（如：CameraServerApp）
5. 点击 **Create App**

### 3. 获取密钥信息

创建完成后，你会看到：
- **Client ID**: 复制这个值
- **Secret**: 点击"Show"按钮，复制这个值

### 4. 配置到项目

打开 `src/main/resources/application.properties`，填入获取的密钥：

```properties
# PayPal支付配置(沙盒环境)
paypal.mode=sandbox
paypal.client-id=你的Client_ID
paypal.client-secret=你的Secret
paypal.return-url=https://cam.pura365.cn/api/callback/paypal/success
paypal.cancel-url=https://cam.pura365.cn/api/callback/paypal/cancel
```

## PayPal沙盒测试账号

### 查看测试账号

1. 在Developer Dashboard点击 **Sandbox** → **Accounts**
2. 你会看到自动生成的测试账号：
   - **Business账号**（商家）：`sb-vxxoc34264340@business.example.com`
   - **Personal账号**（买家）：通常也会自动生成

### 创建新的测试买家账号

如果需要额外的测试账号：
1. 点击 **Create Account**
2. 选择 **Personal** (个人账号)
3. 填写信息并创建
4. 记录邮箱和密码

## 测试PayPal支付流程

### API测试流程

1. **创建订单**
```bash
POST /api/app/payment/create
Authorization: Bearer {token}

{
  "product_type": "cloud_storage",
  "product_id": "plan_7day",
  "device_id": "device_123",
  "payment_method": "paypal"
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "order_id": "order_1234567890",
    "amount": 9.90,
    "currency": "CNY"
  }
}
```

2. **发起PayPal支付**
```bash
POST /api/app/payment/paypal
Authorization: Bearer {token}

{
  "order_id": "order_1234567890"
}
```

**响应**：
```json
{
  "code": 200,
  "data": {
    "approval_url": "https://www.sandbox.paypal.com/checkoutnow?token=EC-xxx",
    "paypal_order_id": "PAYID-xxx"
  }
}
```

3. **用户完成支付**
   - 前端打开 `approval_url`
   - 用户登录PayPal沙盒账号
   - 确认支付
   - PayPal自动回调后端
   - 后端激活服务

### 前端集成示例

#### Web/H5

```javascript
async function payWithPayPal() {
  // 1. 创建订单
  const orderResponse = await fetch('/api/app/payment/create', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      product_type: 'cloud_storage',
      product_id: 'plan_7day',
      device_id: 'device_123',
      payment_method: 'paypal'
    })
  });
  const orderData = await orderResponse.json();
  const orderId = orderData.data.order_id;

  // 2. 获取PayPal支付URL
  const paypalResponse = await fetch('/api/app/payment/paypal', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ order_id: orderId })
  });
  const paypalData = await paypalResponse.json();

  // 3. 跳转到PayPal支付页面
  window.location.href = paypalData.data.approval_url;
  
  // 4. 用户完成支付后，PayPal会重定向回你的网站
  // 后端会自动处理回调并激活服务
}
```

#### React Native

```javascript
import { WebView } from 'react-native-webview';

function PayPalPayment({ orderId }) {
  const [showWebView, setShowWebView] = useState(false);
  const [approvalUrl, setApprovalUrl] = useState('');

  const startPayment = async () => {
    // 获取PayPal支付URL
    const response = await fetch('https://api.example.com/api/app/payment/paypal', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ order_id: orderId })
    });
    const data = await response.json();
    
    setApprovalUrl(data.data.approval_url);
    setShowWebView(true);
  };

  const handleWebViewNavigationStateChange = (newNavState) => {
    const { url } = newNavState;
    
    // 检测是否是回调URL
    if (url.includes('/payment/success')) {
      setShowWebView(false);
      // 显示支付成功
      Alert.alert('支付成功');
    } else if (url.includes('/payment/cancelled')) {
      setShowWebView(false);
      // 显示支付取消
      Alert.alert('支付已取消');
    } else if (url.includes('/payment/failed')) {
      setShowWebView(false);
      // 显示支付失败
      Alert.alert('支付失败');
    }
  };

  if (showWebView) {
    return (
      <WebView
        source={{ uri: approvalUrl }}
        onNavigationStateChange={handleWebViewNavigationStateChange}
      />
    );
  }

  return (
    <Button title="使用PayPal支付" onPress={startPayment} />
  );
}
```

## 常见问题

### Q: 如何切换到生产环境？
A: 
1. 在PayPal Developer Dashboard切换到**Live**标签
2. 创建生产环境的App
3. 获取生产环境的Client ID和Secret
4. 修改配置：`paypal.mode=live`
5. 更新return-url和cancel-url为生产域名

### Q: 支付金额显示为USD？
A: PayPal主要使用USD，你可以在`PayPalService.createPayment()`中修改货币类型，但需要确保你的PayPal账号支持该货币。

### Q: 回调URL必须是HTTPS吗？
A: 
- 沙盒环境：可以使用HTTP（但建议HTTPS）
- 生产环境：必须使用HTTPS

### Q: 如何测试不同的支付结果？
A: 
- 使用不同的沙盒测试账号
- 在沙盒环境中可以模拟各种支付状态
- 查看PayPal沙盒日志了解详细信息

### Q: 前端可以唤起PayPal APP吗？
A: 
- 在移动端使用WebView打开approval_url
- PayPal会自动检测并尝试唤起PayPal APP
- 如果没有安装APP，会在WebView中完成支付

## 支付流程图

```
┌─────────┐     创建订单      ┌─────────┐
│  前端   │ ─────────────────>│  后端   │
└─────────┘                   └─────────┘
     │                             │
     │        返回order_id         │
     │<────────────────────────────│
     │                             │
     │     获取PayPal URL         │
     │─────────────────────────────>│
     │                             │
     │    返回approval_url         │
     │<────────────────────────────│
     │                             │
     │   打开approval_url          │
     │────────────>┌──────────┐   │
     │             │  PayPal  │   │
     │             │  沙盒    │   │
     │             └──────────┘   │
     │                  │          │
     │      用户完成支付  │          │
     │                  │          │
     │                  │  回调验证 │
     │                  │─────────>│
     │                  │          │
     │                  │  激活服务 │
     │                  │          │
     │      重定向成功页面          │
     │<─────────────────────────────│
```

## 相关链接

- PayPal开发者平台：https://developer.paypal.com/
- PayPal沙盒：https://sandbox.paypal.com/
- PayPal REST API文档：https://developer.paypal.com/docs/api/overview/
- PayPal测试工具：https://developer.paypal.com/tools/sandbox/
