<template>
  <el-dialog
    v-model="visible"
    :title="`预览: ${fileName}`"
    width="80%"
    top="5vh"
    @close="$emit('close')"
  >
    <div class="file-preview-container">
      <iframe
        v-if="safeFileUrl"
        :src="safeFileUrl"
        class="file-preview-frame"
        frameborder="0"
        sandbox="allow-same-origin"
        referrerpolicy="no-referrer"
      />
      <div v-else class="preview-placeholder">
        <el-icon :size="60"><Document /></el-icon>
        <p>无法预览此文件</p>
      </div>
    </div>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        @click="$emit('download', { name: fileName, url: fileUrl })"
      >
        下载文件
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  fileName: { type: String, default: '' },
  fileUrl: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'close', 'download'])

const safeFileUrl = computed(() => {
  const url = String(props.fileUrl || '').trim()
  if (!url) return ''
  if (url.startsWith('/api/')) return url
  try {
    const parsed = new URL(url, window.location.origin)
    if (parsed.origin === window.location.origin) return url
  } catch (_) {
    return ''
  }
  return ''
})

const visible = ref(props.modelValue)

watch(() => props.modelValue, (val) => {
  visible.value = val
})

watch(visible, (val) => {
  if (val !== props.modelValue) emit('update:modelValue', val)
})
</script>

<style scoped>
.file-preview-container {
  width: 100%;
  height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-preview-frame {
  width: 100%;
  height: 100%;
  border: none;
}

.preview-placeholder {
  text-align: center;
  color: var(--text-muted);
}

.preview-placeholder .el-icon {
  margin-bottom: 16px;
  color: #c0c4cc;
}

.preview-placeholder p { font-size: 14px; margin: 0; }
</style>
