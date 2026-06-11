import { reactive, computed } from 'vue'
import { useUserStore } from '@/stores/user'

const COLUMN_STORAGE_KEY = 'projectListColumnVisible'

const columnOptions = [
  { key: 'bidOpenTime', label: '开标时间' },
  { key: 'biddingPlatform', label: '投标平台' },
  { key: 'shortlistedCount', label: '计划入围供应商数量' },
  { key: 'createTime', label: '创建时间' },
  { key: 'bidMonth', label: '投标月份' },
  { key: 'projectType', label: '项目类型' },
  { key: 'customerType', label: '客户类型' },
  { key: 'priority', label: '优先级' },
  { key: 'sourceModule', label: '来源平台' },
  { key: 'stage', label: '项目阶段' },
  { key: 'revenue', label: '客户营收（亿）' },
  { key: 'region', label: '总部所在地' },
  { key: 'leaderDepartment', label: '项目负责人部门' },
  { key: 'biddingLeaderName', label: '投标负责人' },
]

const ALL_COLUMNS_VISIBLE = Object.fromEntries(columnOptions.map(c => [c.key, true]))

function loadColumnVisible(userStore) {
  try {
    const uid = userStore.currentUser?.id || 'default'
    const raw = localStorage.getItem(COLUMN_STORAGE_KEY + ':' + uid)
    if (raw) {
      const loaded = JSON.parse(raw)
      const validKeys = new Set(columnOptions.map((o) => o.key))
      Object.keys(loaded).forEach((k) => { if (!validKeys.has(k)) delete loaded[k] })
      return loaded
    }
  } catch (_) { /* ignore */ }
  return { ...ALL_COLUMNS_VISIBLE }
}

function saveColumnVisible(columnVisible, userStore) {
  try {
    const uid = userStore.currentUser?.id || 'default'
    localStorage.setItem(COLUMN_STORAGE_KEY + ':' + uid, JSON.stringify(columnVisible))
  } catch (_) { /* ignore */ }
}

export function useProjectColumns() {
  const userStore = useUserStore()
  const columnVisible = reactive(loadColumnVisible(userStore))

  function toggleColumn(key) {
    columnVisible[key] = !columnVisible[key]
    saveColumnVisible(columnVisible, userStore)
  }

  const visibleOptionalCount = computed(() =>
    columnOptions.filter(c => columnVisible[c.key]).length
  )

  return { columnVisible, columnOptions, visibleOptionalCount, toggleColumn }
}
