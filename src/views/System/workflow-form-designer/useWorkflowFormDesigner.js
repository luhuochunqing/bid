import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { workflowFormApi, formDefinitionApi } from '@/api/modules/workflowForm.js'
import {
  FIELD_TYPES,
  buildDefaultTemplate,
  buildMappingFromFields,
  buildSelectedTemplateState,
  createField,
  extractWorkflowFormError,
  moveField,
  removeField
} from './workflowFormDesignerCore.js'

export function useWorkflowFormDesigner() {
  const templates = ref([])
  const selectedTemplateVersions = ref([])
  const businessTypes = ref(['GENERAL_WORKFLOW', 'QUALIFICATION_BORROW'])
  const draft = reactive(buildDefaultTemplate())
  const oa = reactive({ provider: 'WEAVER', workflowCode: '', fieldMapping: { workflowCode: '', mainFields: [] } })
  const fieldTypes = FIELD_TYPES
  const previewVisible = ref(false)
  const previewModel = ref({})
  const trialPayload = ref('')
  const operationError = ref('')
  const loading = reactive({ templates: false, save: false, publish: false, trial: false })

  // ---------- Form Engine 独立表单（隔离状态）----------
  const activeSource = ref('workflow')  // 'workflow' | 'formengine'
  const formDefinitions = ref([])
  const formEngineDraft = reactive({ scope: '', scopeLabel: '', enabled: false, schema: { fields: [] } })
  const formEngineLoading = reactive({ list: false, save: false, publish: false })
  const formEngineError = ref('')

  // FormEngine 规则状态（由 selectFormDefinition 加载，saveAll 使用）
  const visibilityRules = ref([])
  const crossFieldRules = ref([])
  const tenantOverrides = ref([])


  async function loadFormDefinitions() {
    formEngineLoading.list = true
    formEngineError.value = ''
    try {
      const response = await formDefinitionApi.listFormDefinitions(0, 100)
      const page = response?.data
      if (page && Array.isArray(page.content)) {
        formDefinitions.value = page.content
      } else if (Array.isArray(response?.data)) {
        formDefinitions.value = response.data
      } else {
        formDefinitions.value = []
      }
    } catch (error) {
      formEngineError.value = '独立表单加载失败'
      ElMessage.error(formEngineError.value)
    } finally {
      formEngineLoading.list = false
    }
  }

  function selectFormDefinition(def) {
    let schema
    try {
      schema = typeof def.schemaJson === 'string' ? JSON.parse(def.schemaJson) : (def.schemaJson || {})
    } catch {
      schema = {}
    }
    formEngineDraft.scope = def.scope || ''
    formEngineDraft.scopeLabel = def.scopeLabel || ''
    formEngineDraft.enabled = def.enabled ?? false
    formEngineDraft.schema = schema.fields ? schema : { fields: [] }

    // 加载可见性和条件规则
    loadFormDefinitionRules(def.id)
  }

  async function loadFormDefinitionRules(defId) {
    if (!defId) {
      visibilityRules.value = []
      crossFieldRules.value = []
      tenantOverrides.value = []
      return
    }
    try {
      const [visResp, condResp, crossResp, tenantResp] = await Promise.all([
        formDefinitionApi.getVisibilityRules(defId),
        formDefinitionApi.getConditionRules(defId),
        formDefinitionApi.getCrossFieldRules(defId),
        formDefinitionApi.getTenantOverrides(defId)
      ])
      // 将后端实体映射为前端 rule 格式
      visibilityRules.value = (visResp?.data || []).map(e => ({
        sourceField: e.sourceField || e.fieldKey,
        operator: e.operator || 'eq',
        targetValue: e.targetValue || '',
        targetField: e.targetField || e.fieldKey,
        action: e.action || (e.visible ? 'show' : e.hidden ? 'hide' : 'readonly'),
        rolePattern: e.rolePattern || '',
        visible: e.visible ?? true,
        readonly: e.readonly ?? false,
        hidden: e.hidden ?? false
      }))
      crossFieldRules.value = (condResp?.data || []).map(e => ({
        fieldA: e.sourceField || '',
        operator: e.operator || 'equals',
        fieldB: e.targetField || null,
        targetValue: e.targetValue || '',
        errorMessage: e.errorMessage || '',
        priority: e.displayOrder || 0
      }))
      // 跨字段验证规则
      crossFieldRules.value = [...crossFieldRules.value, ...(crossResp?.data || []).map(e => ({
        fieldA: e.sourceField || '',
        operator: e.operator || 'equals',
        fieldB: e.targetField || null,
        targetValue: e.targetValue || '',
        errorMessage: e.errorMessage || '',
        priority: e.priority || 0
      }))]
      // 租户覆盖规则
      tenantOverrides.value = (tenantResp?.data || []).map(e => ({
        fieldKey: e.fieldKey || '',
        overrideType: e.overrideType || 'label',
        overrideValue: e.overrideValue || ''
      }))
    } catch {
      // 忽略加载失败，保持空规则
      visibilityRules.value = []
      crossFieldRules.value = []
      tenantOverrides.value = []
    }
  }

  async function onSourceChange(source) {
    activeSource.value = source
    if (source === 'workflow') {
      await loadTemplates()
    } else {
      await loadFormDefinitions()
    }
  }

  const normalizedSchema = computed(() => ({
    fields: draft.schema.fields.map(normalizeFieldOptions)
  }))

  function normalizeFieldOptions(field) {
    if (field.type !== 'select') return field
    const optionsText = field.optionsText || field.options?.map((option) => `${option.label}=${option.value}`).join('\n') || ''
    return {
      ...field,
      options: optionsText.split('\n').map((line) => {
        const [label, value] = line.split('=')
        return { label: label?.trim(), value: (value || label || '').trim() }
      }).filter((option) => option.label)
    }
  }

  async function loadTemplates() {
    loading.templates = true
    operationError.value = ''
    try {
      const [templateResponse, typeResponse] = await Promise.all([
        workflowFormApi.listAdminTemplates(),
        workflowFormApi.listBusinessTypes()
      ])
      templates.value = templateResponse.data || []
      businessTypes.value = typeResponse.data || businessTypes.value
      if (templates.value.length > 0) {
        await selectTemplate(templates.value[0])
      } else {
        selectedTemplateVersions.value = []
      }
    } catch (error) {
      operationError.value = extractWorkflowFormError(error, '流程表单模板加载失败')
      ElMessage.error(operationError.value)
    } finally {
      loading.templates = false
    }
  }

  async function selectTemplate(template) {
    const selected = buildSelectedTemplateState(template)
    Object.assign(draft, selected.draft)
    Object.assign(oa, selected.oa)
    if (template?.templateCode) {
      await loadTemplateVersions(template.templateCode)
    } else {
      selectedTemplateVersions.value = []
    }
  }

  async function loadTemplateVersions(templateCode) {
    try {
      const versionResponse = await workflowFormApi.listTemplateVersions(templateCode)
      selectedTemplateVersions.value = versionResponse.data || []
    } catch (error) {
      selectedTemplateVersions.value = []
      ElMessage.error(extractWorkflowFormError(error, '历史版本加载失败'))
    }
  }

  function addField() {
    draft.schema.fields.push(createField(`field${draft.schema.fields.length + 1}`, '新字段', 'text'))
  }

  function deleteField(key) {
    draft.schema.fields = removeField(draft.schema.fields, key)
  }

  function move(index, direction) {
    draft.schema.fields = moveField(draft.schema.fields, index, direction)
  }

  function normalizeField(field) {
    if (field.type === 'select') field.optionsText = field.optionsText || '选项一=option_1'
    if (field.type === 'info') field.required = false
    // section / divider cannot be required
    if (field.type === 'section' || field.type === 'divider') field.required = false
    // phone/email/currency/address default to not required
    if (['phone', 'email', 'url', 'currency', 'percent', 'address', 'tender_source', 'project_status', 'qualification_type'].includes(field.type)) {
      field.required = false
    }
  }

  function autoMapping() {
    oa.fieldMapping = buildMappingFromFields(oa.workflowCode || `WF_${draft.templateCode}`, normalizedSchema.value.fields)
    oa.workflowCode = oa.fieldMapping.workflowCode
  }

  // ---------- scope 辅助 ----------
  function newTemplate(scope = 'GENERAL') {
    const tmpl = buildDefaultTemplate(scope)
    Object.assign(draft, tmpl)
    Object.assign(oa, { provider: 'WEAVER', workflowCode: '', fieldMapping: { workflowCode: '', mainFields: [] } })
    selectedTemplateVersions.value = []
  }

  async function saveAll() {
    if (activeSource.value === 'formengine') {
      // FormEngine 独立表单保存分支
      const def = formDefinitions.value.find(d => d.scope === formEngineDraft.scope)
      if (!def) {
        ElMessage.error('未选中独立表单')
        throw new Error('No form selected')
      }
      formEngineLoading.save = true
      formEngineError.value = ''
      try {
        // 保存 schema
        await formDefinitionApi.updateFormDefinition(def.id, {
          scopeLabel: formEngineDraft.scopeLabel,
          schema: { fields: formEngineDraft.schema.fields }
        })
        // 保存可见性规则（转换为后端 DTO 格式）
        const visibilityDtoList = visibilityRules.value.map(r => ({
          fieldKey: r.targetField || r.fieldKey || '',
          rolePattern: r.rolePattern || null,
          orgId: null,
          visible: r.visible ?? true,
          readonly: r.readonly ?? false,
          hidden: r.hidden ?? false
        }))
        await formDefinitionApi.saveVisibilityRules(def.id, visibilityDtoList)
        // 保存条件规则（转换为后端 DTO 格式）
        const conditionDtoList = crossFieldRules.value.filter(r => r.operator && ['eq', 'neq', 'in', 'contains', 'gt', 'lt'].includes(r.operator)).map(r => ({
          sourceField: r.fieldA || '',
          operator: r.operator || 'equals',
          targetValue: r.targetValue || '',
          action: r.action || 'show',
          targetField: r.fieldB || '',
          displayOrder: r.priority || 0
        }))
        await formDefinitionApi.saveConditionRules(def.id, conditionDtoList)
        // 保存跨字段验证规则
        const crossFieldDtoList = crossFieldRules.value.filter(r => r.operator && ['less_than', 'greater_than', 'equals', 'not_equals', 'sum_equals', 'one_filled', 'both_filled', 'not_after'].includes(r.operator)).map(r => ({
          fieldA: r.fieldA || '',
          operator: r.operator || 'equals',
          fieldB: r.fieldB || '',
          targetValue: r.targetValue || '',
          errorMessage: r.errorMessage || '',
          priority: r.priority || 0
        }))
        if (crossFieldDtoList.length > 0) {
          await formDefinitionApi.saveCrossFieldRules(def.id, crossFieldDtoList)
        }
        // 保存租户覆盖规则
        const tenantOverrideDtoList = tenantOverrides.value.map(o => ({
          fieldKey: o.fieldKey || '',
          overrideType: o.overrideType || 'label',
          overrideValue: o.overrideValue || ''
        }))
        if (tenantOverrideDtoList.length > 0) {
          await formDefinitionApi.saveTenantOverrides(def.id, tenantOverrideDtoList)
        }
        ElMessage.success('独立表单已保存')
        await loadFormDefinitions()
      } catch (error) {
        formEngineError.value = '独立表单保存失败'
        ElMessage.error(formEngineError.value)
        throw error
      } finally {
        formEngineLoading.save = false
      }
    } else {
      // Workflow 表单保存分支（原有逻辑）
      loading.save = true
      operationError.value = ''
      try {
        const payload = { ...draft, schema: normalizedSchema.value }
        await workflowFormApi.createTemplateDraft(payload)
        await workflowFormApi.saveOaBinding(draft.templateCode, {
          provider: oa.provider,
          workflowCode: oa.workflowCode,
          fieldMapping: oa.fieldMapping,
          enabled: true
        })
        ElMessage.success('流程表单草稿已保存')
        await loadTemplates()
      } catch (error) {
        operationError.value = extractWorkflowFormError(error, '流程表单草稿保存失败')
        ElMessage.error(operationError.value)
        throw error
      } finally {
        loading.save = false
      }
    }
  }

  async function publish() {
    if (activeSource.value === 'formengine') {
      // FormEngine 独立表单发布分支
      const def = formDefinitions.value.find(d => d.scope === formEngineDraft.scope)
      if (!def) {
        ElMessage.error('未选中独立表单')
        return
      }
      formEngineLoading.publish = true
      formEngineError.value = ''
      try {
        // 先保存（复用 saveAll 逻辑）
        await saveAll()
        // 调用发布 API
        await formDefinitionApi.publishFormDefinition(def.id)
        ElMessage.success('独立表单已发布')
        await loadFormDefinitions()
      } catch (error) {
        formEngineError.value = '独立表单发布失败'
        ElMessage.error(formEngineError.value)
      } finally {
        formEngineLoading.publish = false
      }
    } else {
      // Workflow 表单发布分支（原有逻辑）
      loading.publish = true
      operationError.value = ''
      try {
        await saveAll()
        await workflowFormApi.publishTemplate(draft.templateCode)
        ElMessage.success('流程表单已发布')
        await loadTemplates()
      } catch (error) {
        operationError.value = extractWorkflowFormError(error, '流程表单发布失败')
        ElMessage.error(operationError.value)
      } finally {
        loading.publish = false
      }
    }
  }

  async function rollback(version) {
    if (!draft.templateCode) return
    loading.save = true
    operationError.value = ''
    try {
      await workflowFormApi.rollbackTemplateVersion(draft.templateCode, version)
      ElMessage.success(`已回滚到 v${version}`)
      await loadTemplates()
    } catch (error) {
      operationError.value = extractWorkflowFormError(error, '回滚历史版本失败')
      ElMessage.error(operationError.value)
    } finally {
      loading.save = false
    }
  }

  async function trialSubmit() {
    loading.trial = true
    operationError.value = ''
    try {
      const response = await workflowFormApi.testSubmitTemplate(draft.templateCode, {
        applicantName: '测试管理员',
        formData: Object.fromEntries(
          normalizedSchema.value.fields.map((field) => [field.key, previewModel.value[field.key] || `测试${field.label}`])
        )
      })
      trialPayload.value = JSON.stringify(response.data, null, 2)
      if (response.data?.oaStarted) {
        ElMessage.success('OA 测试流程已发起')
      }
    } catch (error) {
      operationError.value = extractWorkflowFormError(error, '流程表单试提交失败')
      ElMessage.error(operationError.value)
    } finally {
      loading.trial = false
    }
  }

  onMounted(loadTemplates)

  return {
    templates,
    selectedTemplateVersions,
    businessTypes,
    draft,
    oa,
    fieldTypes,
    previewVisible,
    previewModel,
    trialPayload,
    operationError,
    loading,
    normalizedSchema,
    activeSource,
    formDefinitions,
    formEngineDraft,
    formEngineLoading,
    formEngineError,
    visibilityRules,
    crossFieldRules,
    tenantOverrides,
    addField,
    autoMapping,
    deleteField,
    loadFormDefinitions,
    loadFormDefinitionRules,
    loadTemplateVersions,
    loadTemplates,
    move,
    newTemplate,
    normalizeField,
    normalizeFieldOptions,
    onSourceChange,
    publish,
    rollback,
    saveAll,
    selectFormDefinition,
    selectTemplate,
    trialSubmit
  }
}
