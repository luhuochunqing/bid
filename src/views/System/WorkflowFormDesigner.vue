<template>
  <div class="workflow-designer-page">
    <header class="designer-header">
      <div>
        <p class="eyebrow">Workflow Forms</p>
        <h1>流程表单配置</h1>
      </div>
      <div class="header-actions">
        <el-button :loading="loading.templates" @click="activeSource === 'workflow' ? loadTemplates() : loadFormDefinitions()">刷新</el-button>
        <el-button v-if="activeSource === 'workflow'" type="primary" @click="newTemplate">新建表单</el-button>
      </div>
    </header>

    <main class="designer-shell">
      <!-- 表单来源切换 -->
      <div class="source-tabs">
        <button
          class="source-tab"
          :class="{ active: activeSource === 'workflow' }"
          @click="onSourceChange('workflow')"
        >OA 表单</button>
        <button
          class="source-tab"
          :class="{ active: activeSource === 'formengine' }"
          @click="onSourceChange('formengine')"
        >独立表单</button>
      </div>

      <aside class="template-list">
        <!-- OA 表单列表 -->
        <template v-if="activeSource === 'workflow'">
          <button
            v-for="template in templates"
            :key="template.templateCode"
            class="template-row"
            :class="{ active: template.templateCode === draft.templateCode }"
            type="button"
            @click="selectTemplate(template)"
          >
            <strong>{{ template.name }}</strong>
            <span>{{ template.templateCode }} · v{{ template.version || 0 }} · {{ template.status }}</span>
          </button>
          <div v-if="selectedTemplateVersions.length > 0" class="version-list">
            <div class="version-list-title">历史版本</div>
            <div
              v-for="version in selectedTemplateVersions"
              :key="`${version.templateCode}-${version.version}`"
              class="version-row"
            >
              <div>
                <p>v{{ version.version }}</p>
                <span>{{ version.publishedAt || '-' }}</span>
              </div>
              <el-button
                type="primary"
                size="small"
                :disabled="version.version === draft.version"
                @click="rollback(version.version)"
              >
                回滚
              </el-button>
            </div>
          </div>
        </template>

        <!-- 独立表单列表（form_definition_registry） -->
        <template v-else-if="activeSource === 'formengine'">
          <div v-if="formEngineLoading.list" class="list-loading">
            <el-icon class="is-loading" :size="16"><Loading /></el-icon>
            <span>加载中...</span>
          </div>
          <button
            v-for="def in formDefinitions"
            :key="def.id"
            class="template-row"
            :class="{ active: def.scope === formEngineDraft.scope }"
            type="button"
            @click="selectFormDefinition(def)"
          >
            <strong>{{ def.scopeLabel }}</strong>
            <span>{{ def.scope }} · v{{ def.version || 1 }} · {{ def.enabled ? '已启用' : '已禁用' }}</span>
          </button>
          <div v-if="!formEngineLoading.list && formDefinitions.length === 0" class="list-empty">
            暂无独立表单
          </div>
        </template>
      </aside>

      <section class="designer-main">
        <div class="form-grid">
          <el-form label-width="96px" class="template-form">
            <el-form-item label="模板编码">
              <el-input v-model="draft.templateCode" placeholder="例如 SEAL_APPLY" />
            </el-form-item>
            <el-form-item label="表单名称">
              <el-input v-model="draft.name" placeholder="例如 用章申请" />
            </el-form-item>
            <el-form-item label="业务类型">
              <el-select v-model="draft.businessType">
                <el-option v-for="type in businessTypes" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
            <el-form-item label="启用">
              <el-switch v-model="draft.enabled" />
            </el-form-item>
          </el-form>

          <section class="oa-panel">
            <h2>OA 流程绑定</h2>
            <el-input v-model="oa.workflowCode" placeholder="泛微流程 ID / workflowCode" />
            <el-input v-model="oa.provider" placeholder="Provider" />
            <el-button @click="autoMapping">按字段生成映射</el-button>
          </section>
        </div>

        <!-- Tabs: 字段配置 | 可见性规则 | 验证规则 -->
        <el-tabs v-model="activeTab" class="field-editor-tabs">
          <el-tab-pane label="字段配置" name="fields">
            <div class="section-title">
              <h2>字段配置器</h2>
              <div class="section-actions">
                <el-button @click="addField">添加字段</el-button>
                <el-button @click="newTemplate('GENERAL')" type="info" plain>新建通用</el-button>
                <el-button @click="newTemplate('TENDER')" type="info" plain>新建标讯</el-button>
                <el-button @click="newTemplate('PROJECT')" type="info" plain>新建项目</el-button>
              </div>
            </div>

            <div v-for="(field, index) in draft.schema.fields" :key="field.key" class="field-row">
              <el-input v-model="field.key" placeholder="字段 key" class="field-key-input" />
              <el-input v-model="field.label" placeholder="字段名称" class="field-label-input" />
              <el-select v-model="field.type" @change="(v) => { normalizeField(field); if (['tender_source','project_status','qualification_type'].includes(v) && !field.optionsText) field.optionsText = getEnumOptions(v) }" class="field-type-select">
                <el-option v-for="type in fieldTypes" :key="type.value" :label="type.label" :value="type.value" />
              </el-select>
              <el-checkbox v-model="field.required" :disabled="['info','section','divider'].includes(field.type)">必填</el-checkbox>
              <el-tooltip :content="getFieldHelp(field.type)" placement="top" :show-after="300">
                <span class="field-help-badge">?</span>
              </el-tooltip>
              <el-button :disabled="index === 0" @click="move(index, -1)" size="small">上</el-button>
              <el-button :disabled="index === draft.schema.fields.length - 1" @click="move(index, 1)" size="small">下</el-button>
              <el-button type="danger" @click="deleteField(field.key)" size="small">删</el-button>

              <!-- select 类型的选项编辑器 -->
              <el-input
                v-if="field.type === 'select'"
                v-model="field.optionsText"
                class="field-wide"
                placeholder="选项，格式：显示名=值，每行一个"
                type="textarea"
                :rows="2"
              />
              <!-- tender_source/project_status/qualification_type 枚举预填 -->
              <el-input
                v-if="['tender_source','project_status','qualification_type'].includes(field.type)"
                v-model="field.optionsText"
                class="field-wide"
                placeholder="枚举选项，格式：显示名=值，每行一个"
                type="textarea"
                :rows="2"
              />
              <!-- info 类型的说明内容编辑器 -->
              <el-input
                v-if="field.type === 'info'"
                v-model="field.content"
                class="field-wide"
                placeholder="说明文本内容"
                type="textarea"
                :rows="2"
              />
              <!-- textarea 行数配置 -->
              <div v-if="field.type === 'textarea'" class="field-wide field-props-row">
                <el-input-number v-model="field.rows" :min="2" :max="20" placeholder="行数" />
                <span class="field-prop-label">行</span>
                <el-input v-model="field.placeholder" placeholder="占位提示文字" class="field-placeholder" />
              </div>
              <!-- number / currency 范围配置 -->
              <div v-if="['number','currency'].includes(field.type)" class="field-wide field-props-row">
                <el-input-number v-model="field.min" placeholder="最小值" />
                <span class="field-prop-label">~</span>
                <el-input-number v-model="field.max" placeholder="最大值" />
                <span class="field-prop-label">范围</span>
              </div>
              <!-- attachment 配置 -->
              <div v-if="field.type === 'attachment'" class="field-wide field-props-row">
                <el-input v-model="field.accept" placeholder="接受文件类型，如 .pdf,.doc" style="width: 240px" />
                <el-input-number v-model="field.limit" :min="1" :max="20" placeholder="数量上限" />
                <span class="field-prop-label">个文件</span>
              </div>
              <!-- table 列定义编辑器 -->
              <div v-if="field.type === 'table'" class="field-wide table-columns-editor">
                <div class="table-columns-label">表格列定义</div>
                <div v-for="(col, ci) in (field.columns || [])" :key="ci" class="table-col-row">
                  <el-input v-model="col.key" placeholder="列key" />
                  <el-input v-model="col.label" placeholder="列名" />
                  <el-select v-model="col.type" placeholder="类型" style="width: 100px">
                    <el-option label="文本" value="text" />
                    <el-option label="数字" value="number" />
                    <el-option label="下拉" value="select" />
                  </el-select>
                  <el-checkbox v-model="col.required">必填</el-checkbox>
                  <el-button type="danger" size="small" text @click="field.columns.splice(ci, 1)">删</el-button>
                </div>
                <el-button size="small" @click="(field.columns = field.columns || []).push({ key: '', label: '', type: 'text', required: false })">+ 添加列</el-button>
                <el-input-number v-model="field.minRows" :min="1" :max="field.maxRows || 50" placeholder="最小行数" size="small" />
                <el-input-number v-model="field.maxRows" :min="field.minRows || 1" :max="100" placeholder="最大行数" size="small" />
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: 可见性规则 -->
          <el-tab-pane label="可见性规则" name="visibility">
            <div class="section-title">
              <h2>字段可见性规则</h2>
              <div class="section-actions">
                <el-button size="small" @click="addVisibilityRule">+ 添加规则</el-button>
              </div>
            </div>

            <div v-if="visibilityRules.length === 0" class="empty-tip">
              暂无可见性规则。点击上方按钮添加。
            </div>

            <div v-for="(rule, ri) in visibilityRules" :key="ri" class="rule-row">
              <span class="rule-label">当</span>
              <el-select v-model="rule.sourceField" placeholder="触发字段" style="width: 160px" clearable>
                <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
              </el-select>
              <el-select v-model="rule.operator" placeholder="操作符" style="width: 120px">
                <el-option label="等于" value="eq" />
                <el-option label="不等于" value="neq" />
                <el-option label="包含" value="contains" />
                <el-option label="为空" value="empty" />
              </el-select>
              <el-input v-model="rule.targetValue" placeholder="目标值" style="width: 140px" clearable />
              <span class="rule-label">时，对</span>
              <el-select v-model="rule.targetField" placeholder="目标字段" style="width: 160px" clearable>
                <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
              </el-select>
              <span class="rule-label">执行</span>
              <el-select v-model="rule.action" placeholder="动作" style="width: 120px">
                <el-option label="显示" value="show" />
                <el-option label="隐藏" value="hide" />
                <el-option label="只读" value="readonly" />
              </el-select>
              <span class="rule-label">（角色</span>
              <el-input v-model="rule.rolePattern" placeholder="留空=所有人" style="width: 120px" clearable />
              <span class="rule-label">）</span>
              <el-button type="danger" size="small" text @click="removeVisibilityRule(ri)">删</el-button>
            </div>

            <!-- 租户字段覆盖配置 -->
            <div class="override-section">
              <div class="section-title">
                <h3>租户字段覆盖</h3>
                <el-button size="small" @click="addTenantOverride">+ 添加覆盖</el-button>
              </div>
              <div v-if="tenantOverrides.length === 0" class="empty-tip">
                暂无租户覆盖。点击上方按钮添加。
              </div>
              <div v-for="(ov, oi) in tenantOverrides" :key="oi" class="rule-row">
                <span class="rule-label">对字段</span>
                <el-select v-model="ov.fieldKey" placeholder="字段" style="width: 160px" clearable>
                  <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
                </el-select>
                <span class="rule-label">覆盖</span>
                <el-select v-model="ov.overrideType" placeholder="类型" style="width: 140px">
                  <el-option label="标签" value="label" />
                  <el-option label="必填" value="required" />
                  <el-option label="默认值" value="default_value" />
                  <el-option label="选项" value="options" />
                  <el-option label="隐藏" value="hidden" />
                  <el-option label="只读" value="readonly" />
                </el-select>
                <span class="rule-label">为</span>
                <el-input v-model="ov.overrideValue" placeholder="覆盖值" style="width: 200px" />
                <el-button type="danger" size="small" text @click="removeTenantOverride(oi)">删</el-button>
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab: 验证规则 -->
          <el-tab-pane label="验证规则" name="validation">
            <div class="section-title">
              <h2>跨字段验证规则</h2>
              <div class="section-actions">
                <el-button size="small" @click="addCrossFieldRule">+ 添加验证规则</el-button>
              </div>
            </div>

            <div v-if="crossFieldRules.length === 0" class="empty-tip">
              暂无跨字段验证规则。点击上方按钮添加。
              <br />
              <span class="tip-sub">支持的验证：金额比较（less_than / greater_than）、日期先后（not_after）、
                互斥必填（one_filled / both_filled）、求和校验（sum_equals）等。</span>
            </div>

            <div v-for="(rule, ri) in crossFieldRules" :key="ri" class="rule-row cross-field-rule">
              <span class="rule-label">当</span>
              <el-select v-model="rule.fieldA" placeholder="字段A" style="width: 160px" clearable>
                <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
              </el-select>
              <el-select v-model="rule.operator" placeholder="操作符" style="width: 160px">
                <el-option label="小于" value="less_than" />
                <el-option label="大于" value="greater_than" />
                <el-option label="等于" value="equals" />
                <el-option label="不等于" value="not_equals" />
                <el-option label="求和等于" value="sum_equals" />
                <el-option label="至少填一个" value="one_filled" />
                <el-option label="必须都填" value="both_filled" />
                <el-option label="不晚于" value="not_after" />
              </el-select>
              <span v-if="!['one_filled','both_filled'].includes(rule.operator)" class="rule-label">字段B</span>
              <el-select
                v-if="!['one_filled','both_filled'].includes(rule.operator)"
                v-model="rule.fieldB"
                placeholder="字段B"
                style="width: 140px"
                clearable
              >
                <el-option v-for="f in availableFields" :key="f.key" :label="`${f.label} (${f.key})`" :value="f.key" />
              </el-select>
              <el-input
                v-if="!['one_filled','both_filled'].includes(rule.operator) && rule.fieldB == null"
                v-model="rule.targetValue"
                placeholder="目标值"
                style="width: 120px"
              />
              <span class="rule-label">时报错：</span>
              <el-input v-model="rule.errorMessage" placeholder="错误提示信息" style="width: 240px" />
              <el-input-number v-model="rule.priority" :min="0" :max="999" placeholder="优先级" style="width: 80px" />
              <el-button type="danger" size="small" text @click="removeCrossFieldRule(ri)">删</el-button>
            </div>

            <!-- 验证规则操作符说明 -->
            <div class="validation-help">
              <h4>操作符说明</h4>
              <table class="help-table">
                <thead><tr><th>操作符</th><th>说明</th><th>示例</th></tr></thead>
                <tbody>
                  <tr><td>less_than</td><td>字段A &lt; 字段B（或目标值）</td><td>截止日期 &lt; 开始日期</td></tr>
                  <tr><td>greater_than</td><td>字段A &gt; 字段B（或目标值）</td><td>预算 &gt; 100000</td></tr>
                  <tr><td>equals</td><td>字段A == 字段B（或目标值）</td><td>两次输入一致</td></tr>
                  <tr><td>not_equals</td><td>字段A != 字段B（或目标值）</td><td>不能重复</td></tr>
                  <tr><td>sum_equals</td><td>A+B+... == 目标值</td><td>分项之和 = 总价</td></tr>
                  <tr><td>one_filled</td><td>至少填一个（A或B）</td><td>手机或座机二选一</td></tr>
                  <tr><td>both_filled</td><td>必须都填</td><td>账号+开户行需同时提供</td></tr>
                  <tr><td>not_after</td><td>日期A不晚于日期B</td><td>截止日期不能晚于开始日期</td></tr>
                </tbody>
              </table>
            </div>
          </el-tab-pane>
        </el-tabs>

        <section class="preview-area">
          <div class="section-title">
            <h2>预览和试提交</h2>
            <div>
              <!-- M5.4: Role simulation in preview -->
              <el-select v-model="previewRole" size="small" placeholder="模拟角色" style="width: 120px; margin-right: 8px">
                <el-option label="管理员 (admin)" value="admin" />
                <el-option label="经理 (manager)" value="manager" />
                <el-option label="员工 (staff)" value="staff" />
              </el-select>
              <el-button @click="previewVisible = true">预览表单</el-button>
              <el-button :loading="loading.trial" @click="trialSubmit">试提交</el-button>
              <el-button :loading="loading.save" type="primary" @click="saveAll">保存草稿</el-button>
              <el-button :loading="loading.publish" type="success" @click="publish">发布</el-button>
            </div>
          </div>
          <el-alert v-if="operationError" :title="operationError" type="error" show-icon :closable="false" />
          <pre v-if="trialPayload">{{ trialPayload }}</pre>
        </section>
      </section>
    </main>

    <!-- 预览弹窗 -->
    <el-drawer v-model="previewVisible" title="表单预览" size="600px">
      <div class="preview-role-badge">
        预览角色：<strong>{{ previewRole }}</strong>
      </div>
      <DynamicWorkflowForm :schema="normalizedSchema" v-model="previewModel" />
    </el-drawer>
  </div>
