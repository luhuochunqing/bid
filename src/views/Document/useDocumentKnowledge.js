import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { knowledgeApi } from '@/api'
import { useUserStore } from '@/stores/user'
import {
  buildKnowledgeQuery,
  buildReferencePayload,
  mergeSectionSourceMetadata,
  normalizeKnowledgeMatch
} from './documentEditorHelpers.js'

function buildKnowledgeRelevance(item = {}, keyword = '') {
  const text = `${item.title || ''} ${item.summary || ''} ${item.content || ''}`.toLowerCase()
  const tokens = String(keyword || '')
    .toLowerCase()
    .split(/\s+/)
    .filter(Boolean)

  if (tokens.length === 0) {
    return Number(item.useCount || item.viewCount || item.relevance || 0)
  }

  return tokens.reduce((score, token) => {
    if (text.includes(token)) return score + 30
    return score
  }, Number(item.useCount || item.viewCount || item.relevance || 0))
}

function normalizeCaseMatch(item = {}, keyword = '') {
  return normalizeKnowledgeMatch({
    id: item.id,
    type: 'case',
    title: item.title || '未命名案例',
    summary: item.summary || item.description || '',
    content: item.summary || item.description || '',
    relevance: Math.min(100, buildKnowledgeRelevance(item, keyword)),
    sourceLabel: '案例库',
    sourceDetail: [item.customer || item.customerName, item.industry, item.year].filter(Boolean).join(' · ')
  })
}

function normalizeTemplateMatch(item = {}, keyword = '') {
  return normalizeKnowledgeMatch({
    id: item.id,
    type: 'template',
    title: item.name || item.title || '未命名模板',
    summary: item.description || item.summary || '',
    content: item.content || item.templateContent || '',
    relevance: Math.min(100, buildKnowledgeRelevance(item, keyword)),
    sourceLabel: '模板库',
    sourceDetail: [item.category, item.version].filter(Boolean).join(' · ')
  })
}

function buildKnowledgeKeywords(query = {}) {
  const rawKeyword = String(query.keyword || '').trim()
  const keywordParts = rawKeyword.split(/\s+/).filter(Boolean)
  const primary = rawKeyword
  const broad = keywordParts.slice(0, 2).join(' ')
  const focused = keywordParts.slice(-2).join(' ')

  return [primary, broad, focused].filter((value, index, list) => value && list.indexOf(value) === index)
}

function extractKnowledgeItems(result) {
  return Array.isArray(result?.data) ? result.data : []
}

export function useDocumentKnowledge({
  currentSection,
  projectInfo,
  documentInfo,
  isRemoteProjectId
}) {
  const userStore = useUserStore()
  const knowledgeMatches = ref([])

  async function loadKnowledgeMatches(section) {
    const targetSection = section || currentSection.value

    if (!targetSection) {
      knowledgeMatches.value = []
      return []
    }

    if (!isRemoteProjectId.value) {
      knowledgeMatches.value = []
      return []
    }

    const query = buildKnowledgeQuery(targetSection, documentInfo.value)

    const keywords = buildKnowledgeKeywords(query)
    if (keywords.length === 0) {
      knowledgeMatches.value = []
      return []
    }

    try {
      for (const keyword of keywords) {
        const [caseResult, templateResult] = await Promise.allSettled([
          knowledgeApi.cases.getList({ keyword, page: 1, pageSize: 4 }),
          knowledgeApi.templates.getList({ name: keyword })
        ])

        const caseMatches = (caseResult.status === 'fulfilled' ? extractKnowledgeItems(caseResult.value) : [])
          .slice(0, 4)
          .map((item) => normalizeCaseMatch(item, keyword))
        const templateMatches = (templateResult.status === 'fulfilled' ? extractKnowledgeItems(templateResult.value) : [])
          .slice(0, 4)
          .map((item) => normalizeTemplateMatch(item, keyword))

        knowledgeMatches.value = [...caseMatches, ...templateMatches]
          .sort((a, b) => b.relevance - a.relevance)
          .slice(0, 5)

        if (knowledgeMatches.value.length > 0) {
          return knowledgeMatches.value
        }
      }

      knowledgeMatches.value = []
      return []
    } catch (error) {
      knowledgeMatches.value = []
      return []
    }
  }

  async function handleInsertKnowledge(match) {
    const section = currentSection.value
    if (!section || !match) return

    const insertedAt = new Date().toISOString()
    const sourceLabel = match.type === 'case' ? '案例库' : '模板库'

    if (match.type === 'case' && /^\d+$/.test(String(match.id))) {
      try {
        const payload = buildReferencePayload(match, userStore.currentUser || {}, section, projectInfo.value || {})
        const response = await knowledgeApi.cases.createReferenceRecord(match.id, payload)
        if (!response?.success || !response?.data) {
          throw new Error(response?.msg || '引用记录创建失败')
        }

        const referenceData = response.data
        const citation = {
          kind: 'case',
          title: match.title,
          sourceLabel,
          sourceDetail: match.sourceDetail || '',
          referenceId: referenceData.id ?? null,
          referenceTarget: payload.referenceTarget,
          referenceContext: payload.referenceContext,
          referencedAt: insertedAt
        }

        mergeSectionSourceMetadata(section, citation)
        section.content = `${section.content || ''}\n\n> 来源：${sourceLabel} · ${match.title}\n> 引用记录：${citation.referenceId || '创建成功'}\n\n${match.content || ''}\n`
        ElMessage.success('案例已插入并记录引用')
      } catch (error) {
        ElMessage.error(error?.message || '案例引用失败，未插入正文')
      }

      return
    }

    const citation = {
      kind: match.type || 'template',
      title: match.title,
      sourceLabel,
      sourceDetail: match.sourceDetail || '',
      referencedAt: insertedAt
    }

    mergeSectionSourceMetadata(section, citation)
    section.content = `${section.content || ''}\n\n> 来源：${sourceLabel} · ${match.title}\n\n${match.content || ''}\n`
    ElMessage.success('知识内容已插入')
  }

  watch(
    () => currentSection.value?.id,
    () => {
      void loadKnowledgeMatches()
    },
    { immediate: true }
  )

  return {
    knowledgeMatches,
    loadKnowledgeMatches,
    handleInsertKnowledge
  }
}
