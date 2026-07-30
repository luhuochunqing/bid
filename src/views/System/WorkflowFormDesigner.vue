<template>
  <div class="workflow-designer-page">
    <header class="designer-header">
      <div>
        <p class="eyebrow">Workflow Forms</p>
        <h1>流程表单配置</h1>
      </div>
      <div class="header-actions">
        <el-button :loading="formEngineLoading.list" @click="loadFormDefinitions">刷新</el-button>
      </div>
    </header>

    <main class="designer-shell">
      <!-- 左侧列表 -->
      <aside class="template-list">
        <div v-if="formEngineLoading.list" class="list-loading"><el-icon class="is-loading" :size="16"><Loading /></el-icon><span>加载中...</span></div>
        <button v-for="def in formDefinitions" :key="def.id" class="template-row" :class="{ active: def.scope === formEngineDraft.scope }" type="button" @click="selectFormDefinition(def)">
          <strong>{{ def.scopeLabel }}</strong>
          <span>{{ def.scope }} · v{{ def.version || 1 }} · {{ def.enabled ? '已启用' : '已禁用' }}</span>
        </button>
        <div v-if="!formEngineLoading.list && formDefinitions.length === 0" class="list-empty">暂无独立表单</div>
      </aside>

      <!-- 主编辑区 -->
      <section class="designer-main">
        <el-alert
          v-if="isUnsupportedProjectScope"
          type="warning"
          :closable="false"
          show-icon
          title="该表单暂未开放自定义"
          description="此表单含保证金、客户矩阵、AI 评估、审批流、OBS 直传等复杂交互，自定义字段会导致业务流程异常。后续版本将支持混合渲染模式。"
          style="margin-bottom: 16px"
        />
        <div class="form-grid">
          <el-form label-width="96px" class="template-form">
            <el-form-item label="模板编码"><el-input v-model="draft.templateCode" placeholder="例如 tender.entry" disabled /></el-form-item>
            <el-form-item label="表单名称"><el-input v-model="draft.name" placeholder="例如 标讯手工录入" /></el-form-item>
            <el-form-item label="启用"><el-switch v-model="draft.enabled" /></el-form-item>
          </el-form>
        </div>

        <!-- 编辑区 + 实时预览 并排 -->
        <div class="editor-preview-layout">
          <div class="editor-col">
            <el-tabs v-model="activeTab" class="field-editor-tabs">
              <el-tab-pane label="字段配置" name="fields">
                <DesignerFieldList :fields="draft.schema.fields" :field-types="fieldTypes" :disable-add-field="isUnsupportedProjectScope" @add-field="addField" @delete-field="deleteField" @copy-field="copyField" @new-template="newTemplate" @normalize-field="normalizeField" @get-enum-options="getEnumOptions" />
              </el-tab-pane>
              <el-tab-pane label="规则配置" name="rules">
                <DesignerRulePanel :visibility-rules="visibilityRules" :cross-field-rules="crossFieldRules" :tenant-overrides="tenantOverrides" :available-fields="availableFields" @add-visibility="addVisibilityRule" @remove-visibility="removeVisibilityRule" @add-cross-field="addCrossFieldRule" @remove-cross-field="removeCrossFieldRule" @add-tenant-override="addTenantOverride" @remove-tenant-override="removeTenantOverride" />
              </el-tab-pane>
            </el-tabs>
          </div>
          <div class="preview-col">
            <DesignerPreview v-model="previewModel" v-model:role="previewRole" :schema="normalizedSchema" :trial-payload="trialPayload" :trial-loading="loading.trial" @open-full-preview="previewVisible = true" @trial-submit="trialSubmit" />
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-alert v-if="operationError" :title="operationError" type="error" show-icon :closable="false" style="margin-bottom: 12px" />
          <el-button :loading="formEngineLoading.save" type="primary" @click="saveAll">保存草稿</el-button>
          <el-button :loading="formEngineLoading.publish" type="success" @click="publish">发布</el-button>
        </div>
      </section>
    </main>

    <!-- 全屏预览弹窗 -->
    <el-drawer v-model="previewVisible" title="表单预览" size="600px">
      <div class="preview-role-badge">预览角色：<strong>{{ previewRole }}</strong></div>
      <DynamicWorkflowForm :schema="normalizedSchema" v-model="previewModel" />
    </el-drawer>
  </div>
</template>

<script setup>
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'
import { useWorkflowFormDesigner } from './workflow-form-designer/useWorkflowFormDesigner.js'
import './workflow-form-designer/workflow-form-designer.css'
import { Loading } from '@element-plus/icons-vue'
import { ref, computed } from 'vue'
import DesignerFieldList from './workflow-form-designer/components/DesignerFieldList.vue'
import DesignerRulePanel from './workflow-form-designer/components/DesignerRulePanel.vue'
import DesignerPreview from './workflow-form-designer/components/DesignerPreview.vue'