</template>

<script setup>
import DynamicWorkflowForm from '@/components/common/DynamicWorkflowForm.vue'
import { useWorkflowFormDesigner } from './workflow-form-designer/useWorkflowFormDesigner.js'
import { FIELD_TYPE_HELP_TEXT } from './workflow-form-designer/workflowFormDesignerCore.js'
import './workflow-form-designer/workflow-form-designer.css'
import { Loading } from '@element-plus/icons-vue'
import { ref, computed } from 'vue'

const {
  activeSource,
  formDefinitions,
  formEngineDraft,
  formEngineLoading,
  addField,
  autoMapping,
  deleteField,
  draft,
  fieldTypes,
  loadFormDefinitions,
  loadTemplates,
  move,
  newTemplate,
  normalizeField,
  onSourceChange,
  operationError,
  oa,
  businessTypes,
  previewModel,
  previewVisible,
  publish,
  rollback,
  normalizedSchema,
  selectFormDefinition,
  trialPayload,
  trialSubmit,
  templates,
  selectedTemplateVersions,
  loading,
  saveAll,
  visibilityRules,
  crossFieldRules,
  tenantOverrides
} = useWorkflowFormDesigner()

// M5.4: Active tab state
const activeTab = ref('fields')

// M5.4: Role simulation for preview
const previewRole = ref('admin')

