import { describe, expect, it } from 'vitest'
import { findSectionPathByChapter } from './sectionTreeMatch.js'

describe('findSectionPathByChapter', () => {
  const tree = [
    {
      label: '第1章 商务标响应',
      children: [{ label: '1.1 投标承诺书' }, { label: '1.2 资格证明材料' }]
    },
    { label: '第2章 技术服务方案', children: [{ label: '2.1 系统总体架构设计' }] }
  ]

  it('matches exact leaf label and returns full path', () => {
    expect(findSectionPathByChapter(tree, '1.2 资格证明材料')).toEqual(['第1章 商务标响应', '1.2 资格证明材料'])
  })

  it('matches top-level node label', () => {
    expect(findSectionPathByChapter(tree, '第1章 商务标响应')).toEqual(['第1章 商务标响应'])
  })

  it('matches full joined path', () => {
    expect(findSectionPathByChapter(tree, '第2章 技术服务方案 / 2.1 系统总体架构设计')).toEqual(['第2章 技术服务方案', '2.1 系统总体架构设计'])
  })

  it('falls back to longest containment match', () => {
    expect(findSectionPathByChapter(tree, '第2章 技术服务方案（初稿）')).toEqual(['第2章 技术服务方案'])
  })

  it('supports value-only nodes', () => {
    expect(findSectionPathByChapter([{ value: '3.1 概述' }], '3.1 概述')).toEqual(['3.1 概述'])
  })

  it('returns empty array when nothing matches or input is empty', () => {
    expect(findSectionPathByChapter(tree, '完全无关')).toEqual([])
    expect(findSectionPathByChapter(tree, '')).toEqual([])
    expect(findSectionPathByChapter(tree, '   ')).toEqual([])
    expect(findSectionPathByChapter([], '1.1')).toEqual([])
    expect(findSectionPathByChapter(null, '1.1')).toEqual([])
  })
})
