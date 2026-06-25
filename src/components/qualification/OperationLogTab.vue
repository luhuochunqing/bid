<template>
  <div class="op-log-tab" v-loading="loading" data-testid="qd-op-log-tab">
    <!-- 时间范围筛选 -->
    <div class="op-log-filter">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="—"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        size="small"
        style="width: 240px"
        @change="handleDateChange"
      />
      <el-button size="small" @click="resetFilter">重置</el-button>
    </div>

    <!-- 日志表格 -->
    <el-table v-if="filteredLogs.length" :data="filteredLogs" size="small" border class="op-log-table">
      <el-table-column prop="time" label="时间" width="160" />
      <el-table-column prop="operator" label="操作人" width="120">
        <template #default="scope">
          {{ formatOperator(scope.row) }}
        </template>
      </el-table-column>
      <el-table-column label="操作类型" width="100">
        <template #default="scope">
          <el-tag :type="tagType(scope.row.actionType)" size="small">
            {{ actionLabel(scope.row.actionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="变更详情" show-overflow-tooltip />
      <el-table-column prop="target" label="目标对象" width="140" show-overflow-tooltip>
        <template #default="scope">
          {{ scope.row.target && scope.row.target !== '-' ? scope.row.target : '—' }}
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-else-if="!loading" description="暂无操作记录" :image-size="80" data-testid="qd-op-log-empty" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import auditApi from '@/api/modules/audit.js'

const props = defineProps({
  qualificationId: { type: [String, Number], default: null }
})

const loading = ref(false)
const logs = ref([])
const dateRange = ref(null)

const ACTION_LABELS = {
  create: '新增',
  update: '修改',
  delete: '删除',
  import: '批量导入',
  export: '导出',
  borrow: '借阅',
  return: '归还',
  approve: '审批通过',
  reject: '审批拒绝',
  archive: '归档',
  submit: '提交',
  withdraw: '撤回',
  verify: '审核',
  claim: '认领',
  assign: '分配',
  resolve: '处理',
  cancel: '取消',
  pay: '支付',
  regenerate: '重新生成',
  assemble: '组装',
  login: '登录',
  logout: '登出',
  reviewed: '审核',
  closed: '关闭',
  submitted: '提交',
  transitioned: '状态流转',
  changed: '变更',
  registered: '登记',
  view_password: '查看密码',
  attachment_change: '附件变更'
}

const actionLabel = (a) => ACTION_LABELS[String(a || '').toLowerCase()] || a || '操作'

const tagType = (actionType) => {
  const a = String(actionType || '').toLowerCase()
  if (a === 'create' || a === 'approve' || a === 'submit') return 'success'
  if (a === 'delete' || a === 'reject' || a === 'withdraw' || a === 'cancel') return 'danger'
  if (a === 'update' || a === 'import' || a === 'export' || a === 'attachment_change') return 'primary'
  if (a === 'borrow') return 'warning'
  return 'info'
}

const formatOperator = (log) => {
  // 后端已返回 "姓名（工号）" 格式，直接透传；兜底显示
  if (log?.operator && log.operator.includes('（') && log.operator.includes('）')) {
    return log.operator
  }
  const name = log?.operator || '未知用户'
  const role = log?.role && log.role !== 'unknown' ? `（${log.role}）` : ''
  return `${name}${role}`
}

const filteredLogs = computed(() => {
  if (!dateRange.value || !dateRange.value.length) return logs.value
  const [start, end] = dateRange.value
  return logs.value.filter((log) => {
    const t = log.time?.slice(0, 10)
    return t && t >= start && t <= end
  })
})

const handleDateChange = () => {
  // 筛选由 computed 自动处理
}

const resetFilter = () => {
  dateRange.value = null
}

const loadLogs = async () => {
  if (!props.qualificationId) {
    logs.value = []
    return
  }
  loading.value = true
  try {
    const res = await auditApi.getQualificationLogs(props.qualificationId)
    const payload = res?.data
    const list = Array.isArray(payload)
      ? payload
      : (payload?.items || payload?.logs || payload?.list || [])
    logs.value = Array.isArray(list) ? list : []
  } catch (err) {
    ElMessage.warning('操作日志加载失败')
    logs.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.qualificationId, (id) => {
  if (id != null) loadLogs()
}, { immediate: true })
</script>

<style scoped lang="scss">
.op-log-tab {
  padding: 4px 0 16px;
}
.op-log-filter {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.op-log-table {
  margin-top: 4px;
}
</style>
