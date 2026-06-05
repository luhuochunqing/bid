<template>
  <el-icon :size="normalizedSize" :style="iconStyle">
    <component :is="iconComponent" />
  </el-icon>
</template>

<script setup>
import { computed } from 'vue'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { iconMap } from './icons'

const props = defineProps({
  name: {
    type: String,
    required: true,
    validator: (value) => {
      // 支持直接使用 Element Plus 图标名称或业务图标名称
      return Object.keys(ElementPlusIconsVue).includes(value) ||
             Object.keys(iconMap).includes(value) ||
             Object.values(iconMap).includes(value)
    }
  },
  size: {
    type: [String, Number],
    default: 'default'
  },
  color: {
    type: String,
    default: ''
  }
})

// 尺寸映射
const sizeMap = {
  xs: 14,
  sm: 16,
  default: 18,
  md: 20,
  lg: 24
}

// 标准化尺寸
const normalizedSize = computed(() => {
  if (typeof props.size === 'number') {
    return props.size
  }
  return sizeMap[props.size] || sizeMap.default
})

// 图标样式
const iconStyle = computed(() => {
  return props.color ? { color: props.color } : {}
})

// 获取图标组件
const iconComponent = computed(() => {
  const iconName = iconMap[props.name] || props.name
  return ElementPlusIconsVue[iconName]
})
</script>

<style scoped>
.el-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  vertical-align: middle;
}
</style>
