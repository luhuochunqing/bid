// Input: knowledge API access, feature placeholder helper, and template form payloads
// Output: remote template-library operations for list/query/save/activity actions
// Pos: src/views/Knowledge/components/template/ - template page remote data helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { isFeatureUnavailableResponse, knowledgeApi } from '@/api'
import { notifyFeatureUnavailable } from '@/utils/featureFeedback'
import { extractTags } from './templateLibraryHelpers.js'

export function getCurrentUserId(userStore) {
  const rawId = userStore.currentUser?.id
  if (rawId === undefined || rawId === null || rawId === '') return null
  const numericId = Number(rawId)
  return Number.isFinite(numericId) ? numericId : null
}

export function upsertTemplateInCollection(items, template) {
  const index = items.findIndex((item) => String(item.id) === String(template.id))
  if (index > -1) {
    items.splice(index, 1, template)
    return
  }
  items.unshift(template)
}

export async function fetchTemplateList(query) {
  const result = await knowledgeApi.templates.getList(query)
  if (result?.success) {
    return {
      templates: Array.isArray(result.data) ? result.data : [],
      featurePlaceholder: null,
      errorMessage: ''
    }
  }

  const featurePlaceholder = notifyFeatureUnavailable(result, {
    fallback: {
      title: '模板库当前不可用',
      hint: '当前无法加载模板列表，请稍后重试或联系管理员检查知识库服务。'
    }
  })

  return {
    templates: [],
    featurePlaceholder,
    errorMessage: featurePlaceholder ? '' : result?.msg || ''
  }
}

export async function saveTemplate(mode, templateForm, createdBy) {
  const payload = {
    ...templateForm,
    tags: extractTags(templateForm.tagsText),
    createdBy
  }
  return mode === 'create'
    ? knowledgeApi.templates.create(payload)
    : knowledgeApi.templates.update(templateForm.id, payload)
}

export async function recordTemplateUse(templateId, useTemplateForm, usedBy) {
  return knowledgeApi.templates.recordUse(templateId, {
    documentName: useTemplateForm.docName,
    docType: useTemplateForm.docType,
    projectId: useTemplateForm.projectId || null,
    applyOptions: useTemplateForm.applyOptions,
    usedBy
  })
}

export async function loadTemplateVersions(templateId) {
  const result = await knowledgeApi.templates.getVersions(templateId)
  if (isFeatureUnavailableResponse(result)) {
    return {
      versions: [],
      placeholder: notifyFeatureUnavailable(result, {
        fallback: {
          title: '版本历史当前不可用',
          hint: '当前无法加载模板版本轨迹，可继续使用模板主体能力。'
        }
      }),
      errorMessage: ''
    }
  }
  if (!result?.success) {
    return {
      versions: [],
      placeholder: null,
      errorMessage: result?.msg || '获取版本历史失败'
    }
  }
  return {
    versions: result.data || [],
    placeholder: null,
    errorMessage: ''
  }
}

export async function recordTemplateDownload(templateId, downloadedBy) {
  return knowledgeApi.templates.recordDownload(templateId, { downloadedBy })
}

export async function copyTemplateRecord(template, createdBy) {
  return knowledgeApi.templates.copy(template.id, {
    name: `${template.name}（副本）`,
    createdBy
  })
}

export async function deleteTemplateRecord(templateId) {
  return knowledgeApi.templates.delete(templateId)
}
