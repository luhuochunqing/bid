import { describe, expect, it } from 'vitest'

import {
  buildCatalog,
  classifyKind,
  collectNewSymbols,
  extractIdentifiers,
  extractSymbols,
  findDuplicates,
  formatReport,
  isGenericName,
  isPageVue,
  isSkippablePath,
  isSuppressed,
  jaccard,
  kindsCompatible,
  normalizeName,
  parseArgs,
  scoreCandidate,
  tokenizeName,
  reusePriority,
} from './check-duplicate-implementations.mjs'

const FORMAT_DATE_BODY = `
  const parsed = dayjs(input)
  if (!parsed.isValid()) return ''
  return parsed.format(pattern || 'YYYY-MM-DD HH:mm')
`

function makeSymbol(overrides) {
  return {
    name: 'formatDate',
    kind: 'util',
    file: 'src/utils/formatDate.js',
    line: 3,
    language: 'js',
    identifiers: extractIdentifiers(FORMAT_DATE_BODY),
    reuseOk: [],
    ...overrides,
  }
}

describe('check-duplicate-implementations helpers', () => {
  it('normalizes and tokenizes names across affixes', () => {
    expect(tokenizeName('useListPagination')).toEqual(['list', 'pagination'])
    expect(tokenizeName('DateFormatUtil')).toEqual(['date', 'format'])
    expect(normalizeName('formatDate')).toBe('formatdate')
    expect(normalizeName('dateFormat')).toBe('dateformat')
    expect(normalizeName('useListPagination')).toBe('listpagination')
  })

  it('treats short/common names as generic', () => {
    expect(isGenericName('format')).toBe(true)
    expect(isGenericName('handleSubmit')).toBe(true)
    expect(isGenericName('formatDate')).toBe(false)
    expect(isGenericName('useListPagination')).toBe(false)
  })

  it('classifies kinds from path and name', () => {
    expect(classifyKind('src/components/StatusTag.vue', 'StatusTag')).toBe('component')
    expect(classifyKind('src/composables/useListPagination.js', 'useListPagination')).toBe('hook')
    expect(classifyKind('src/api/modules/projects.js', 'fetchProject')).toBe('api')
    expect(classifyKind('src/utils/formatDateTime.js', 'formatDateTime')).toBe('util')
    expect(classifyKind('backend/src/main/java/com/xiyu/bid/project/service/ProjectService.java', 'findVisible')).toBe('service')
    expect(kindsCompatible('util', 'function')).toBe(true)
    expect(kindsCompatible('hook', 'util')).toBe(false)
    expect(kindsCompatible('api', 'service')).toBe(false)
  })

  it('skips tests, generated files, and view pages as reusable components', () => {
    expect(isSkippablePath('src/utils/formatDate.spec.js')).toBe(true)
    expect(isSkippablePath('backend/src/test/java/com/xiyu/bid/FooTest.java')).toBe(true)
    expect(isSkippablePath('src/utils/formatDate.js')).toBe(false)
    expect(isPageVue('src/views/Project/Detail.vue')).toBe(true)
    expect(isPageVue('src/views/Project/components/StatusBadge.vue')).toBe(false)
    expect(isPageVue('src/components/StatusTag.vue')).toBe(false)
  })

  it('computes jaccard similarity', () => {
    expect(jaccard(['date', 'format'], ['format', 'date'])).toBe(1)
    expect(jaccard(['list', 'pagination'], ['pagination'])).toBeCloseTo(0.5)
    expect(jaccard([], [])).toBe(0)
  })
})

describe('extractSymbols', () => {
  it('extracts JS exported functions and arrow utils', () => {
    const content = `
export function formatDate(input) {
  return dayjs(input).format('YYYY-MM-DD')
}
export const dateFormat = (input) => dayjs(input).format('YYYY-MM-DD')
export const API_BASE = '/api'
`
    const symbols = extractSymbols(content, 'src/utils/dates.js')
    expect(symbols.map((s) => s.name).sort()).toEqual(['dateFormat', 'formatDate'])
    expect(symbols.every((s) => s.kind === 'util')).toBe(true)
  })

  it('extracts Vue component name and skips page views', () => {
    const component = `
<template><span class="tag">ok</span></template>
<script setup>
defineOptions({ name: 'StatusTag' })
function unusedLocal() { return 1 }
</script>
`
    const page = `
<template><div>page</div></template>
<script setup>
export function helper() { return dayjs().format('YYYY') }
</script>
`
    const componentSymbols = extractSymbols(component, 'src/components/StatusTag.vue')
    expect(componentSymbols.some((s) => s.kind === 'component' && s.name === 'StatusTag')).toBe(true)

    const pageSymbols = extractSymbols(page, 'src/views/Project/Detail.vue')
    expect(pageSymbols.some((s) => s.kind === 'component')).toBe(false)
    expect(pageSymbols.some((s) => s.name === 'helper')).toBe(true)
  })

  it('extracts Java util/service methods and skips entities', () => {
    const util = `
public class DateFormatUtil {
  public static String formatDate(LocalDateTime input) {
    if (input == null) return "";
    return input.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
  }
}
`
    const entity = `
public class ProjectEntity {
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}
`
    const utilSymbols = extractSymbols(util, 'backend/src/main/java/com/xiyu/bid/common/util/DateFormatUtil.java')
    expect(utilSymbols.map((s) => s.name)).toEqual(expect.arrayContaining(['DateFormatUtil', 'formatDate']))

    const entitySymbols = extractSymbols(entity, 'backend/src/main/java/com/xiyu/bid/entity/ProjectEntity.java')
    expect(entitySymbols).toEqual([])
  })

  it('skips Java service getters and @Override methods', () => {
    const service = `
public class ProjectQueryService {
  public Project findVisible(Long id) {
    return projectRepository.findById(id).orElseThrow();
  }
  public String getName() { return name; }
  @Override
  public String toString() { return "x"; }
}
`
    const symbols = extractSymbols(service, 'backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java')
    expect(symbols.map((s) => s.name)).toEqual(expect.arrayContaining(['ProjectQueryService', 'findVisible']))
    expect(symbols.map((s) => s.name)).not.toEqual(expect.arrayContaining(['getName', 'toString']))
  })
})

