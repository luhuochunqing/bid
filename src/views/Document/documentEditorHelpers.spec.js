import { describe, expect, it } from 'vitest'
import {
  applyAssemblyContentToSections,
  buildKnowledgeQuery,
  mergeSectionSourceMetadata
} from './documentEditorHelpers.js'

describe('documentEditorHelpers', () => {
  it('buildKnowledgeQuery infers the category from the section context', () => {
    expect(buildKnowledgeQuery({ id: '3.1', name: '案例展示' }, { name: '智慧城市IOC项目' })).toEqual({
      keyword: '智慧城市IOC项目 案例展示 3.1',
      category: 'cases'
    })
  })

  it('buildKnowledgeQuery falls back to title fields from backend editor payloads', () => {
    expect(buildKnowledgeQuery({ id: 29, title: '技术说明' }, { templateName: 'DBG 文档结构 985548' })).toEqual({
      keyword: 'DBG 文档结构 985548 技术说明 29',
      category: 'technical'
    })
  })

  it('mergeSectionSourceMetadata preserves existing metadata and appends sources', () => {
    const section = {
      metadata: JSON.stringify({
        note: 'keep-me',
        sources: [{ kind: 'template', title: '旧来源' }]
      })
    }

    mergeSectionSourceMetadata(section, {
      kind: 'case',
      title: '上海XX智慧城市IOC项目',
      referencedAt: '2026-04-19 10:00:00'
    })

    expect(JSON.parse(section.metadata)).toEqual({
      note: 'keep-me',
      sources: [
        { kind: 'template', title: '旧来源' },
        {
          kind: 'case',
          title: '上海XX智慧城市IOC项目',
          referencedAt: '2026-04-19 10:00:00'
        }
      ]
    })
  })

  it('applyAssemblyContentToSections writes assembled content back into the selected sections', () => {
    const sections = [
      { id: '1.1', name: '项目背景', content: 'old background' },
      { id: '1.2', name: '需求分析', content: 'old requirements' }
    ]

    const filledIds = applyAssemblyContentToSections(sections, ['1.1', '1.2'], 'assembled-body', '智慧城市模板')

    expect(filledIds).toEqual(['1.1', '1.2'])
    expect(sections[0].content).toContain('assembled-body')
    expect(sections[0].content).toContain('智慧城市模板')
    expect(JSON.parse(sections[0].metadata).sources[0]).toMatchObject({
      kind: 'assembly',
      title: '智慧城市模板'
    })
  })
})
