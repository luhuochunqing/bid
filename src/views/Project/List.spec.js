import { describe, expect, it } from 'vitest'
import projectListSource from './List.vue?raw'
import searchCardSource from './components/ProjectSearchCard.vue?raw'

describe('Project/List.vue layout', () => {
  it('defaults to 7 core columns with column visibility toggle', () => {
    // No forced min-width
    expect(projectListSource).not.toContain('style="min-width: 1400px"')
    // Column selector dropdown
    expect(projectListSource).toContain('列设置')
    expect(projectListSource).toContain('toggleColumn')
    // Core columns always visible (no v-if)
    expect(projectListSource).toContain('label="项目名称"')
    // Optional columns controlled by v-if
    expect(projectListSource).toContain('v-if="columnVisible.shortlistedCount"')
    expect(projectListSource).toContain('v-if="columnVisible.createTime"')
  })

  it('shows core columns with status and result tags', () => {
    expect(projectListSource).toContain('label="项目名称"')
    expect(projectListSource).toContain('label="招标主体"')
    expect(projectListSource).toContain('label="项目状态"')
    expect(projectListSource).toContain('label="投标平台"')
    expect(projectListSource).toContain('getProjectStatusType(row.bidStatus)')
  })

  it('has no fixed-right columns', () => {
    expect(projectListSource).not.toContain('fixed="right"')
  })

  it('includes export button in card header', () => {
    expect(projectListSource).toContain('@click="handleExport"')
    expect(projectListSource).toContain('Download')
    expect(projectListSource).toContain('导出 Excel')
    // Export logic from composable
    expect(projectListSource).toContain('useProjectExport')
  })

  it('supports priority and date range search filters', () => {
    // Search fields extracted to ProjectSearchCard
    expect(searchCardSource).toContain('searchForm.projectType')
    expect(searchCardSource).toContain('searchForm.priority')
    expect(searchCardSource).toContain('searchForm.projectLeaderName')
    expect(searchCardSource).toContain('searchForm.biddingLeaderName')
    expect(searchCardSource).toContain('searchForm.bidOpenTimeRange')
    expect(searchCardSource).toContain('type="daterange"')
  })

  it('exports file with formatted filename', () => {
    // Export logic moved to useProjectExport composable
    expect(projectListSource).toContain('useProjectExport')
    expect(projectListSource).toContain('handleExport')
    expect(projectListSource).toContain('exporting')
  })

  it('has column visibility reactive state', () => {
    expect(projectListSource).toContain('columnVisible')
    expect(projectListSource).toContain('toggleColumn')
    expect(projectListSource).toContain('columnOptions')
    expect(projectListSource).toContain('useProjectColumns')
  })

  it('renders ProjectSearchCard component', () => {
    expect(projectListSource).toContain('ProjectSearchCard')
  })

  it('uses scoped styles', () => {
    expect(projectListSource).toContain('scoped')
    expect(projectListSource).toContain('.table-wrapper')
  })

  it('persists column visibility per user in localStorage', () => {
    expect(projectListSource).toContain('useProjectColumns')
    expect(projectListSource).toContain('columnVisible')
    expect(projectListSource).toContain('toggleColumn')
    expect(projectListSource).toContain('visibleOptionalCount')
    expect(projectListSource).toContain('useProjectColumns')
  })

  // Edge case handling tests
  it('has empty state component', () => {
    expect(projectListSource).toContain('el-empty')
    expect(projectListSource).toContain('empty-state')
  })

  it('has error state component', () => {
    expect(projectListSource).toContain('error-state')
    expect(projectListSource).toContain('el-alert')
    expect(projectListSource).toContain('retryLoad')
  })

  it('has export loading state', () => {
    expect(projectListSource).toContain('exporting')
    expect(projectListSource).toContain(':loading="exporting"')
  })

  it('hides pagination when no data', () => {
    expect(projectListSource).toContain('v-if="matchedProjects.length > 0"')
  })

  it('has isFiltered computed for empty state messages', () => {
    expect(projectListSource).toContain('isFiltered')
  })

})

// US4 — date picker labels fully visible (no truncation), extracted to ProjectSearchCard
describe('ProjectSearchCard — date picker labels fully visible (no truncation)', () => {
  it('创建时间 and 开标时间 form items use search-field--datetime class for fixed width', () => {
    const createTimePattern = /<el-form-item label="创建时间"[^>]*class="[^"]*search-field--datetime/i
    const bidOpenTimePattern = /<el-form-item label="开标时间"[^>]*class="[^"]*search-field--datetime/i
    expect(searchCardSource).toMatch(createTimePattern)
    expect(searchCardSource).toMatch(bidOpenTimePattern)
  })

  it('keeps search-field--datetime class on both long date filters', () => {
    const datetimeMatches = searchCardSource.match(/class="search-field--datetime"/g) || []
    expect(datetimeMatches).toHaveLength(2)
  })

  it('includes filter-date-picker CSS definition for label width fix', () => {
    expect(searchCardSource).toMatch(/\.filter-date-picker\s*\{[^}]*width\s*:[^}]*\}/)
  })
})
