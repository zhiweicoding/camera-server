# 前端接口变更文档 v2.1

> 更新时间：2025-12-28
> 主要变更：新增装机商模块、设备转让模块，调整经销商和账单统计维度，废弃业务员相关接口

---

## 一、装机商模块（新增）

### 1. 装机商管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/installer/list` | 分页查询装机商列表 |
| GET | `/api/admin/installer/{id}` | 获取装机商详情 |
| POST | `/api/admin/installer` | 新增装机商 |
| PUT | `/api/admin/installer/{id}` | 更新装机商 |
| PUT | `/api/admin/installer/{id}/status` | 启用/禁用装机商 |
| GET | `/api/admin/installer/all` | 获取所有启用的装机商（下拉框用） |

### 2. 列表查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 搜索关键词（代码/名称/联系人） |
| status | String | 否 | 状态筛选：ENABLED / DISABLED |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 3. 装机商字段

```json
{
  "id": 1,
  "installerCode": "A001",
  "installerName": "装机商名称",
  "contactPerson": "联系人",
  "contactPhone": "13800000000",
  "address": "地址",
  "commissionRate": 30.00,
  "status": "ENABLED",
  "createdAt": "2025-12-28T00:00:00",
  "updatedAt": "2025-12-28T00:00:00"
}
```

### 4. 新增/更新请求体

```json
{
  "installerCode": "A001",
  "installerName": "装机商名称",
  "contactPerson": "联系人",
  "contactPhone": "13800000000",
  "address": "地址",
  "commissionRate": 30.00
}
```

---

## 二、经销商模块（调整）

### 1. 接口变更

| 变更类型 | 路径 | 说明 |
|----------|------|------|
| 调整 | GET `/api/admin/vendor/list` | 新增 `installerId` 筛选参数 |
| **废弃** | POST `/api/admin/vendor` | 不再支持，改为在用户管理中创建经销商用户时自动创建 |

### 2. 列表查询新增参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| installerId | Long | 否 | 按所属装机商筛选 |

### 3. 经销商新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| installerId | Long | 所属装机商ID |
| commissionRate | BigDecimal | 分润比例（基于剩余可分润金额的百分比） |
| parentVendorId | Long | 上级经销商ID（多级分销用） |
| level | Integer | 层级（1=一级，2=二级...） |

---

## 三、设备转让模块（新增）

### 1. 设备转让 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/device-transfer` | 创建设备转让 |
| GET | `/api/admin/device-transfer/list` | 分页查询转让记录 |
| GET | `/api/admin/device-transfer/{id}` | 获取转让详情 |
| PUT | `/api/admin/device-transfer/{id}/confirm` | 确认转让 |
| PUT | `/api/admin/device-transfer/{id}/cancel` | 取消转让 |
| GET | `/api/admin/device-transfer/device/{deviceId}/chain` | 查询设备所有权链 |

### 2. 列表查询参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fromVendorId | Long | 否 | 转出方经销商ID |
| toVendorId | Long | 否 | 转入方经销商ID |
| status | String | 否 | 状态：PENDING / CONFIRMED / CANCELLED |
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 3. 创建转让请求体

```json
{
  "fromVendorId": 1,
  "toVendorId": 2,
  "deviceIds": ["device001", "device002"],
  "commissionRate": 20.00,
  "remark": "备注说明"
}
```

### 4. 转让记录字段

```json
{
  "id": 1,
  "transferNo": "TF202512280001",
  "fromVendorId": 1,
  "fromVendorName": "经销商A",
  "toVendorId": 2,
  "toVendorName": "经销商B",
  "deviceCount": 2,
  "commissionRate": 20.00,
  "status": "PENDING",
  "remark": "备注",
  "createdAt": "2025-12-28T00:00:00",
  "confirmedAt": null
}
```

### 5. 设备所有权链返回

```json
{
  "deviceId": "device001",
  "chain": [
    {
      "vendorId": 1,
      "vendorName": "经销商A",
      "commissionRate": 30.00,
      "level": 1,
      "acquiredAt": "2025-12-01T00:00:00"
    },
    {
      "vendorId": 2,
      "vendorName": "经销商B",
      "commissionRate": 20.00,
      "level": 2,
      "acquiredAt": "2025-12-15T00:00:00"
    }
  ]
}
```

---

## 四、账单统计模块（调整）

### 1. 接口变更

