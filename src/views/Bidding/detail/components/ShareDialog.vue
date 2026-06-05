<template>
  <el-dialog
    v-model="visible"
    title="分享标讯"
    width="420px"
    :close-on-click-modal="false"
    align-center
    top="25vh"
  >
    <div class="share-body">
      <!-- Tender title -->
      <div class="share-tender-title">{{ tenderTitle }}</div>

      <!-- QR code -->
      <div class="share-qr-section">
        <canvas ref="qrCanvasRef" />
      </div>

      <!-- URL copy -->
      <div class="share-url-section">
        <el-input v-model="shareUrl" readonly>
          <template #append>
            <el-button @click="handleCopy">
              <template v-if="copied">已复制</template>
              <template v-else>复制链接</template>
            </el-button>
          </template>
        </el-input>
      </div>

      <div v-if="copied" class="share-copied-hint">
        <el-icon><CircleCheckFilled /></el-icon> 链接已复制到剪贴板
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { CircleCheckFilled } from '@element-plus/icons-vue'
import QRCode from 'qrcode'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  tenderTitle: { type: String, default: '' },
  url: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const qrCanvasRef = ref(null)
const copied = ref(false)
const shareUrl = computed(() => {
  return props.url || (typeof window !== 'undefined' ? window.location.href : '')
})

// Generate QR code when dialog opens
watch(visible, async (open) => {
  if (!open) return
  copied.value = false
  await nextTick()
  if (qrCanvasRef.value) {
    try {
      await QRCode.toCanvas(qrCanvasRef.value, shareUrl.value, {
        width: 180,
        margin: 2,
        color: {
          dark: '#1a1a2e',
          light: '#ffffff',
        },
      })
    } catch (err) {
      console.error('QR code generation failed:', err)
    }
  }
})

async function handleCopy() {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
  } catch {
    // Fallback for older browsers
    const textarea = document.createElement('textarea')
    textarea.value = shareUrl.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
  }
  copied.value = true
  setTimeout(() => { copied.value = false }, 2500)
}
</script>

<style scoped>
.share-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 8px 0;
}

.share-tender-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary, #303133);
  text-align: center;
  line-height: 1.5;
  word-break: break-all;
  max-width: 100%;
}

.share-qr-section {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 12px;
  background: #ffffff;
  border: 1px solid var(--gray-250, #e4e7ed);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.share-qr-section canvas {
  display: block;
}

.share-url-section {
  width: 100%;
}

.share-copied-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--el-color-success, #67c23a);
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