const {
  formDefinitions, formEngineDraft, formEngineLoading,
  addField, deleteField, draft, fieldTypes,
  loadFormDefinitions, newTemplate, normalizeField,
  operationError,
  previewModel, previewVisible, publish, normalizedSchema,
  selectFormDefinition, trialPayload, trialSubmit,
  loading, saveAll,
  visibilityRules, crossFieldRules, tenantOverrides,
} = useWorkflowFormDesigner()

const activeTab = ref('fields')
const previewRole = ref('admin')

const availableFields = computed(() => (draft.schema?.fields || [])
  .filter(f => f.key && !['section', 'divider', 'info'].includes(f.type))
  .map(f => ({ key: f.key, label: f.label || f.key }))
)

// 暂未开放自定义的表单 scope（含复杂交互，开放会导致业务流程断裂）
const UNSUPPORTED_PROJECT_SCOPES = ['project.initiation', 'project.detail']
const isUnsupportedProjectScope = computed(() => UNSUPPORTED_PROJECT_SCOPES.includes(formEngineDraft.scope))

function getEnumOptions(type) {
  if (type === 'tender_source') return '招标公告=bidding\n比选公告=selection\n竞争性谈判=negotiation\n单一来源=single_source'
  if (type === 'project_status') return '进行中=in_progress\n已暂停=suspended\n已结项=closed\n已取消=cancelled'
  if (type === 'qualification_type') return '营业执照=business_license\n资质证书=qualification_cert\n安全生产许可证=safety_cert\nISO认证=iso_cert'
  return ''
}

function copyField(index) {
  const original = draft.schema.fields[index]
  const copy = { ...JSON.parse(JSON.stringify(original)), key: original.key + '_copy' }
  draft.schema.fields.splice(index + 1, 0, copy)
}

function addVisibilityRule() { visibilityRules.value.push({ sourceField: '', operator: 'eq', targetValue: '', targetField: '', action: 'hide', rolePattern: '' }) }
function removeVisibilityRule(i) { visibilityRules.value.splice(i, 1) }
function addCrossFieldRule() { crossFieldRules.value.push({ fieldA: '', operator: 'less_than', fieldB: null, targetValue: '', errorMessage: '', priority: crossFieldRules.value.length }) }
function removeCrossFieldRule(i) { crossFieldRules.value.splice(i, 1) }
function addTenantOverride() { tenantOverrides.value.push({ fieldKey: '', overrideType: 'label', overrideValue: '' }) }
function removeTenantOverride(i) { tenantOverrides.value.splice(i, 1) }
</script>

<style scoped>
.workflow-designer-page { display: flex; flex-direction: column; height: 100%; }
.designer-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 24px; border-bottom: 1px solid var(--el-border-color-light); background: var(--el-fill-color-blank); }
.designer-header h1 { margin: 0; font-size: 20px; font-weight: 600; }
.designer-shell { display: flex; flex: 1; overflow: hidden; }
.template-list { width: 240px; border-right: 1px solid var(--el-border-color-light); background: var(--el-fill-color-light); overflow-y: auto; padding: 8px; }
.template-row { display: block; width: 100%; padding: 10px 12px; margin-bottom: 4px; border: 1px solid var(--el-border-color-light); border-radius: 6px; background: var(--el-fill-color-blank); text-align: left; cursor: pointer; transition: all 0.2s; }
.template-row:hover { border-color: var(--el-color-primary); background: var(--el-fill-color-light); }
.template-row.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.template-row strong { display: block; font-size: 14px; color: var(--el-text-color-primary); }
.template-row span { font-size: 12px; color: var(--el-text-color-secondary); }
.list-loading { display: flex; align-items: center; gap: 6px; padding: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.list-empty { padding: 24px; text-align: center; color: var(--el-text-color-secondary); font-size: 13px; }
.designer-main { flex: 1; overflow-y: auto; padding: 20px 24px; }
.form-grid { display: flex; gap: 24px; margin-bottom: 16px; }
.template-form { flex: 1; }
.editor-preview-layout { display: flex; gap: 16px; margin-bottom: 16px; }
.editor-col { flex: 3; min-width: 0; }
.preview-col { flex: 2; min-width: 300px; position: sticky; top: 0; align-self: flex-start; }
.field-editor-tabs :deep(.el-tabs__header) { margin-bottom: 12px; }
.action-bar { padding-top: 12px; border-top: 1px solid var(--el-border-color-lighter); }
.preview-role-badge { padding: 8px 12px; margin-bottom: 12px; background: var(--el-fill-color-light); border-radius: 6px; font-size: 13px; }
</style>
