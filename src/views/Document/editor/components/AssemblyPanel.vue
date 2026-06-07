<template>
  <div class="right-panel">
    <el-card shadow="never" class="assembly-card">
      <template #header>
        <div class="card-header with-ai">
          <el-icon class="ai-icon"><MagicStick /></el-icon>
          <span>智能装配</span>
        </div>
      </template>

      <div class="assembly-content">
        <div class="form-section">
          <h4 class="section-label">选择模板</h4>
          <el-radio-group v-model="assemblyForm.templateId" class="template-options">
            <el-radio
              v-for="template in assemblyTemplates"
              :key="template.id"
              :value="template.id"
              border
            >
              <div class="template-option">
                <div class="template-name">{{ template.name }}</div>
                <div class="template-meta">{{ template.category || 'OTHER' }}</div>
              </div>
            </el-radio>
          </el-radio-group>
          <el-empty v-if="assemblyTemplates.length === 0" description="暂无可用模板" />
        </div>

        <div class="form-section">
          <h4 class="section-label">包含章节</h4>
          <el-checkbox-group v-model="assemblyForm.sections" class="section-checkboxes">
            <el-checkbox value="technical">技术方案</el-checkbox>
            <el-checkbox value="cases">案例展示</el-checkbox>
            <el-checkbox value="qualification">资质文件</el-checkbox>
            <el-checkbox value="service">服务承诺</el-checkbox>
            <el-checkbox value="delivery">交付计划</el-checkbox>
          </el-checkbox-group>
        </div>

        <el-button
          type="primary"
          size="large"
          :loading="isAssembling"
          :disabled="assemblyForm.sections.length === 0"
          class="assembly-btn"
          @click="$emit('start-assembly')"
        >
          <el-icon v-if="!isAssembling"><MagicStick /></el-icon>
          {{ isAssembling ? '装配中...' : '开始装配' }}
        </el-button>

        <HistoryLists
          :assembly-history="assemblyHistory"
          :export-history="exportHistory"
          :archive-history="archiveHistory"
        />
      </div>
    </el-card>

    <AssemblyProgressDialog
      v-model="showAssemblyProgress"
      :steps="assemblySteps"
      :current-step-index="currentStepIndex"
    />
  </div>
</template>

<script setup>
import { MagicStick } from '@element-plus/icons-vue'
import AssemblyProgressDialog from './AssemblyProgressDialog.vue'
import HistoryLists from './HistoryLists.vue'

defineModel('assemblyForm', { type: Object, required: true })
const showAssemblyProgress = defineModel('showAssemblyProgress', { type: Boolean, default: false })
defineProps({
  assemblyTemplates: { type: Array, default: () => [] },
  assemblyHistory: { type: Array, default: () => [] },
  assemblySteps: { type: Array, default: () => [] },
  currentStepIndex: { type: Number, default: 0 },
  isAssembling: { type: Boolean, default: false },
  exportHistory: { type: Array, default: () => [] },
  archiveHistory: { type: Array, default: () => [] }
})

defineEmits(['start-assembly'])
</script>

<style scoped>
.right-panel {
  width: 320px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.assembly-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.assembly-card :deep(.el-card__body) {
  flex: 1;
  overflow-y: auto;
}

.card-header.with-ai {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.ai-icon {
  color: #409eff;
  font-size: 18px;
}

.assembly-content {
  padding: 8px 0;
}

.form-section {
  margin-bottom: 24px;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--gray-750);
  margin: 0 0 12px 0;
}

.template-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.template-options :deep(.el-radio),
.template-options :deep(.el-radio.is-bordered) {
  width: 100%;
  margin-right: 0;
  margin-bottom: 0;
}

.template-option {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
}

.template-name { font-size: 14px; font-weight: 600; line-height: 1.3; }
.template-meta { font-size: 12px; color: var(--text-muted); }

.section-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-checkboxes :deep(.el-checkbox) {
  margin-right: 0;
  margin-bottom: 0;
}

.assembly-btn {
  width: 100%;
  margin-bottom: 24px;
  height: 42px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  background: linear-gradient(135deg, var(--accent-blue), var(--brand-xiyu-logo));
  border: none;
  box-shadow: 0 2px 8px rgba(3, 105, 161, 0.2);
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

.assembly-btn:hover {
  background: linear-gradient(135deg, var(--brand-xiyu-logo), var(--accent-blue));
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(3, 105, 161, 0.3);
}

.assembly-btn:active {
  transform: translateY(0);
}

:deep(.el-radio.is-bordered) {
  border-radius: 8px;
  border: 1.5px solid #e5e7eb;
  transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1);
}

:deep(.el-radio.is-bordered:hover) { border-color: var(--gray-400); }

:deep(.el-radio.is-bordered.is-checked) {
  border-color: var(--accent-blue);
  background: #f0f9ff;
}

:deep(.el-checkbox) { transition: all 200ms cubic-bezier(0.4, 0, 0.2, 1); }
:deep(.el-checkbox:hover) { color: var(--accent-blue); }

:deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background: linear-gradient(135deg, var(--accent-blue), var(--brand-xiyu-logo));
  border-color: var(--accent-blue);
}

@media (max-width: 1200px) {
  .right-panel { width: 100%; height: auto; max-height: 300px; }
}
</style>
