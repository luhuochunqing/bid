// Input: httpClient for workflow-form requests and attachment files
// Output: workflowFormApi - dynamic workflow form schema, attachment upload and submission accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

function normalizeWorkflowType(workflowType) {
  return String(workflowType || 'QUALIFICATION_BORROW').toUpperCase()
}

async function getFormDefinition(workflowType = 'QUALIFICATION_BORROW') {
  const normalized = normalizeWorkflowType(workflowType)
  return httpClient.get(`/api/workflow-forms/templates/${normalized}/active`)
}

async function submitWorkflowForm(workflowType, payload = {}) {
  const normalized = normalizeWorkflowType(workflowType)
  return httpClient.post('/api/workflow-forms/instances', {
    templateCode: payload.templateCode || normalized,
    businessType: payload.businessType || normalized,
    projectId: payload.projectId ?? payload.formData?.projectId ?? null,
    applicantName: payload.applicantName || '',
    formData: payload.formData || {}
  })
}

async function getWorkflowInstance(id) {
  return httpClient.get(`/api/workflow-forms/instances/${id}`)
}

async function listAdminTemplates() {
  return httpClient.get('/api/admin/workflow-forms/templates')
}

async function listTemplateVersions(templateCode) {
  return httpClient.get(`/api/admin/workflow-forms/templates/${templateCode}/versions`)
}

async function listBusinessTypes() {
  return httpClient.get('/api/admin/workflow-forms/business-types')
}

async function createTemplateDraft(payload = {}) {
  return httpClient.post('/api/admin/workflow-forms/templates', payload)
}

async function updateTemplateDraft(templateCode, payload = {}) {
  return httpClient.put(`/api/admin/workflow-forms/templates/${templateCode}/draft`, payload)
}

async function saveOaBinding(templateCode, payload = {}) {
  return httpClient.put(`/api/admin/workflow-forms/templates/${templateCode}/oa-binding`, payload)
}

async function publishTemplate(templateCode) {
  return httpClient.post(`/api/admin/workflow-forms/templates/${templateCode}/publish`)
}

async function rollbackTemplateVersion(templateCode, version) {
  return httpClient.post(`/api/admin/workflow-forms/templates/${templateCode}/versions/${version}/rollback`)
}

async function testSubmitTemplate(templateCode, payload = {}) {
  return httpClient.post(`/api/admin/workflow-forms/templates/${templateCode}/oa/test-submit`, payload)
}

async function uploadWorkflowFormAttachment(templateCode, fieldKey, file, options = {}) {
  const formData = new FormData()
  formData.append('templateCode', normalizeWorkflowType(templateCode))
  formData.append('fieldKey', fieldKey)
  if (options.projectId !== undefined && options.projectId !== null && options.projectId !== '') {
    formData.append('projectId', String(options.projectId))
  }
  formData.append('file', file)
  return httpClient.post('/api/workflow-forms/attachments', formData)
}

// ================================================================
// Form Definition Registry API (Dynamic Form Engine)
// Endpoints: /api/form-definitions/{scope}/active, /submit, etc.
// ================================================================

async function getActiveFormDefinition(scope) {
  return httpClient.get(`/api/form-definitions/${scope}/active`)
}

async function submitFormDefinition(scope, payload = {}) {
  return httpClient.post(`/api/form-definitions/${scope}/submit`, payload)
}

export const formDefinitionApi = {
  getActiveFormDefinition,
  submitFormDefinition,
  listFormDefinitions: (page = 0, size = 20) => httpClient.get(`/api/admin/form-definitions?page=${page}&size=${size}`),
  getFormDefinitionById: (id) => httpClient.get(`/api/admin/form-definitions/${id}`),
  createFormDefinition: (payload) => httpClient.post('/api/admin/form-definitions', payload),
  updateFormDefinition: (id, payload) => httpClient.put(`/api/admin/form-definitions/${id}`, payload),
  publishFormDefinition: (id) => httpClient.post(`/api/admin/form-definitions/${id}/publish`),
  saveVisibilityRules: (id, rules) => httpClient.post(`/api/admin/form-definitions/${id}/visibility`, rules),
  saveConditionRules: (id, rules) => httpClient.post(`/api/admin/form-definitions/${id}/conditions`, rules),
  getVisibilityRules: (id) => httpClient.get(`/api/admin/form-definitions/${id}/visibility`),
  getConditionRules: (id) => httpClient.get(`/api/admin/form-definitions/${id}/conditions`),
  getCrossFieldRules: (id) => httpClient.get(`/api/admin/form-definitions/${id}/cross-field-rules`),
  saveCrossFieldRules: (id, rules) => httpClient.post(`/api/admin/form-definitions/${id}/cross-field-rules`, rules),
  getTenantOverrides: (id) => httpClient.get(`/api/admin/form-definitions/${id}/tenant-overrides`),
  saveTenantOverrides: (id, overrides) => httpClient.post(`/api/admin/form-definitions/${id}/tenant-overrides`, overrides),
}

export const workflowFormApi = {
  getFormDefinition,
  submitWorkflowForm,
  getWorkflowInstance,
  uploadWorkflowFormAttachment,
  listAdminTemplates,
  listTemplateVersions,
  listBusinessTypes,
  createTemplateDraft,
  updateTemplateDraft,
  saveOaBinding,
  publishTemplate,
  rollbackTemplateVersion,
  testSubmitTemplate
}

// Aliases for AdaptiveFormPage compatibility
export const adaptiveFormApi = {
  getActive: getActiveFormDefinition,
  submit: submitFormDefinition
}

export default workflowFormApi
