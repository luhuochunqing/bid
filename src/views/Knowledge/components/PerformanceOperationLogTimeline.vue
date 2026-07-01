<template>
  <div class="perf-log-timeline" v-loading="loading">
    <el-timeline v-if="logs.length > 0">
      <el-timeline-item
        v-for="log in logs"
        :key="log.id"
        :timestamp="log.time"
        :type="logType(log.actionType)"
      >
        <h4>{{ actionLabel(log.actionType) }}</h4>
        <p>{{ formatOperator(log) }}{{ log.detail ? ' ' + log.detail : '' }}</p>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="暂无操作记录" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import auditApi from '@/api/modules/audit.js'

const props = defineProps({
  performanceId: { type: [Number, String], default: null },
  // 触发加载的外部信号（父组件 tab 切换时传入）
  loadTrigger: { type: Boolean, default: false }
})

const logs = ref([])
const loading = ref(false)

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

const logType = (actionType) => {
  const a = String(actionType || '').toLowerCase()
  if (a === 'create' || a === 'approve' || a === 'submit') return 'success'
  if (a === 'delete' || a === 'reject' || a === 'withdraw' || a === 'cancel') return 'danger'
  if (a === 'update' || a === 'import' || a === 'export' || a === 'attachment_change') return 'primary'
  if (a === 'borrow') return 'warning'
  return 'info'
}

const formatOperator = (log) => {
  if (log?.operator && log.operator.includes('（') && log.operator.includes('）')) {
    return log.operator
  }
  const name = log?.operator || '未知用户'
  const role = log?.role && log.role !== 'unknown' ? `（${log.role}）` : ''
  return `${name}${role}`
}

const loadLogs = async () => {
  if (!props.performanceId) {
    logs.value = []
    return
  }
  loading.value = true
  try {
    // CO-440: 改用业绩专属审计端点，替代通用 /api/audit（hasAnyRole('ADMIN','AUDITOR')）一刀切拦截
    const res = await auditApi.getPerformanceLogs(props.performanceId)
    const payload = res?.data
    const list = Array.isArray(payload)
      ? payload
      : (payload?.items || payload?.logs || payload?.list || [])
    logs.value = Array.isArray(list) ? list : []
  } catch (e) {
    ElMessage.warning('操作日志加载失败')
    logs.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.loadTrigger, (v) => {
  if (v && props.performanceId) loadLogs()
})

watch(() => props.performanceId, (id) => {
  if (!id) logs.value = []
})

defineExpose({ loadLogs })
</script>

<style scoped lang="scss">
.perf-log-timeline {
  padding: 12px 24px;
  h4 {
    margin: 0 0 4px 0;
    font-size: 14px;
    font-weight: 600;
  }
  p {
    margin: 0;
    font-size: 13px;
    color: var(--el-text-color-regular);
  }
}
</style>
