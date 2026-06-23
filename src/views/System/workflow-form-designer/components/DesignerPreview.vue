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
        <el-button size="small" :loading="trialLoading" @click="$emit('trial-submit')">试提交</el-button>
      </div>
    </div>
    <div class="preview-body">
      <DynamicWorkflowForm :schema="schema" v-model="model" />
    </div>
    <div v-if="trialPayload" class="preview-payload">
      <div class="payload-label">提交数据：</div>
      <pre>{{ trialPayload }}</pre>
    </div>
  </div>
</template>

<script setup>
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'

defineProps({
  schema: { type: Object, required: true },
  trialPayload: { type: String, default: '' },
  trialLoading: { type: Boolean, default: false },
})

const model = defineModel({ type: Object, default: () => ({}) })
const role = defineModel('role', { type: String, default: 'admin' })

defineEmits(['open-full-preview', 'trial-submit'])
</script>

<style scoped>
.inline-preview { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.preview-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; background: var(--el-fill-color-light); border-bottom: 1px solid var(--el-border-color-lighter); }
.preview-header h2 { margin: 0; font-size: 14px; font-weight: 600; }
.preview-actions { display: flex; gap: 8px; align-items: center; }
.preview-body { padding: 16px; max-height: 400px; overflow-y: auto; }
.preview-payload { padding: 12px 16px; border-top: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-light); }
.payload-label { font-size: 12px; font-weight: 600; color: var(--el-text-color-secondary); margin-bottom: 4px; }
.preview-payload pre { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; max-height: 200px; overflow-y: auto; }
</style>
