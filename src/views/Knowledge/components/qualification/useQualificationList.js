// Input: http client
// Output: 资质证书列表的状态、筛选、分页、fetch、状态标签辅助函数
// Pos: src/views/Knowledge/components/qualification/ - 从 Qualification.vue 抽取的列表逻辑
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/api/client'
import { ApiCode } from '@/constants/apiCode'

const STATUS_OPTIONS = [
  { label: '在库', value: 'VALID' },
  { label: '即将到期', value: 'EXPIRING' },
  { label: '已过期', value: 'EXPIRED' },
  { label: '已下架', value: 'RETIRED' }
]

const STATUS_LABELS = {
  in_stock: '在库',
  valid: '在库',
  expiring: '即将到期',
  expired: '已过期',
  retired: '已下架'
}

export function useQualificationList() {
  const qualifications = ref([])
  const loading = ref(false)
  const page = ref(1)
  const pageSize = ref(15)
  const total = ref(0)

  // filters: 初始无默认筛选，空状态显示全部
  const filters = reactive({
    keyword: '',
    issuer: '',
    expiryRange: null,
    statuses: [],
    level: ''
  })

  const hasFilterActive = computed(() =>
    Boolean(filters.keyword || filters.issuer || filters.expiryRange || filters.statuses.length || filters.level)
  )

  const levelOptions = computed(() => {
    const levels = new Set()
    qualifications.value.forEach((item) => { if (item.level) levels.add(item.level) })
    return Array.from(levels).sort()
  })

  const fetchQualifications = async () => {
    loading.value = true
    try {
      const q = new URLSearchParams()
      if (filters.keyword) q.set('keyword', filters.keyword)
      if (filters.issuer) q.set('issuer', filters.issuer)
      if (filters.expiryRange) {
        q.set('expiringFrom', filters.expiryRange[0])
        q.set('expiringTo', filters.expiryRange[1])
      }
      if (filters.statuses.length) filters.statuses.forEach((s) => q.append('status', s))
      if (filters.level) q.set('level', filters.level)
      // CO-155 fix: 前端 page 从 1 开始 → 后端从 0 开始
      q.set('page', page.value - 1)
      q.set('size', pageSize.value)
      const body = await http.get(`/api/knowledge/qualifications?${q.toString()}`)
      if (body?.code === ApiCode.SUCCESS) {
        // CO-155 fix: 后端现在返回 Page<DTO>，有 content/totalElements 字段
        const data = body.data
        if (data && Array.isArray(data.content)) {
          qualifications.value = data.content
          total.value = data.totalElements ?? data.content.length
        } else {
          // 兜底：老接口返回 List<DTO>
          qualifications.value = Array.isArray(data) ? data : []
          total.value = qualifications.value.length
        }
      }
    } catch {
      ElMessage.error('加载失败')
    } finally {
      loading.value = false
    }
  }

  const resetFilters = () => {
    Object.assign(filters, { keyword: '', issuer: '', expiryRange: null, statuses: [], level: '' })
    page.value = 1
    fetchQualifications()
  }

  const getStatusTagType = (row) => {
    const s = (row.status || '').toLowerCase()
    if (s === 'in_stock' || s === 'valid') return 'success'
    if (s === 'expiring') return 'warning'
    if (s === 'expired') return 'danger'
    return 'info'
  }

  const statusLabel = (s) => STATUS_LABELS[(s || '').toLowerCase()] || s || '—'

  onMounted(async () => {
    await fetchQualifications()
  })

  return {
    qualifications,
    loading,
    page,
    pageSize,
    total,
    filters,
    statusOptions: STATUS_OPTIONS,
    hasFilterActive,
    levelOptions,
    fetchQualifications,
    resetFilters,
    getStatusTagType,
    statusLabel
  }
}