// Available fields for rule dropdowns
const availableFields = computed(() => (draft.schema?.fields || [])
  .filter(f => f.key && !['section', 'divider', 'info'].includes(f.type))
  .map(f => ({ key: f.key, label: f.label || f.key }))
)

function getFieldHelp(type) {
  return FIELD_TYPE_HELP_TEXT[type] || ''
}

function getEnumOptions(type) {
  if (type === 'tender_source') return '招标公告=bidding\n比选公告=selection\n竞争性谈判=negotiation\n单一来源=single_source'
  if (type === 'project_status') return '进行中=in_progress\n已暂停=suspended\n已结项=closed\n已取消=cancelled'
  if (type === 'qualification_type') return '营业执照=business_license\n资质证书=qualification_cert\n安全生产许可证=safety_cert\nISO认证=iso_cert'
  return ''
}

// M5.4: Visibility rule helpers
function addVisibilityRule() {
  visibilityRules.value.push({
    sourceField: '',
    operator: 'eq',
    targetValue: '',
    targetField: '',
    action: 'hide',
    rolePattern: ''
  })
}

function removeVisibilityRule(index) {
  visibilityRules.value.splice(index, 1)
}

// M5.4: Tenant override helpers
function addTenantOverride() {
  tenantOverrides.value.push({
    fieldKey: '',
    overrideType: 'label',
    overrideValue: ''
  })
}

