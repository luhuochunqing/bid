<template>
  <el-dialog v-model="visible" title="账户详情" width="680px">
    <el-tabs v-model="activeTab" v-if="data">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="平台名称" :span="2">{{ data.accountName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ data.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="平台账号">{{ data.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="平台密码">
            <div class="password-cell">
              <span class="password-text">{{ password.displayText(data.id) }}</span>
              <el-button
                size="small"
                link
                :disabled="password.isLoading(data.id)"
                @click="password.toggle(data.id)">
                <el-icon>
                  <component :is="password.isVisible(data.id) ? Hide : View" />
                </el-icon>
              </el-button>
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag v-if="data.status === 'available'" type="success">可用</el-tag>
            <el-tag v-else-if="data.status === 'in_use'" type="warning">使用中</el-tag>
            <el-tag v-else type="info">禁用</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="账号保管员">{{ data.contactPersonLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否有 CA">{{ data.hasCa ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="使用人">{{ data.borrower || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ data.remarks || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册人">{{ data.registrant || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册手机">{{ data.registerPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="注册邮箱">{{ data.registerEmail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近使用">{{ data.lastUsed || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归还截止"><DateTimeDisplay :value="data.dueAt" /></el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="借用记录" name="borrows">
        <el-table :data="borrowRecords" stripe size="small" max-height="360" v-loading="borrowLoading">
          <el-table-column label="申请人" width="100">
            <template #default="{ row }">
              {{ row.applicantName || '未知' }}
              <span v-if="row.applicantEmployeeNo" style="color: var(--el-text-color-secondary); font-size: 12px;">
                （{{ row.applicantEmployeeNo }}）
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="120" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="expectedReturnAt" label="预计归还" width="150">
            <template #default="{ row }">{{ formatDateTime(row.expectedReturnAt) }}</template>
          </el-table-column>
          <el-table-column prop="returnedAt" label="实际归还" width="150">
            <template #default="{ row }">{{ row.returnedAt ? formatDateTime(row.returnedAt) : '-' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="borrowStatusType(row.status)" size="small">
                {{ borrowStatusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!borrowLoading && !borrowRecords.length" description="暂无借用记录" :image-size="60" />
      </el-tab-pane>
      <el-tab-pane label="操作日志" name="audit">
        <el-table :data="auditLogs" stripe size="small" max-height="360" scrollbar-always-on
                  style="width: 100%" v-loading="auditLoading">
          <el-table-column label="操作类型" width="120">
            <template #default="{ row }">
              <el-tag :type="auditActionTagType(row.actionType)" size="small">
                {{ auditActionLabel(row.actionType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="140" show-overflow-tooltip />
          <el-table-column prop="time" label="时间" width="160" />
          <el-table-column prop="detail" label="详情" min-width="240" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!auditLoading && !auditLogs.length" description="暂无操作日志" :image-size="60" />
      </el-tab-pane>
    </el-tabs>
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="actions.return" type="success" @click="$emit('return')">登记归还</el-button>
     <el-button v-if="actions.edit" type="primary" @click="$emit('edit')">编辑</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Hide, View } from '@element-plus/icons-vue'
import { resourcesApi } from '@/api'
import { usePasswordReveal } from './composables/usePasswordReveal.js'
import DateTimeDisplay from '@/components/common/DateTimeDisplay.vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  data: { type: Object, default: null },
  actions: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['update:modelValue', 'edit', 'return'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const activeTab = ref('info')
const borrowRecords = ref([])
const borrowLoading = ref(false)
// CO-522: 操作日志 Tab 状态
const auditLogs = ref([])
const auditLoading = ref(false)

const password = usePasswordReveal((id) => resourcesApi.accounts.getPassword(id))

watch(() => props.modelValue, (open) => {
  if (!open) {
    password.visible.value = {}
    password.revealed.value = {}
    password.loading.value = {}
  }
})

const BORROW_STATUS_MAP = {
  PENDING_APPROVAL: '待审批',
  BORROWED: '已借出',
  REJECTED: '已拒绝',
  RETURNED: '已归还',
  CANCELLED: '已撤销'
}
const borrowStatusLabel = (status) => BORROW_STATUS_MAP[status] || status

const borrowStatusType = (status) => {
  if (status === 'PENDING_APPROVAL') return 'warning'
  if (status === 'BORROWED') return 'success'
  if (status === 'REJECTED' || status === 'CANCELLED') return 'danger'
  if (status === 'RETURNED') return 'info'
  return ''
}

const formatDateTime = (value) => {
  if (!value) return '-'
  const d = new Date(value)
  return isNaN(d.getTime()) ? value : d.toLocaleString('zh-CN', { hour12: false })
}

const loadBorrowRecords = async () => {
  const id = props.data?.id ?? props.data?.raw?.id
  if (!id) return
  borrowLoading.value = true
  try {
    const res = await resourcesApi.accounts.getBorrowApplications(id)
    borrowRecords.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    console.error('Failed to load borrow records:', e)
    borrowRecords.value = []
  } finally {
    borrowLoading.value = false
  }
}

// CO-522: 加载账户操作日志（后端已过滤 VIEW_PASSWORD 等敏感事件）
const loadAuditLogs = async () => {
  const id = props.data?.id ?? props.data?.raw?.id
  if (!id) return
  auditLoading.value = true
  try {
    const res = await resourcesApi.accounts.getAuditLogs(id)
    auditLogs.value = Array.isArray(res?.data) ? res.data : []
  } catch (e) {
    console.error('Failed to load audit logs:', e)
    auditLogs.value = []
  } finally {
    auditLoading.value = false
  }
}

// CO-522: 操作类型 → 中文标签
const AUDIT_ACTION_LABELS = {
  create: '新增平台',
  update: '编辑平台',
  transfer_contact: '更换联系人',
  delete: '删除',
  borrow: '借用',
  return: '归还'
}
const auditActionLabel = (actionType) => AUDIT_ACTION_LABELS[actionType] || actionType || '未知'
const auditActionTagType = (actionType) => {
  if (actionType === 'create') return 'success'
  if (actionType === 'delete') return 'danger'
  if (actionType === 'transfer_contact') return 'warning'
  return 'info'
}

watch(() => props.data, (newVal) => {
  activeTab.value = 'info'
  borrowRecords.value = []
  auditLogs.value = []
  if (newVal) {
    loadBorrowRecords()
    loadAuditLogs()
  }
})
</script>

<style scoped>
.password-cell { display: inline-flex; align-items: center; gap: 8px; }
.password-text { font-family: monospace; }
</style>
