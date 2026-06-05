// Input: API config, HTTP client, feature availability helpers, business API modules
// Output: @/api public exports and Promise-compatible module accessors including bid-agent, match-scoring, and integration APIs
// Pos: src/api/ - Frontend data access public entry
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

/**
 * API 统一导出
 * 真实 API 为唯一数据源
 *
 * 使用方式:
 * import { authApi, projectsApi, tendersApi } from '@/api'
 *
 * 环境变量:
 * - VITE_API_BASE_URL     后端 API 地址 (默认: http://localhost:8080)
 */

export { API_CONFIG, isCommercialMode, getApiUrl } from './config.js'
export { buildFeatureUnavailableResponse, getFeaturePlaceholder, isFeatureUnavailableResponse } from './featureAvailability.js'

import httpClient from './client.js'
import { authApi } from './modules/auth.js'
import { tendersApi } from './modules/tenders.js'
import { batchTendersApi } from './modules/tenders/batch.js'
import { crmApi } from './modules/crm.js'
import { projectsApi } from './modules/projects.js'
import qualificationsApi from './modules/qualification.js'
import { templatesApi } from './modules/templates.js'
import knowledgeApi from './modules/knowledge.js'
import { feesApi } from './modules/fees.js'
import aiApi from './modules/ai.js'
import resourcesApi from './modules/resources.js'
import collaborationApi from './modules/collaboration.js'
import { dashboardApi } from './modules/dashboard.js'
import { approvalApi } from './modules/approval.js'
import { exportApi, ExportType, ExportFormat, ExportStatus } from './modules/export.js'
import { auditApi } from './modules/audit.js'
import { settingsApi } from './modules/settings.js'
import { permissionMatrixApi } from './modules/permissionMatrix.js'
import { projectGroupsApi } from './modules/projectGroups.js'
import { bidResultsApi } from './modules/bidResults.js'
import { workbenchApi } from './modules/workbench.js'
import { bidAgentApi } from './modules/bidAgent.js'
import { bidMatchScoringApi } from './modules/bidMatchScoring.js'
import { workflowFormApi } from './modules/workflowForm.js'
import { taskStatusDictApi } from './modules/taskStatusDict.js'
import { taskStatusDictAdminApi } from './modules/taskStatusDictAdmin.js'
import { taskExtendedFieldApi } from './modules/taskExtendedField.js'
import { taskExtendedFieldAdminApi } from './modules/taskExtendedFieldAdmin.js'
import { taskActivityApi } from './modules/taskActivity.js'
import { organizationIntegrationApi, weComIntegrationApi } from './modules/systemIntegration.js'
import { tenderFavoritesApi } from './modules/tenderFavorites.js'

export {
  httpClient,
  authApi,
  tendersApi,
  batchTendersApi,
  crmApi,
  projectsApi,
  qualificationsApi,
  templatesApi,
  knowledgeApi,
  feesApi,
  aiApi,
  resourcesApi,
  collaborationApi,
  dashboardApi,
  approvalApi,
  exportApi,
  ExportType,
  ExportFormat,
  ExportStatus,
  auditApi,
  settingsApi,
  permissionMatrixApi,
  projectGroupsApi,
  bidResultsApi,
  workbenchApi,
  bidAgentApi,
  bidMatchScoringApi,
  workflowFormApi,
  taskStatusDictApi,
  taskStatusDictAdminApi,
  taskExtendedFieldApi,
  taskExtendedFieldAdminApi,
  taskActivityApi,
  tenderFavoritesApi,
  weComIntegrationApi,
  organizationIntegrationApi
}

export default {
  auth: () => Promise.resolve(authApi),
  tenders: () => Promise.resolve(tendersApi),
  tenderBatch: () => Promise.resolve(batchTendersApi),
  projects: () => Promise.resolve(projectsApi),
  qualifications: () => Promise.resolve(qualificationsApi),
  knowledge: () => Promise.resolve(knowledgeApi),
  fees: () => Promise.resolve(feesApi),
  ai: () => Promise.resolve(aiApi),
  resources: () => Promise.resolve(resourcesApi),
  collaboration: () => Promise.resolve(collaborationApi),
  dashboard: () => Promise.resolve(dashboardApi),
  approval: () => Promise.resolve(approvalApi),
  export: () => Promise.resolve(exportApi),
  audit: () => Promise.resolve(auditApi),
  settings: () => Promise.resolve(settingsApi),
  permissionMatrix: () => Promise.resolve(permissionMatrixApi),
  projectGroups: () => Promise.resolve(projectGroupsApi),
  workbench: () => Promise.resolve(workbenchApi),
  bidAgent: () => Promise.resolve(bidAgentApi),
  bidMatchScoring: () => Promise.resolve(bidMatchScoringApi),
  workflowForm: () => Promise.resolve(workflowFormApi),
  taskStatusDict: () => Promise.resolve(taskStatusDictApi),
  taskStatusDictAdmin: () => Promise.resolve(taskStatusDictAdminApi),
  taskExtendedField: () => Promise.resolve(taskExtendedFieldApi),
  taskExtendedFieldAdmin: () => Promise.resolve(taskExtendedFieldAdminApi),
  taskActivity: () => Promise.resolve(taskActivityApi),
  tenderFavorites: () => Promise.resolve(tenderFavoritesApi),
  weComIntegration: () => Promise.resolve(weComIntegrationApi),
  organizationIntegration: () => Promise.resolve(organizationIntegrationApi)
}
