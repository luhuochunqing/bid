<template>
  <el-card class="register-panel" shadow="never">
    <template #header>
      <div class="header">
        <div>
          <div class="title">人工登记结果</div>
          <div class="subtitle">统一录入中标/未中标、合同期限、备注和竞对信息</div>
        </div>
        <el-button link @click="$emit('reset')">重置</el-button>
      </div>
    </template>

    <BidResultFormFields
      :form="form"
      :projects="projects"
      show-project-selector
      @add-competitor="$emit('add-competitor')"
      @remove-competitor="$emit('remove-competitor', $event)"
    />

    <div class="actions">
      <el-button @click="$emit('reset')">清空</el-button>
      <el-button type="primary" :loading="saving" @click="$emit('submit')">
        {{ form.id ? '保存修改' : '登记结果' }}
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import BidResultFormFields from './BidResultFormFields.vue'

defineProps({
  form: {
    type: Object,
    required: true
  },
  projects: {
    type: Array,
    default: () => []
  },
  saving: {
    type: Boolean,
    default: false
  }
})

defineEmits(['add-competitor', 'remove-competitor', 'reset', 'submit'])
</script>

<style scoped>
.header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin-top: 4px;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 16px;
}
</style>
