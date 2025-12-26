# 微信登录测试指南

## 测试准备

### 1. 配置微信AppID和AppSecret

编辑 `application.properties`:
```properties
oauth.wechat.app-id=你的AppID
oauth.wechat.app-secret=你的AppSecret
```

### 2. 启动服务器

```bash
mvn spring-boot:run
```

## 测试方法

### 方法一: 检查配置是否正确 ✅

**接口**: `GET /api/test/wechat/check-config`

**使用 curl**:
```bash
curl http://localhost:8080/api/test/wechat/check-config
```

**使用 PowerShell**:
```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/test/wechat/check-config" -Method GET | Select-Object -ExpandProperty Content
```

**预期响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "configured": true,
    "appId": "wx1234567890abcdef",
    "appSecretSet": true,
    "message": "配置正确,可以使用微信登录"
  }
}
```

### 方法二: 使用真实的 code 测试 (推荐) ✅

这是最准确的测试方法,需要真实的微信授权流程。

#### 步骤1: 获取 code

有两种方式:

**方式A: 使用简单的测试APP (推荐)**

创建一个最简单的测试APP来获取code:

**Android 测试代码**:
```java
// build.gradle
dependencies {
    implementation 'com.tencent.mm.opensdk:wechat-sdk-android:6.8.0'
}

// MainActivity.java
public class MainActivity extends AppCompatActivity {
    private IWXAPI api;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化微信SDK
        api = WXAPIFactory.createWXAPI(this, "你的AppID", true);
        api.registerApp("你的AppID");
        
        // 点击按钮发起登录
        findViewById(R.id.btn_login).setOnClickListener(v -> {
            SendAuth.Req req = new SendAuth.Req();
            req.scope = "snsapi_userinfo";
            req.state = "test";
            api.sendReq(req);
        });
    }
}

// WXEntryActivity.java (放在包名.wxapi目录下)
public class WXEntryActivity extends Activity implements IWXAPIEventHandler {
    @Override
    public void onResp(BaseResp resp) {
        if (resp instanceof SendAuth.Resp) {
            SendAuth.Resp authResp = (SendAuth.Resp) resp;
            if (authResp.errCode == BaseResp.ErrCode.ERR_OK) {
                String code = authResp.code;
                // 显示code,方便复制
                Toast.makeText(this, "Code: " + code, Toast.LENGTH_LONG).show();
                Log.i("WechatTest", "Code: " + code);
            }
        }
        finish();
    }
}
```

**方式B: 使用现有的客户端**

如果你已经有客户端APP,让前端同事帮忙获取一个code,然后在console打印出来。

#### 步骤2: 用获取到的 code 测试后端

**接口**: `POST /api/test/wechat/verify-code`

**使用 curl**:
```bash
curl -X POST http://localhost:8080/api/test/wechat/verify-code \
  -H "Content-Type: application/json" \
  -d "{\"code\": \"你获取到的code\"}"
```

**使用 PowerShell**:
```powershell
$body = @{
    code = "你获取到的code"
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/test/wechat/verify-code" `
    -Method POST `
    -ContentType "application/json" `
    -Body $body
```

**使用 Postman**:
1. Method: POST
2. URL: `http://localhost:8080/api/test/wechat/verify-code`
3. Headers: `Content-Type: application/json`
4. Body (raw JSON):
   ```json
   {
     "code": "你获取到的code"
   }
   ```

**成功响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "success": true,
    "message": "微信授权验证成功!",
    "openId": "oXXXXXXXXXXXXXXXXXXXXXXXXX",
    "unionId": "oYYYYYYYYYYYYYYYYYYYYYYYY",
    "nickname": "微信用户昵称",
    "avatar": "https://thirdwx.qlogo.cn/...",
    "gender": 1,
    "country": "中国",
    "province": "广东",
    "city": "深圳"
  }
}
```

**失败响应**:
```json
{
  "code": 500,
  "message": "微信验证失败: invalid code",
  "data": {
    "success": false,
    "error": "invalid code",
    "errorType": "RuntimeException"
  }
}
```

### 方法三: 测试完整登录流程 ✅

使用正式的登录接口:

**接口**: `POST /api/app/auth/login/wechat`

```bash
curl -X POST http://localhost:8080/api/app/auth/login/wechat \
  -H "Content-Type: application/json" \
  -d "{\"code\": \"你获取到的code\"}"
```

**成功响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refresh_token": "a1b2c3d4e5f6...",
    "expires_in": 7200,
    "user": {
      "id": "user_123456",
      "phone": null,
      "nickname": "微信用户",
      "avatar": "https://thirdwx.qlogo.cn/xxx",
      "role": "user"
    }
  }
}
```

这个接口会:
1. ✅ 验证 code
2. ✅ 获取微信用户信息
3. ✅ 创建或查找用户
4. ✅ 生成 JWT token
5. ✅ 返回完整登录信息

## 常见问题排查

### 1. "微信登录配置未设置"

**原因**: application.properties 配置未修改

**解决**: 
```bash
# 检查配置
curl http://localhost:8080/api/test/wechat/check-config
```

### 2. "invalid code"

**原因**: 
- code 已过期(5分钟)
- code 已被使用过
- code 不是当前 AppID 生成的

**解决**: 重新获取新的 code

### 3. "40001: invalid credential"

**原因**: AppSecret 配置错误

**解决**: 检查 application.properties 中的 app-secret 是否正确

### 4. "40013: invalid appid"

**原因**: AppID 配置错误

**解决**: 检查 application.properties 中的 app-id 是否正确

### 5. code 从哪里获取?

**回答**: 
- ❌ 不能通过网页直接获取(移动应用AppID不支持)
- ✅ 必须通过微信SDK在手机上获取
- ✅ 可以写一个简单的测试APP来获取

## 测试检查清单

- [ ] 配置已正确填写 (check-config 接口返回 configured: true)
- [ ] 能够在手机上获取到 code
- [ ] verify-code 接口测试通过,返回用户信息
- [ ] login/wechat 接口测试通过,返回 token
- [ ] 数据库中成功创建了 user 和 user_auth 记录

## 访问 Swagger 文档

启动服务器后,访问:
```
http://localhost:8080/swagger-ui.html
```

在 Swagger 中可以直接测试所有接口。

## 生产环境注意

⚠️ **重要**: 测试完成后,请删除测试控制器:
```bash
rm src/main/java/com/pura365/camera/controller/test/WechatTestController.java
```

测试接口仅用于开发环境,不应部署到生产环境!
