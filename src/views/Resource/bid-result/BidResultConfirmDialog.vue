<template>
  <el-dialog :model-value="visible" title="确认外部结果" width="820px" @close="$emit('close')">
    <div v-if="fetchRecord" class="summary">
      <el-tag size="small">{{ fetchRecord.source || '公开来源' }}</el-tag>
      <span>{{ fetchRecord.projectName }}</span>
      <span>{{ fetchRecord.result === 'won' ? '中标' : '未中标' }}</span>
    </div>

    <BidResultFormFields
      v-model:form="form"
      :projects="projects"
      :show-project-selector="!form.projectId"
      @add-competitor="$emit('add-competitor')"
      @remove-competitor="$emit('remove-competitor', $event)"
    />

    <template #footer>
      <el-button @click="$emit('close')">取消</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('submit')">确认并保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import BidResultFormFields from './BidResultFormFields.vue'

const form = defineModel('form', { type: Object, required: true })
defineProps({
  visible: Boolean,
  saving: Boolean,
  fetchRecord: {
    type: Object,
    default: null
  },
  projects: {
    type: Array,
    default: () => []
  }
})

defineEmits(['add-competitor', 'remove-competitor', 'close', 'submit'])
</script>

<style scoped>
.summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  color: var(--text-secondary-ui);
  font-size: 13px;
}
</style>
