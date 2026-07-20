import { describe, expect, it } from 'vitest'

import {
  SECTION_PATTERN,
  getOriginMainMaxSection,
  getOriginMainSectionSet,
} from './check-lessons-section-conflict.mjs'

describe('check-lessons-section-conflict', () => {
  describe('SECTION_PATTERN', () => {
    it('matches valid section headers', () => {
      expect('## 73. Review PR 必须...'.match(SECTION_PATTERN)).not.toBeNull()
      expect('## 1. 第一条'.match(SECTION_PATTERN)).not.toBeNull()
      expect('## 999. 最大编号'.match(SECTION_PATTERN)).not.toBeNull()
      // extract the number
      expect('## 73. Review PR'.match(SECTION_PATTERN)[1]).toBe('73')
    })

    it('rejects non-section lines', () => {
      expect('### 73. 三级标题'.match(SECTION_PATTERN)).toBeNull()
      expect('## 73'.match(SECTION_PATTERN)).toBeNull()
      expect('## 标题无编号.'.match(SECTION_PATTERN)).toBeNull()
      expect('普通文本 ## 73.'.match(SECTION_PATTERN)).toBeNull()
      expect('## 73a. 不是纯数字'.match(SECTION_PATTERN)).toBeNull()
    })
  })

  describe('getOriginMainMaxSection / getOriginMainSectionSet', () => {
    it('returns number / Set without throwing (integration smoke)', () => {
      // In test environment, git show origin/main:... likely fails gracefully
      // (returns 0 / empty set). Just verify no throw + correct types.
      expect(typeof getOriginMainMaxSection()).toBe('number')
      expect(getOriginMainSectionSet()).toBeInstanceOf(Set)
    })
  })
})
