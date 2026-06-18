# 标讯导入"总部所在地"字段值丢失 根因分析

> Issue: CO-259
> 日期: 2026-06-18
> 排查者: mimo

---

## 现场还原

**症状素描**：通过批量导入模板导入标讯后，编辑页面的"总部所在地"字段显示为空。

**边界划定**：
- 人工录入的标讯：编辑页面正常显示 ✅
- 批量导入的标讯：编辑页面值丢失 ❌
- 列表页面：两种来源都能正常显示 ✅

**思维沙箱**：怀疑是导入和人工录入存储的 region 格式不一致。

---

## 剥洋葱：逆向调用链

### Layer 1 — 入口/参数层

导入模板后端校验只允许省级值：

```java
// backend/src/main/java/com/xiyu/bid/tender/service/TenderImportService.java:52
static final List<String> REGIONS = List.of(
    "北京", "天津", "河北", ...
);
```

人工录入前端使用 el-cascader 级联选择器，存储的是"省+市+区"拼接字符串：

```javascript
// ManualTenderDialog.vue:226
form.value.region = val.join('')  // ['北京市', '东城区'] → "北京市东城区"
```

### Layer 2 — 核心逻辑层

编辑页面的 regionCascaderValue computed get 逻辑：

```javascript
// 修改前
get: () => {
    const v = form.value.region
    for (const province of chinaRegionOptions) {
        if (province.name === v) return [v]  // 期望 "北京市"，实际是 "北京"
    }
    return v  // 返回字符串，cascader 期望数组 → 值丢失
}
```

### Layer 3 — 数据层

后端数据库 region 字段存储格式：
- 导入的记录：`"北京"`（省级）
- 人工录入的记录：`"北京市东城区"`（省+市+区）

前端 chinaRegionData.js 省份名格式：`"北京市"`（带"市"后缀）

---

## 零号病人定位

**第一行错误：**

```
ManualTenderDialog.vue:210 (修改前)
if (province.name === v) return [v]
```

**必然性解释：**
- `province.name` 是 `"北京市"`
- 导入的 `v` 是 `"北京"`
- `"北京市" === "北京"` 为 false
- 匹配失败，返回原字符串 `v`（类型 string）
- el-cascader 期望数组，拿到 string → 显示为空

**状态变迁图：**
```
用户导入 Excel (region="北京")
  → 后端校验通过（REGIONS 列表包含"北京"）
  → 数据库存储 region="北京"
  → 编辑页面加载
  → regionCascaderValue.get() 尝试匹配
  → "北京市" === "北京" 失败
  → 返回 "北京" (string)
  → el-cascader 期望 array
  → 显示为空
```

---

## 验证与修复

### 修复 diff

```diff
// ManualTenderDialog.vue & TenderBasicInfoTab.vue
- if (province.name === v) return [v]
+ if (province.name === v || province.name === v + '市' || province.name === v + '省' || province.name === v + '自治区') return [province.name]
```

同时补充了省+市+区三级匹配逻辑：

```diff
+ if (city.children) {
+   for (const district of city.children) {
+     if (v === province.name + city.name + district.name) return [province.name, city.name, district.name]
+   }
+ }
```

**最小验证：**
1. 导入一条 region="北京" 的标讯
2. 打开编辑页面，检查"总部所在地"是否显示为"北京市"
3. 导入一条 region="内蒙古" 的标讯，验证显示为"内蒙古自治区"

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `ManualTenderDialog.vue:210` 精确匹配逻辑 | ✅ |
| 必然性已证明 | 省级值与带后缀省份名不匹配 → 返回 string → cascader 值丢失 | ✅ |
| 最小验证已设计 | 导入省级值后编辑页面回显验证 | ✅ |
| 修复 diff 已提供 | 见上 | ✅ |
| 防复发测试已设计 | 见下 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. 导入模板填写"北京"→ 编辑页面显示"北京市"
2. 导入模板填写"内蒙古"→ 编辑页面显示"内蒙古自治区"
3. 人工录入选择"北京市/东城区"→ 编辑页面正确回显级联选择