describe('duplicate matching', () => {
  it('FAIL when a new util reuses an existing exact name', () => {
    const newbie = makeSymbol({
      file: 'src/views/Foo/helpers.js',
      line: 12,
    })
    const existing = makeSymbol({
      file: 'src/utils/formatDate.js',
      line: 3,
    })
    const findings = findDuplicates([newbie], [existing])
    expect(findings).toHaveLength(1)
    expect(findings[0].existingFile).toBe('src/utils/formatDate.js')
    expect(findings[0].reason).toContain('名称完全相同')
  })

  it('FAIL when names differ but tokens and body match (same capability)', () => {
    const bodyIds = extractIdentifiers(`
      const parsed = dayjs(inputValue)
      if (!parsed.isValid()) return fallbackLabel
      return parsed.tz(timezone).format(displayPattern)
    `)
    const newbie = makeSymbol({
      name: 'dateFormat',
      file: 'src/views/Foo/helpers.js',
      identifiers: bodyIds,
    })
    const existing = makeSymbol({
      name: 'formatDate',
      file: 'src/utils/formatDateTime.js',
      identifiers: bodyIds,
    })
    const scored = scoreCandidate(newbie, existing)
    expect(scored).not.toBeNull()
    expect(scored.reason).toMatch(/名称不同但能力相同|归一化/)
    expect(findDuplicates([newbie], [existing])).toHaveLength(1)
  })

  it('PASS when names are similar but implementations differ', () => {
    const newbie = makeSymbol({
      name: 'useProjectDetailTasks',
      kind: 'hook',
      file: 'src/composables/projectDetail/useProjectDetailTasks.js',
      identifiers: extractIdentifiers(`
        const taskRows = computed(() => props.project.tasks.filter(t => t.assigneeId === currentUser.id))
        const openTaskDrawer = (taskId) => emit('open-task', taskId)
        return { taskRows, openTaskDrawer }
      `),
    })
    const existing = makeSymbol({
      name: 'useProjectDetailTaskActions',
      kind: 'hook',
      file: 'src/composables/projectDetail/useProjectDetailTaskActions.js',
      identifiers: extractIdentifiers(`
        const submitReview = async (payload) => taskApi.review(payload.taskId, payload.decision)
        const rejectDelivery = async (deliverableId) => taskApi.reject(deliverableId)
        return { submitReview, rejectDelivery }
      `),
    })
    expect(scoreCandidate(newbie, existing)).toBeNull()
    expect(findDuplicates([newbie], [existing])).toHaveLength(0)
  })

  it('PASS when no reasonable reuse target exists', () => {
    const newbie = makeSymbol({
      name: 'buildBidDeadlineCard',
      kind: 'function',
      file: 'src/views/Workbench/deadlineCard.js',
      identifiers: extractIdentifiers(`
        const remainHours = dayjs(deadline).diff(dayjs(), 'hour')
        const tone = remainHours < 24 ? 'danger' : 'warning'
        return { remainHours, tone, label: projectName }
      `),
    })
    const existing = makeSymbol({
      name: 'formatUserLabel',
      kind: 'util',
      file: 'src/utils/formatUserLabel.js',
      identifiers: extractIdentifiers(`
        const displayName = user.realName || user.username || '未知用户'
        return deptName ? displayName + '（' + deptName + '）' : displayName
      `),
    })
    expect(findDuplicates([newbie], [existing])).toHaveLength(0)
  })

  it('does not flag two Java services that share a method name but not a body', () => {
    const newbie = makeSymbol({
      name: 'findVisible',
      kind: 'service',
      language: 'java',
      file: 'backend/src/main/java/com/xiyu/bid/tender/service/TenderQueryService.java',
      identifiers: extractIdentifiers(`
        return tenderRepository.findPublishedByOrg(orgId, pageable);
      `),
    })
    const existing = makeSymbol({
      name: 'findVisible',
      kind: 'service',
      language: 'java',
      file: 'backend/src/main/java/com/xiyu/bid/project/service/ProjectQueryService.java',
      identifiers: extractIdentifiers(`
        return projectAccessScopeService.filter(projectRepository.findAllByOwner(ownerId));
      `),
    })
    expect(scoreCandidate(newbie, existing)).toBeNull()
  })

  it('prefers src/utils over a view-local copy when names tie', () => {
    expect(reusePriority('src/utils/formatDate.js')).toBeLessThan(reusePriority('src/views/Foo/helpers.js'))
    const newbie = makeSymbol({ file: 'src/views/Bar/newDate.js', line: 1 })
    const findings = findDuplicates([newbie], [
      makeSymbol({ file: 'src/views/Knowledge/helpers.js', line: 8 }),
      makeSymbol({ file: 'src/utils/formatDate.js', line: 3 }),
    ])
    expect(findings).toHaveLength(1)
    expect(findings[0].existingFile).toBe('src/utils/formatDate.js')
  })

  it('honors REUSE-OK suppression pointing at the existing file', () => {
    const newbie = makeSymbol({
      file: 'src/views/Foo/helpers.js',
      reuseOk: ['src/utils/formatDate.js'],
    })
    const existing = makeSymbol({ file: 'src/utils/formatDate.js' })
    expect(isSuppressed(newbie, existing)).toBe(true)
    expect(findDuplicates([newbie], [existing])).toHaveLength(0)
  })
})

