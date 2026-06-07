<template>
  <div class="op-log-tab" v-loading="loading" data-testid="qd-op-log-tab">
    <el-timeline v-if="logs.length" class="op-log-tab__timeline">
      <el-timeline-item
        v-for="log in logs"
        :key="log.id"
        :timestamp="log.time"
        :type="timelineType(log.actionType)"
        placement="top"
        data-testid="qd-op-log-item"
      >
        <div class="op-log-tab__row">
          <span class="op-log-tab__operator" data-testid="qd-op-log-operator">
            {{ formatOperator(log) }}
          </span>
          <span class="op-log-tab__action" :class="`op-log-tab__action--${log.actionType}`">
            {{ actionLabel(log.actionType) }}
          </span>
        </div>
        <div v-if="log.detail" class="op-log-tab__detail" data-testid="qd-op-log-detail">
          {{ log.detail }}
        </div>
        <div v-if="log.target && log.target !== '-'" class="op-log-tab__target">
          目标：{{ log.target }}
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else-if="!loading" description="暂无操作记录" :image-size="80" data-testid="qd-op-log-empty" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import auditApi from '@/api/modules/audit.js'

const props = defineProps({
  qualificationId: { type: [String, Number], default: null }
})

const loading = ref(false)
const logs = ref([])

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

const timelineType = (actionType) => {
  const a = String(actionType || '').toLowerCase()
  if (a === 'create') return 'success'
  if (a === 'delete') return 'danger'
  if (a === 'update' || a === 'import' || a === 'export' || a === 'attachment_change') return 'primary'
  if (a === 'approve' || a === 'submit' || a === 'verify') return 'success'
  if (a === 'reject' || a === 'withdraw' || a === 'cancel') return 'danger'
  return 'info'
}

const formatOperator = (log) => {
  const name = log?.operator || '未知用户'
  const role = log?.role && log.role !== 'unknown' ? `（${log.role}）` : ''
  return `${name}${role}`
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
  :deep(.el-timeline) { padding-left: 0; }
}
.op-log-tab__timeline { margin-top: 4px; }
.op-log-tab__row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.op-log-tab__operator { font-weight: 600; color: #1f2937; }
.op-log-tab__action {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
  &--create, &--approve, &--submit { background: #ecfdf5; color: #047857; }
  &--update, &--import, &--export, &--attachment_change { background: #eff6ff; color: #1d4ed8; }
  &--delete, &--reject, &--withdraw, &--cancel { background: #fef2f2; color: #b91c1c; }
  &--borrow { background: #f5f3ff; color: #6d28d9; }
  &--return { background: #f0f9ff; color: #0369a1; }
  background: #f3f4f6;
  color: #4b5563;
}
.op-log-tab__detail { font-size: 13px; color: #374151; margin-top: 4px; line-height: 1.6; word-break: break-word; }
.op-log-tab__target { font-size: 12px; color: #6b7280; margin-top: 4px; }
</style>
