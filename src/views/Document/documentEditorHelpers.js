function asText(value) {
  return String(value ?? '').trim()
}

function safeJsonParse(value, fallback = {}) {
  if (!value) return { ...fallback }
  if (typeof value === 'object') return { ...fallback, ...value }

  try {
    const parsed = JSON.parse(value)
    if (parsed && typeof parsed === 'object') {
      return { ...fallback, ...parsed }
    }
  } catch {
    return { ...fallback }
  }

  return { ...fallback }
}

function findSectionById(sections, id) {
  for (const section of sections || []) {
    if (String(section.id) === String(id)) {
      return section
    }
    if (Array.isArray(section.children) && section.children.length > 0) {
      const found = findSectionById(section.children, id)
      if (found) return found
    }
  }
  return null
}

function classifySection(section = {}) {
  const id = asText(section.id)
  const name = asText(section.name || section.title)
  const lowerName = name.toLowerCase()

  if (id.startsWith('3') || /案例/.test(name) || /case/.test(lowerName)) {
    return 'cases'
  }
  if (/资质|证书|资格|qualification/.test(name) || id.startsWith('2.1')) {
    return 'qualification'
  }
  if (/服务|售后|交付|delivery/.test(name)) {
    return 'service'
  }
  if (/商务|报价|commercial/.test(name)) {
    return 'commercial'
  }
  return 'technical'
}

export function buildKnowledgeQuery(section = {}, documentInfo = {}) {
  const keywordParts = [
    documentInfo.name || documentInfo.templateName || documentInfo.title,
    section.name || section.title,
    section.id
  ]
    .map(asText)
    .filter(Boolean)

  return {
    keyword: keywordParts.join(' '),
    category: classifySection(section)
  }
}

export function parseSectionMetadata(metadata) {
  return safeJsonParse(metadata)
}

export function mergeSectionSourceMetadata(section, sourceEntry) {
  if (!section) return {}

  const metadata = parseSectionMetadata(section.metadata)
  const sources = Array.isArray(metadata.sources) ? [...metadata.sources] : []

  if (sourceEntry && typeof sourceEntry === 'object') {
    sources.push({ ...sourceEntry })
  }

  const nextMetadata = {
    ...metadata,
    sources
  }

  section.metadata = JSON.stringify(nextMetadata)
  return nextMetadata
}

export function buildReferencePayload(match = {}, currentUser = {}, section = {}, projectInfo = {}) {
  const userName = currentUser?.name || currentUser?.fullName || currentUser?.username || '当前用户'

  return {
    referencedBy: currentUser?.id ?? null,
    referencedByName: userName,
    referenceTarget: section?.name || match?.title || projectInfo?.name || '文档编辑器',
    referenceContext: `文档编辑器插入案例：${section?.name || '未命名章节'}`
  }
}

export function normalizeKnowledgeMatch(match = {}) {
  return {
    id: match.id,
    type: match.type,
    title: match.title || '未命名知识项',
    summary: match.summary || '',
    content: match.content || '',
    relevance: Number(match.relevance || 0),
    sourceLabel: match.sourceLabel || (match.type === 'case' ? '案例库' : '模板库'),
    sourceDetail: match.sourceDetail || '',
    raw: match
  }
}

export function buildAssemblyVariables({
  projectInfo = {},
  documentInfo = {},
  template = {},
  selectedSections = [],
  knowledgeMatches = {}
} = {}) {
  const sectionSummaries = selectedSections.map((section) => {
    const topMatch = (knowledgeMatches[section.id] || [])[0] || null

    return {
      id: section.id,
      name: section.name,
      topMatchTitle: topMatch?.title || '',
      topMatchSummary: topMatch?.summary || '',
      topMatchType: topMatch?.type || ''
    }
  })

  return JSON.stringify({
    projectId: projectInfo?.id ?? '',
    projectName: projectInfo?.name || '',
    documentTemplateId: documentInfo?.templateId || '',
    documentTemplateName: documentInfo?.templateName || '',
    templateId: template?.id ?? '',
    templateName: template?.name || '',
    sectionIds: selectedSections.map((section) => section.id),
    sectionNames: selectedSections.map((section) => section.name),
    sectionSummaries
  })
}

export function applyAssemblyContentToSections(
  sections = [],
  selectedSectionIds = [],
  assembledContent = '',
  templateName = '文档组装'
) {
  const filledIds = []
  const nextContent = assembledContent || ''
  const sourceEntry = {
    kind: 'assembly',
    title: templateName,
    sourceLabel: '文档组装',
    sourceDetail: templateName
  }

  selectedSectionIds.map(String).forEach((sectionId) => {
    const section = findSectionById(sections, sectionId)
    if (!section) return

    const heading = section.name ? `## ${section.name}\n\n` : ''
    const sourceNote = `\n\n> 组装来源：${templateName}`
    section.content = `${heading}${nextContent}${sourceNote}`.trim()
    mergeSectionSourceMetadata(section, sourceEntry)
    filledIds.push(sectionId)
  })

  return filledIds
}