function removeTenantOverride(index) {
  tenantOverrides.value.splice(index, 1)
}

// M5.4: Cross-field validation rule helpers
function addCrossFieldRule() {
  crossFieldRules.value.push({
    fieldA: '',
    operator: 'less_than',
    fieldB: null,
    targetValue: '',
    errorMessage: '',
    priority: crossFieldRules.value.length
  })
}

function removeCrossFieldRule(index) {
  crossFieldRules.value.splice(index, 1)
}
</script>

<style scoped>
.workflow-designer-page {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-blank);
}

.designer-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.designer-shell {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.template-list {
  width: 240px;
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-fill-color-light);
  overflow-y: auto;
  padding: 8px;
}

.template-row {
  display: block;
  width: 100%;
  padding: 10px 12px;
  margin-bottom: 4px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  text-align: left;
  cursor: pointer;
  transition: all 0.2s;
}

.template-row:hover {
  border-color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

.template-row.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.template-row strong {
  display: block;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.template-row span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.version-list {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.version-list-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
  padding: 4px 8px;
}

.version-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.designer-main {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.form-grid {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.template-form {
  flex: 1;
}

.oa-panel {
  flex: 1;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  background: var(--el-fill-color-light);
}

.oa-panel h2 {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
}

.field-editor-tabs {
  margin-bottom: 16px;
}

.field-editor-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title h2,
.section-title h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.section-title h3 {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.section-actions {
  display: flex;
  gap: 8px;
}

.field-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  flex-wrap: wrap;
}

.field-key-input {
  width: 100px;
}

.field-label-input {
  width: 140px;
}

.field-type-select {
  width: 140px;
}

.field-help-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-secondary);
  font-size: 11px;
  cursor: help;
}

.field-wide {
  width: 100%;
  margin-top: 4px;
}

.field-props-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}

.field-prop-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.field-placeholder {
  flex: 1;
}

.table-columns-editor {
  width: 100%;
  margin-top: 4px;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.table-columns-label {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
}

.table-col-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

/* Visibility / Validation rule rows */
.rule-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  flex-wrap: wrap;
}

.cross-field-rule {
  background: var(--el-fill-color-light);
}

.rule-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.override-section {
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
}

.empty-tip {
  padding: 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
  margin-bottom: 12px;
}

.tip-sub {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* Validation help table */
.validation-help {
  margin-top: 20px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.validation-help h4 {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
}

.help-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.help-table th,
.help-table td {
  padding: 5px 8px;
  border: 1px solid var(--el-border-color-lighter);
  text-align: left;
  color: var(--el-text-color-regular);
}

.help-table th {
  background: var(--el-fill-color-blank);
  font-weight: 600;
}

/* Preview area */
.preview-area {
  margin-top: 16px;
}

.preview-area pre {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 6px;
  font-size: 12px;
  overflow-x: auto;
  margin-top: 8px;
}

/* Preview role badge */
.preview-role-badge {
  padding: 8px 12px;
  margin-bottom: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

/* Source tabs */
.source-tabs {
  display: flex;
  gap: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 12px;
}

.source-tab {
  padding: 8px 16px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  color: var(--el-text-color-secondary);
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: color 0.2s, border-color 0.2s;
}

.source-tab:hover {
  color: var(--el-color-primary);
}

.source-tab.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  font-weight: 500;
}

/* List states */
.list-loading,
.list-empty {
  padding: 16px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
</style>
