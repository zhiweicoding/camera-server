# 微信登录集成指南

## 一、准备工作

### 1. 在微信开放平台申请移动应用

1. 访问 [微信开放平台](https://open.weixin.qq.com/)
2. 注册并登录开发者账号
3. 创建移动应用 → 填写应用信息(名称、简介、图标等)
4. 填写应用签名(Android)和Bundle ID(iOS)
5. 提交审核,等待审核通过(通常1-3个工作日)

### 2. 获取配置信息

审核通过后,在应用详情页面可以看到:
- **AppID**: 应用唯一标识
- **AppSecret**: 应用密钥(用于服务端验证)

### 3. 配置后端

编辑 `src/main/resources/application.properties`:

```properties
# 替换为您的真实配置
oauth.wechat.app-id=wx1234567890abcdef
oauth.wechat.app-secret=abcdef1234567890abcdef1234567890
```

⚠️ **重要**: AppSecret 非常敏感,不要提交到代码仓库!建议使用环境变量或配置中心管理。

## 二、客户端集成

### Android 集成

1. **下载微信 SDK**
   - 访问 [微信开放平台文档](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)
   - 下载最新版本的 Android SDK

2. **配置 build.gradle**
   ```gradle
   dependencies {
       implementation 'com.tencent.mm.opensdk:wechat-sdk-android:6.8.0'
   }
   ```

3. **注册应用**
   ```java
   IWXAPI api = WXAPIFactory.createWXAPI(context, "YOUR_APP_ID", true);
   api.registerApp("YOUR_APP_ID");
   ```

4. **发起登录请求**
   ```java
   if (!api.isWXAppInstalled()) {
       Toast.makeText(this, "未安装微信", Toast.LENGTH_SHORT).show();
       return;
   }
   
   SendAuth.Req req = new SendAuth.Req();
   req.scope = "snsapi_userinfo";
   req.state = "wechat_sdk_demo_test"; // 自定义状态值
   api.sendReq(req);
   ```

5. **接收授权结果**
   创建 WXEntryActivity:
   ```java
   public class WXEntryActivity extends Activity implements IWXAPIEventHandler {
       private IWXAPI api;
       
       @Override
       protected void onCreate(Bundle savedInstanceState) {
           super.onCreate(savedInstanceState);
           api = WXAPIFactory.createWXAPI(this, "YOUR_APP_ID", false);
           api.handleIntent(getIntent(), this);
       }
       
       @Override
       public void onResp(BaseResp resp) {
           if (resp.getType() == ConstantsAPI.COMMAND_SENDAUTH) {
               SendAuth.Resp authResp = (SendAuth.Resp) resp;
               if (authResp.errCode == BaseResp.ErrCode.ERR_OK) {
                   String code = authResp.code;
                   // 将 code 发送到后端
                   sendCodeToServer(code);
               }
           }
       }
   }
   ```

### iOS 集成

1. **安装 SDK**
   使用 CocoaPods:
   ```ruby
   pod 'WechatOpenSDK'
   ```

2. **注册应用**
   ```swift
   WXApi.registerApp("YOUR_APP_ID", universalLink: "https://yourdomain.com/")
   ```

3. **发起登录**
   ```swift
   let req = SendAuthReq()
   req.scope = "snsapi_userinfo"
   req.state = "wechat_sdk_demo"
   WXApi.send(req)
   ```

4. **处理回调**
   ```swift
   func onResp(_ resp: BaseResp) {
       if let authResp = resp as? SendAuthResp {
           if authResp.errCode == 0 {
               let code = authResp.code
               // 将 code 发送到后端
               sendCodeToServer(code)
           }
       }
   }
   ```

## 三、后端API调用

### 请求示例

**接口**: `POST /api/app/auth/login/wechat`

**请求体**:
```json
{
  "code": "0x1a2b3c4d5e6f"
}
```

**成功响应** (200):
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

**失败响应**:
```json
{
  "code": 401,
  "message": "微信授权失败: invalid code",
  "data": null
}
```

### 客户端示例代码

**使用 Retrofit (Android/Kotlin)**:
```kotlin
interface AuthApi {
    @POST("/api/app/auth/login/wechat")
    suspend fun wechatLogin(@Body request: WechatLoginRequest): Response<ApiResponse<LoginData>>
}

data class WechatLoginRequest(val code: String)

// 调用
try {
    val response = authApi.wechatLogin(WechatLoginRequest(code))
    if (response.isSuccessful && response.body()?.code == 0) {
        val token = response.body()?.data?.token
        val user = response.body()?.data?.user
        // 保存 token,更新UI
    }
} catch (e: Exception) {
    // 处理错误
}
```

**使用 Alamofire (iOS/Swift)**:
```swift
struct WechatLoginRequest: Encodable {
    let code: String
}

AF.request("https://yourapi.com/api/app/auth/login/wechat",
           method: .post,
           parameters: WechatLoginRequest(code: code),
           encoder: JSONParameterEncoder.default)
    .responseDecodable(of: ApiResponse<LoginData>.self) { response in
        switch response.result {
        case .success(let result):
            if result.code == 0, let token = result.data?.token {
                // 保存 token
            }
        case .failure(let error):
            print("Error: \(error)")
        }
    }
```

## 四、后端实现流程

1. **接收客户端发送的 code**
2. **使用 code 换取 access_token**
   - 调用微信API: `https://api.weixin.qq.com/sns/oauth2/access_token`
   - 参数: appid, secret, code, grant_type
3. **使用 access_token 获取用户信息**
   - 调用微信API: `https://api.weixin.qq.com/sns/userinfo`
   - 参数: access_token, openid
4. **查找或创建用户**
   - 根据 openid 查询 user_auth 表
   - 如果不存在则创建新用户
5. **生成 JWT token 并返回**

## 五、数据库设计

### user_auth 表
存储第三方登录信息:

| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | BIGINT | 关联 user 表 |
| auth_type | VARCHAR | 登录类型: wechat/apple/google |
| open_id | VARCHAR | 第三方平台用户ID(微信的openid) |
| union_id | VARCHAR | 微信UnionID(可选) |
| extra_info | TEXT | 额外信息(JSON) |

### user 表
用户基本信息:

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| uid | VARCHAR | 用户唯一标识 |
| nickname | VARCHAR | 昵称 |
| avatar | VARCHAR | 头像URL |
| phone | VARCHAR | 手机号(可选) |

## 六、常见问题

### 1. code 只能使用一次
微信的 code 只能使用一次,使用后立即失效。客户端不要重复使用同一个 code。

### 2. code 有效期为 5 分钟
客户端获取 code 后应尽快发送到后端,超过 5 分钟会失效。

### 3. 配置未设置错误
```
微信登录配置未设置,请联系管理员
```
检查 application.properties 中的配置是否正确,确保不是默认的 `YOUR_WECHAT_APP_ID`。

### 4. scope 参数
- `snsapi_base`: 静默授权,只能获取 openid,不弹窗
- `snsapi_userinfo`: 需要用户授权,可获取用户基本信息(昵称、头像等)

移动应用建议使用 `snsapi_userinfo`。

### 5. UnionID 机制
- 同一用户在同一开放平台下的不同应用,UnionID 相同
- 如果只有一个应用,可能不返回 UnionID
- 建议使用 OpenID 作为主键,UnionID 作为关联

## 七、安全建议

1. **不要在客户端存储 AppSecret**
2. **code 换取 access_token 必须在服务端进行**
3. **使用 HTTPS 传输数据**
4. **JWT token 设置合理的过期时间**
5. **定期刷新 refresh_token**

## 八、测试步骤

1. 修改 `application.properties`,填入真实的 AppID 和 AppSecret
2. 启动服务器
3. 在手机上安装测试应用
4. 确保手机已安装微信
5. 点击微信登录按钮
6. 授权后观察返回结果

## 九、相关链接

- [微信开放平台](https://open.weixin.qq.com/)
- [微信登录 Android 文档](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/Android.html)
- [微信登录 iOS 文档](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/Access_Guide/iOS.html)
- [微信登录API文档](https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Development_Guide.html)
