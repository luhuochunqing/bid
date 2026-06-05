<template>
  <el-button
    :type="subscribed ? 'success' : 'primary'"
    :loading="loading"
    :plain="!subscribed"
    size="small"
    :aria-label="subscribed ? '取消关注' : '关注此' + entityLabel"
    @click="handleToggle"
  >
    <el-icon><Star /></el-icon>
    {{ subscribed ? '已关注' : '关注' }}
  </el-button>
</template>

<script setup>
// Input: entityType + entityId props, uses useSubscription composable
// Output: styled Element Plus button emitting toggle events
// Pos: src/components/common/ - Subscribe toggle button
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { Star } from '@element-plus/icons-vue'
import { useSubscription } from '@/composables/useSubscription'

const props = defineProps({
  entityType: { type: String, required: true },
  entityId: { type: [Number, String], required: true },
  entityLabel: { type: String, default: '对象' }
})

const emit = defineEmits(['toggle'])

const { subscribed, loading, toggle } = useSubscription(
  props.entityType,
  Number(props.entityId)
)

const handleToggle = async () => {
  await toggle()
  emit('toggle', subscribed.value)
}
</script>
