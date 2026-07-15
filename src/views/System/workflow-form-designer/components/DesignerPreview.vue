<template>
  <div class="inline-preview">
    <div class="preview-header">
      <h2>实时预览</h2>
      <div class="preview-actions">
        <el-select v-model="role" size="small" placeholder="模拟角色" style="width: 120px">
          <el-option label="管理员" value="admin" />
          <el-option label="经理" value="manager" />
          <el-option label="投标专员" value="bid-Team" />
        </el-select>
        <el-button size="small" @click="$emit('open-full-preview')">全屏预览</el-button>
        <el-button size="small" :loading="trialLoading" @click="$emit('trial-submit')">预览提交数据</el-button>
      </div>
    </div>
    <div class="preview-body">
      <DynamicWorkflowForm :schema="schema" v-model="model" />
      <!-- tender.entry 特有字段预览（不在 schema 中，由业务页硬编码） -->
      <div v-if="isTenderEntry" class="extension-preview">
        <div class="extension-divider">以下为 tender.entry 业务页特有字段（不在 schema 中）</div>
        <div class="extension-field">
          <label class="extension-label">粘贴识别</label>
          <div class="extension-hint">[粘贴识别]或文字输入，系统将智能拆分回填标讯信息</div>
          <div class="extension-placeholder">多行文本输入框 + "识别粘贴文字"按钮（触发 AI 解析回填）</div>
        </div>
        <div class="extension-field">
          <label class="extension-label">标讯文件</label>
          <div class="extension-hint">支持 PDF/Word 文件上传（≤50MB），上传即保存，自动 AI 解析回填表单字段</div>
          <div class="extension-placeholder">文件上传区域（拖拽或点击选择附件，触发 AI 解析回填）</div>
        </div>
      </div>
    </div>
    <div v-if="trialPayload" class="preview-payload">
      <div class="payload-label">预览提交数据（本地生成，未发送到后端）：</div>
      <pre>{{ trialPayload }}</pre>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'

const props = defineProps({
  schema: { type: Object, required: true },
  trialPayload: { type: String, default: '' },
  trialLoading: { type: Boolean, default: false },
})

const model = defineModel({ type: Object, default: () => ({}) })
const role = defineModel('role', { type: String, default: 'admin' })

defineEmits(['open-full-preview', 'trial-submit'])

// tender.entry 特有字段预览：仅在当前 scope 为 tender.entry 时显示
// 这些字段是业务页硬编码的 AI 解析交互，不在动态 schema 中
const isTenderEntry = computed(() => {
  // 通过 schema 的 fields 中是否包含 tender.entry 特有字段（如 title + purchaser + deadline 组合）来判断
  // 或更简单：通过 schema 的 fields 里有 purchaser 字段且无 pastedText 字段来推断
  const fields = props.schema?.fields || []
  if (fields.length === 0) return false
  const hasPurchaser = fields.some(f => f.key === 'purchaser')
  const hasDeadline = fields.some(f => f.key === 'deadline')
  return hasPurchaser && hasDeadline
})
</script>

<style scoped>
.inline-preview { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.preview-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-lighter); }
.preview-header h2 { margin: 0; font-size: 14px; font-weight: 600; }
.preview-actions { display: flex; gap: 8px; align-items: center; }
.preview-body { padding: 16px; max-height: 500px; overflow-y: auto; }
.preview-payload { padding: 12px 16px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-light); }
.payload-label { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.preview-payload pre { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow-y: auto; }
.extension-preview { margin-top: 16px; border-top: 1px dashed var(--el-border-color); padding-top: 12px; }
.extension-divider { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 12px; font-style: italic; }
.extension-field { margin-bottom: 12px; }
.extension-label { display: block; font-size: 13px; font-weight: 500; color: var(--el-text-color-primary); margin-bottom: 4px; }
.extension-hint { font-size: 12px; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.extension-placeholder { padding: 8px 12px; background: var(--el-fill-color-light); border: 1px dashed var(--el-border-color-lighter); border-radius: 4px; font-size: 12px; color: var(--el-text-color-placeholder); }
</style>