| 变更类型 | 路径 | 说明 |
|----------|------|------|
| **新增** | GET `/api/admin/billing/installer-summary` | 按装机商维度统计账单 |
| **废弃** | GET `/api/admin/billing/salesman-summary` | 返回空数据，请改用上面的接口 |

### 2. 装机商账单汇总参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| installerId | Long | 否 | 装机商ID |
| installerCode | String | 否 | 装机商代码 |
| startDate | Date | 否 | 开始日期 yyyy-MM-dd |
| endDate | Date | 否 | 结束日期 yyyy-MM-dd |
| month | String | 否 | 按月查询 yyyy-MM（优先级高于日期范围） |

### 3. 返回结构

```json
{
  "list": [
    {
      "installerId": 1,
      "installerCode": "A001",
      "installerName": "装机商名称",
      "orderCount": 10,
      "totalAmount": 10000.00,
      "installerAmount": 3000.00
    }
  ],
  "totalOrders": 10,
  "totalAmount": 10000.00,
  "totalInstallerAmount": 3000.00
}
```

---

## 五、支付订单字段（新增）

订单详情/列表中新增以下快照字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| installerId | Long | 装机商ID |
| installerCode | String | 装机商代码 |
| installerRate | BigDecimal | 装机商分润比例（快照） |
| installerAmount | BigDecimal | 装机商分润金额 |
| dealerId | Long | 经销商ID |
| dealerCode | String | 经销商代码 |
| dealerRate | BigDecimal | 经销商分润比例（快照） |
| dealerAmount | BigDecimal | 经销商分润金额 |
| feeAmount | BigDecimal | 手续费 |
| planCost | BigDecimal | 套餐成本 |
| profitAmount | BigDecimal | 可分润金额 |

### 双重身份分润说明

如果某用户同时是装机商和经销商（`isInstaller=1` 且 `isDealer=1`），且该用户的经销商身份在该设备的分润链中：
- **装机商分润**：记录在 `installerAmount`
- **经销商分润**：记录在 `dealerAmount`
- **该用户实际所得**：`installerAmount + dealerAmount`（两份都拿）

结算时后台会自动合并同一用户的双重身份分润。

---

## 六、业务员相关（废弃）

以下接口/字段建议前端逐步移除：

### 废弃接口
- `GET /api/admin/billing/salesman-summary` — 已废弃，返回空数据

### 废弃字段
订单中的以下字段保留兼容但不再写入新数据：
- `salesmanId`
- `salesmanName`
- `salesmanAmount`

---

## 七、用户管理模块（调整）

### 1. 接口变更

| 变更类型 | 路径 | 说明 |
|----------|------|------|
| 调整 | GET `/api/admin/users` | 新增 `isInstaller`、`isDealer` 筛选参数 |
| 调整 | POST `/api/admin/users` | 支持双重身份字段 |
| **新增** | PUT `/api/admin/users/{id}/identity` | 更新用户身份标识 |

### 2. 用户新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| isInstaller | Integer | 是否为装机商身份：0-否 1-是 |
| isDealer | Integer | 是否为经销商身份：0-否 1-是 |
| installerId | Long | 关联装机商ID |
| dealerId | Long | 关联经销商ID |

### 3. 双重身份说明

- 同一用户可以同时勾选装机商和经销商身份
- `isInstaller=1` 且 `isDealer=1` 表示双重身份
- 双重身份用户在分润时两份都拿

### 4. 身份更新请求体

```json
{
  "isInstaller": 1,
  "isDealer": 1,
  "installerId": 1,
  "dealerId": 2
}
```

---

## 八、分润计算逻辑说明

### 可分润金额计算
```
可分润金额 = 套餐价格 - 手续费 - 套餐成本
```

### 分润分配顺序
1. **公司利润**：可分润金额 × 公司利润比例（如 40%）
2. **装机商分润**：(可分润金额 - 公司利润) × 装机商分润比例
3. **一级经销商分润**：剩余金额 × 一级经销商分润比例
4. **二级经销商分润**：剩余金额 × 二级经销商分润比例
5. **...以此类推**

### 示例
- 套餐价格：1000 元
- 手续费：6 元
- 套餐成本：100 元
- 可分润金额：894 元
- 公司利润（40%）：357.6 元
- 剩余可分润：536.4 元
- 装机商（30%）：160.92 元
- 一级经销商（50%）：187.74 元
- 二级经销商（50%）：93.87 元
- 剩余归公司：93.87 元
