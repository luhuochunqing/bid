<template>
  <el-tooltip :content="isFavorited ? '取消收藏' : '收藏标讯'" placement="top">
    <span
      class="favorite-text"
      :class="{ 'is-favorited': isFavorited }"
      :loading="loading"
      @click.stop="handleToggle"
    >
      {{ isFavorited ? '★ 已收藏' : '☆ 收藏' }}
    </span>
  </el-tooltip>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tenderFavoritesApi } from '@/api'

const props = defineProps({
  tenderId: { type: [Number, String], required: true },
  /** 初始收藏状态（可选，外部可提供以节省一次请求） */
  initialFavorited: { type: Boolean, default: undefined }
})

const emit = defineEmits(['toggle'])

const isFavorited = ref(false)
const loading = ref(false)

watch(() => props.initialFavorited, (val) => {
  if (val !== undefined) {
    isFavorited.value = val
  }
}, { immediate: true })

async function handleToggle() {
  loading.value = true
  try {
    const res = await tenderFavoritesApi.toggleFavorite(props.tenderId)
    const favorited = res?.data?.favorited ?? !isFavorited.value
    isFavorited.value = favorited
    ElMessage.success(favorited ? '已收藏' : '已取消收藏')
    emit('toggle', favorited)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '操作失败，请重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.favorite-text {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  user-select: none;
  transition: color 0.2s;
}
.favorite-text:hover {
  color: var(--el-color-warning);
}
.favorite-text.is-favorited {
  color: var(--el-color-warning);
}
</style>