describe('collectNewSymbols and catalog', () => {
  it('treats symbols in added files as new, and only added names in modified files', () => {
    const addedContent = 'export function formatDate(input) { return dayjs(input).format("YYYY-MM-DD") }\n'
    const oldContent = 'export function keepOld() { return 1 }\n'
    const newContent = `${oldContent}export function formatDate(input) { return dayjs(input).format("YYYY-MM-DD") }\n`
    const files = {
      'src/utils/newDate.js': addedContent,
      'src/utils/old.js': newContent,
    }
    const baseFiles = {
      'src/utils/old.js': oldContent,
    }
    const newSymbols = collectNewSymbols(
      [
        { statusCode: 'A', path: 'src/utils/newDate.js' },
        { statusCode: 'M', path: 'src/utils/old.js' },
      ],
      (rel) => files[rel],
      (rel) => baseFiles[rel] || '',
    )
    expect(newSymbols.map((s) => `${s.file}:${s.name}`).sort()).toEqual([
      'src/utils/newDate.js:formatDate',
      'src/utils/old.js:formatDate',
    ])
  })

  it('builds a catalog that excludes the new symbols themselves', () => {
    const files = {
      'src/utils/formatDate.js': 'export function formatDate(input) { return dayjs(input).format("YYYY-MM-DD") }\n',
      'src/views/Foo/helpers.js': 'export function formatDate(input) { return dayjs(input).format("YYYY-MM-DD") }\n',
    }
    const newbie = extractSymbols(files['src/views/Foo/helpers.js'], 'src/views/Foo/helpers.js')
    const catalog = buildCatalog(Object.keys(files), (rel) => files[rel], newbie)
    expect(catalog.some((s) => s.file === 'src/utils/formatDate.js' && s.name === 'formatDate')).toBe(true)
    expect(catalog.some((s) => s.file === 'src/views/Foo/helpers.js' && s.name === 'formatDate')).toBe(false)
  })
})

describe('report and CLI args', () => {
  it('formats PASS and FAIL the way the gate prints them', () => {
    const pass = formatReport({ verdict: 'PASS', checked: 3, findings: [], skipped: false })
    expect(pass).toContain('PASS: 没有发现重复实现')
    const fail = formatReport({
      verdict: 'FAIL',
      checked: 1,
      skipped: false,
      findings: [{
        kind: 'util',
        name: 'formatDate',
        file: 'src/views/Foo/helpers.js',
        line: 12,
        existingFile: 'src/utils/formatDateTime.js',
        existingName: 'formatDateTime',
        existingLine: 3,
        reason: '名称不同但能力相同',
        score: 0.75,
      }],
    })
    expect(fail).toContain('FAIL: 发现 1 处重复实现')
    expect(fail).toContain('应复用: src/utils/formatDateTime.js:3 (formatDateTime)')
    expect(fail).toContain('REUSE-OK:')
  })

  it('parses --base --head --json', () => {
    expect(parseArgs(['--base', 'origin/main', '--head', 'HEAD', '--json'])).toEqual({
      base: 'origin/main',
      head: 'HEAD',
      json: true,
    })
  })
})
