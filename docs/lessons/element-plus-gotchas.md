# Element Plus 陷阱与调试经验

记录开发过程中遇到的 Element Plus 组件陷阱、调试方法论和设计教训。

---

## 1. el-input 与 el-cascader/el-select/el-date-picker 宽度不一致

### 问题

表单中 `el-input` 的边框视觉上比其他输入组件短：

```vue
<el-row :gutter="16">
  <el-col :span="12">
    <el-form-item label="总部所在地">
      <el-cascader class="full-width" />  <!-- 占满 -->
    </el-form-item>
  </el-col>
  <el-col :span="12">
    <el-form-item label="招标主体">
      <el-input placeholder="请输入" />  <!-- 视觉上更短！ -->
    </el-form-item>
  </el-col>
</el-row>
```

### 根因

Element Plus 组件默认宽度行为不一致：

| 组件 | 默认宽度行为 |
|------|-------------|
| `el-cascader` | 需要显式设置宽度，通常配合 `class="full-width"` |
| `el-select` | 需要显式设置宽度，通常配合 `class="full-width"` |
| `el-date-picker` | 需要显式设置宽度，通常配合 `class="full-width"` |
| `el-input` | 默认由内部机制决定，**不自动占满父容器** |

当给前者添加 `class="full-width"`（`width: 100%`）而 `el-input` 没有时，出现宽度差异。

### 修复

统一给所有 `el-input` 添加 `class="full-width"`：

```vue
<!-- ❌ 错误：el-input 没有 full-width -->
<el-input v-model="form.purchaser" placeholder="请输入招标主体" />

<!-- ✅ 正确：统一添加 full-width -->
<el-input v-model="form.purchaser" placeholder="请输入招标主体" class="full-width" />
```

### 涉及文件

- `src/views/Bidding/list/components/ManualTenderDialog.vue`
- `src/views/Bidding/list/components/TenderBasicInfoTab.vue`

### 规范建议

在 Element Plus 表单中，**统一给所有输入组件添加 `class="full-width"`**，确保宽度一致。

---

## 2. el-cascader 级联选择器与后端字符串字段的转换陷阱

### 问题

```vue
<!-- ❌ 错误：直接假设后端值与 options 中的 name 完全匹配 -->
<script setup>
const cascaderValue = computed({
  get: () => {
    const v = form.value.region
    for (const province of options) {
      if (province.name === v) return [v]  // 后端是"北京"，options 是"北京市"
    }
    return v  // 返回 string，cascader 期望 array
  }
})
</script>

<!-- ✅ 正确：处理后端值可能缺少后缀的情况 -->
<script setup>
const cascaderValue = computed({
  get: () => {
    const v = form.value.region
    for (const province of options) {
      if (province.name === v || 
          province.name === v + '市' || 
          province.name === v + '省' ||
          province.name === v + '自治区') return [province.name]
    }
    return v
  }
})
</script>
```

el-cascader 的 `v-model` 期望数组格式 `['省', '市', '区']`，但后端通常存储拼接字符串 `"省市区"`。当后端值缺少后缀（如"北京"而非"北京市"）时，直接精确匹配会失败，返回原字符串导致组件显示为空。

### 正确写法

```vue
<script setup>
import { computed } from 'vue'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'

const props = defineProps({ form: Object })

const regionCascaderValue = computed({
  get: () => {
    const v = props.form.region
    if (!v) return null
    
    for (const province of chinaRegionOptions) {
      // 支持省级匹配（含后缀修正）
      if (province.name === v || 
          province.name === v + '市' || 
          province.name === v + '省' || 
          province.name === v + '自治区') return [province.name]
      
      if (province.children) {
        for (const city of province.children) {
          // 支持省+市匹配
          if (v === province.name + city.name) return [province.name, city.name]
          
          if (city.children) {
            for (const district of city.children) {
              // 支持省+市+区匹配
              if (v === province.name + city.name + district.name) {
                return [province.name, city.name, district.name]
              }
            }
          }
        }
      }
    }
    return v
  },
  set: (val) => {
    if (!val) {
      props.form.region = ''
      return
    }
    props.form.region = Array.isArray(val) ? val.join('') : val
  }
})
</script>

<template>
  <el-cascader
    v-model="regionCascaderValue"
    :options="chinaRegionOptions"
    :props="{ expandTrigger: 'hover', label: 'name', value: 'name', checkStrictly: false, emitPath: true }"
    clearable
    filterable
  />
</template>
```

