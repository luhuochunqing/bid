import { describe, expect, it } from 'vitest'

import {
  classifyBudgetChange,
  countLines,
  isGuardedPath,
  loadConfig,
  parseNameStatusLine,
} from './check-line-budgets.mjs'

const config = loadConfig()

describe('check-line-budgets', () => {
  it('guards core source paths and skips excluded files', () => {
    expect(isGuardedPath('src/views/Project/Detail.vue', config)).toBe(true)
    expect(isGuardedPath('backend/src/main/java/com/xiyu/bid/task/service/TaskService.java', config)).toBe(true)
    expect(isGuardedPath('src/api/modules/projects.spec.js', config)).toBe(false)
    expect(isGuardedPath('backend/src/test/java/com/xiyu/bid/TaskServiceTest.java', config)).toBe(false)
    expect(isGuardedPath('scripts/dev-services.sh', config)).toBe(false)
  })

  it('classifies ratchet violations correctly', () => {
    expect(classifyBudgetChange({ maxLines: 300, oldLines: null, newLines: 301 })).toBe('NEW_OVER_LIMIT')
    expect(classifyBudgetChange({ maxLines: 300, oldLines: 300, newLines: 301 })).toBe('CROSSED_LIMIT')
    expect(classifyBudgetChange({ maxLines: 300, oldLines: 420, newLines: 421 })).toBe('GREW_WHILE_OVER_LIMIT')
    expect(classifyBudgetChange({ maxLines: 300, oldLines: 420, newLines: 400 })).toBeNull()
    expect(classifyBudgetChange({ maxLines: 300, oldLines: 200, newLines: 300 })).toBeNull()
  })

  it('counts trailing-newline files without an off-by-one error', () => {
    expect(countLines('')).toBe(0)
    expect(countLines('alpha')).toBe(1)
    expect(countLines('alpha\nbeta')).toBe(2)
    expect(countLines('alpha\nbeta\n')).toBe(2)
  })

  it('parses rename and modify diff lines', () => {
    expect(parseNameStatusLine('M\tsrc/views/Project/Detail.vue')).toEqual({
      statusCode: 'M',
      oldPath: 'src/views/Project/Detail.vue',
      newPath: 'src/views/Project/Detail.vue',
    })
    expect(parseNameStatusLine('R100\tsrc/views/Old.vue\tsrc/views/New.vue')).toEqual({
      statusCode: 'R',
      oldPath: 'src/views/Old.vue',
      newPath: 'src/views/New.vue',
    })
    expect(parseNameStatusLine('C100\tsrc/api/modules/old.js\tsrc/api/modules/new.js')).toEqual({
      statusCode: 'C',
      oldPath: null,
      newPath: 'src/api/modules/new.js',
    })
  })
})
