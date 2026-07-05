<template>
  <span>{{ formatted }}</span>
</template>

<script setup>
// Input: backend datetime string / Date / null
// Output: span 渲染 "yyyy-MM-dd HH:mm:ss" 或 "yyyy-MM-dd"
// Pos: src/components/common/ - CO-472 公共日期时间显示组件
import { computed } from 'vue'
import { formatDisplayDateTime } from '@/utils/formatDisplayDate'

const props = defineProps({
  value: { type: [String, Number, Date], default: null },
  fallback: { type: String, default: '-' }
})

const formatted = computed(() => formatDisplayDateTime(props.value, props.fallback))
</script>