### 调试方法

如果怀疑 cascader 值丢失，在 computed get 中加日志：

```javascript
get: () => {
    const v = form.value.region
    console.log('[Cascader Debug] region value:', v, 'type:', typeof v)
    // ...
}
```

或者用 Vue DevTools 检查：
1. 打开 Vue DevTools
2. 找到包含 cascader 的组件
3. 检查 computed 属性的返回值类型（应该是 Array，不是 String）

---

## 3. el-upload 自定义 #file slot 必须显式渲染 file.percentage，否则进度条不可见

### 问题

投标文件上传组件 `ProjectDocumentTable.vue` 使用 `el-upload` 并自定义 `#file` slot 渲染文件列表（含 OBS 直传场景），用户反馈上传进度条不可见：

```vue
<!-- ❌ 错误：#file slot 内未渲染 file.percentage -->
<template #file="{ file }">
  <div class="file-item">
    <span>{{ file.name }}</span>
    <el-button @click="handleRemove(file)">删除</el-button>
  </div>
</template>
```

文件能上传成功，但用户看不到任何进度反馈，体验上像"卡住了"。

### 根因

`el-upload` 组件**默认的进度条渲染**只在默认 slot 下生效。一旦自定义 `#file` slot，组件就完全接管文件项的渲染责任，Element Plus 不再自动渲染进度条。

此时必须**显式读取 `file.percentage`**（0-100 的数字）并自行渲染进度展示。

### 修复

```vue
<!-- ✅ 正确：#file slot 内显式渲染 file.percentage -->
<template #file="{ file }">
  <div class="file-item">
    <div class="file-info">
      <span>{{ file.name }}</span>
      <span class="progress-text">{{ Math.round(file.percentage || 0) }}%</span>
    </div>
    <el-progress
      :percentage="Math.round(file.percentage || 0)"
      :status="file.status === 'success' ? 'success' : file.status === 'fail' ? 'exception' : ''"
    />
    <el-button @click="handleRemove(file)">删除</el-button>
  </div>
</template>
```

### OBS 直传场景的进度同步

当使用 OBS 直传 + 415 回退逻辑（`useObsUploadFallback.js` 的 `callApiWithObsFallback`）时，需要注意：

1. **OBS 直传进度**：通过 `useObsUpload` 暴露的 `progressPercent`（0-100 的 computed 值）获取，**不要重复 `Math.round(val * 100)` 转换**——内部已经处理。
2. **回退到 multipart 时**：需在回退前 `progress.value = 0` 重置进度，避免进度条停留在中间值（如 50%）后又被新请求覆盖。
3. **composable 层不写 UI 提示**：`ElMessage.success` 等 UI 提示必须由组件层负责，composable 只暴露状态（ref/computed）。

### 涉及文件

- `src/components/project/ProjectDocumentTable.vue` — 投标文件上传组件（自定义 #file slot）
- `src/composables/useObsUpload.js` — OBS 直传 composable，集中导出 `OBS_DIRECT_PREFIX`、`isObsEnabled`、`progressPercent`
- `src/composables/useObsUploadFallback.js` — `callApiWithObsFallback` 公共回退函数

### 规范建议

1. **使用 `#file` slot 时必须显式渲染 `file.percentage`**：Element Plus 默认进度条不会在自定义 slot 下生效。
2. **进度展示推荐用 `el-progress` 组件**：配合 `:status` 显示成功/失败状态，比纯文本更直观。
3. **进度值统一为 0-100**：composable 暴露 `progressPercent`（computed），避免在模板中重复转换。
4. **回退场景必须重置进度**：OBS 直传失败回退 multipart 前，`progress.value = 0` 防止状态污染。
5. **UI 提示分层**：composable 层只暴露状态，UI 提示（ElMessage 等）由组件层负责。

### 相关 PR

- PR !2063 / !2065 — 投标文件上传 4 个问题修复（进度条不显示 / 列表不刷新 / 上传中允许提交 / 删除状态不同步）
- 详见 `docs/lessons/root-cause-analysis-bid-file-upload-issues.md`
