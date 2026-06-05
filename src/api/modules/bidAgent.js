// Input: httpClient and project bid-agent endpoints
// Output: bidAgentApi - create/status/apply/review accessors for bid writing runs
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const toArray = (value) => (Array.isArray(value) ? value : [])

function normalizeConfidence(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return null
  return numeric > 1 ? Math.round(numeric) : Math.round(numeric * 100)
}

function normalizeSection(section = {}) {
  const metadata = section.metadata || {}
  const sourceReferences = section.sourceReferences || metadata.sourceReferences || []
  return {
    ...section,
    id: section.id ?? section.sectionId ?? section.key,
    title: section.title || section.name || section.heading || '未命名章节',
    content: section.content || section.text || section.body || section.summary || '',
    source: section.source || section.sourceName || section.sourceTitle || metadata.source || sourceReferences[0] || '',
    confidence: normalizeConfidence(section.confidence ?? metadata.confidence),
  }
}

function artifactToSection(artifact = {}) {
  return normalizeSection({
    id: artifact.id,
    title: artifact.title || artifact.artifactType || 'AI 生成内容',
    content: artifact.content,
    sourceReferences: [`bid-agent-artifact:${artifact.id}`],
    confidence: artifact.confidence,
  })
}

function normalizeDraftSections(data = {}, draft = {}) {
  if (toArray(draft.sections).length) return toArray(draft.sections).map(normalizeSection)
  if (toArray(data.draftSections).length || toArray(data.sections).length) {
    return toArray(data.draftSections || data.sections).map(normalizeSection)
  }
  if (toArray(data.artifacts).length) {
    return toArray(data.artifacts)
      .filter((artifact) => ['DRAFT_TEXT', 'HANDOFF_CHECKLIST'].includes(String(artifact.artifactType || '').toUpperCase()))
      .map(artifactToSection)
  }
  if (data.draftText) {
    return [normalizeSection({ id: `${data.id ?? data.runId}-draft`, title: '自动生成投标草稿', content: data.draftText })]
  }
  return []
}

function normalizeStage(stage = {}) {
  return {
    ...stage,
    key: stage.key || stage.code || stage.name || stage.stage,
    title: stage.title || stage.name || stage.label || stage.stage || '处理阶段',
    status: stage.status || stage.state || 'PENDING',
    message: stage.message || stage.description || '',
  }
}

export function normalizeBidAgentRun(data = null) {
  if (!data) return null
  const draft = data.draft || data.draftResult || {}
  const manualConfirmation = data.manualConfirmation || {}
  const gapCheck = data.gapCheck || {}

  return {
    ...data,
    id: data.id ?? data.runId,
    runId: data.runId ?? data.id,
    status: data.status || data.state || 'UNKNOWN',
    stages: toArray(data.stages || data.stageStatuses).map(normalizeStage),
    draft: {
      ...draft,
      sections: normalizeDraftSections(data, draft),
    },
    gaps: toArray(data.gaps || data.requirementGaps || gapCheck.gaps),
    risks: toArray(data.risks || data.riskItems),
    manualConfirmations: toArray(data.manualConfirmations || data.confirmations || manualConfirmation.reasons),
  }
}

export const bidAgentApi = {
  async importTenderDocument(projectId, formData) {
    return httpClient.post(`/api/projects/${projectId}/bid-agent/tender-documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  async createRun(projectId, payload = {}) {
    const response = await httpClient.post(`/api/projects/${projectId}/bid-agent/runs`, payload)
    return { ...response, data: normalizeBidAgentRun(response?.data) }
  },

  async getRun(projectId, runId) {
    const response = await httpClient.get(`/api/projects/${projectId}/bid-agent/runs/${runId}`)
    return { ...response, data: normalizeBidAgentRun(response?.data) }
  },

  async applyRun(projectId, runId, payload = {}) {
    return httpClient.post(`/api/projects/${projectId}/bid-agent/runs/${runId}/apply`, payload)
  },

  async createReview(projectId, payload = {}) {
    if (payload.runId) {
      const { runId, ...body } = payload
      return httpClient.post(`/api/projects/${projectId}/bid-agent/runs/${runId}/reviews`, body)
    }
    return httpClient.post(`/api/projects/${projectId}/bid-agent/reviews`, payload)
  },

  async getQualificationMatch(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/qualification-match`)
  },

  async getTechnicalRequirements(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/technical-requirements`)
  },

  async getCommercialRequirements(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/commercial-requirements`)
  },

  async getRiskClassification(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/risk-classification`)
  },

  async getScoringCriteria(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/scoring-criteria`)
  },

  async getKnowledgeBaseMatch(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/knowledge-base-match`)
  },

  async getFullAnalysis(projectId) {
    return httpClient.get(`/api/projects/${projectId}/bid-agent/full-analysis`)
  },
}

export default bidAgentApi
