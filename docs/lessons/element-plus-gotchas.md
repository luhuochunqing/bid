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
