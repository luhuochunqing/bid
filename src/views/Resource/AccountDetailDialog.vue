<template>
  <el-dialog v-model="visible" title="账户详情" width="680px">
    <el-tabs v-model="activeTab" v-if="data">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="平台名称" :span="2">{{ data.accountName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="网址">{{ data.url || '-' }}</el-descriptions-item>
          <el-descriptions-item label="平台类型">{{ platformTypeLabel || '-' }}</el-descriptions-item>
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
          <el-descriptions-item label="绑定联系人">{{ data.contactPersonLabel || '-' }}</el-descriptions-item>
          <el-descriptions-item label="绑定手机">{{ data.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="绑定邮箱">{{ data.contactEmail || '-' }}</el-descriptions-item>
          <el-descriptions-item label="是否有 CA">{{ data.hasCa ? '是' : '否' }}</el-descriptions-item>
          <el-descriptions-item label="使用人">{{ data.borrower || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ data.remarks || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最近使用">{{ data.lastUsed || '-' }}</el-descriptions-item>
          <el-descriptions-item label="归还截止">{{ data.dueAt || '-' }}</el-descriptions-item>
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

const password = usePasswordReveal((id) => resourcesApi.accounts.getPassword(id))

watch(() => props.modelValue, (open) => {
  if (!open) {
    password.visible.value = {}
    password.revealed.value = {}
    password.loading.value = {}
  }
})

const PLATFORM_TYPE_MAP = {
  GOV_PROCUREMENT: '政府采购',
  BIDDING_PLATFORM: '招投标平台',
  CONSTRUCTION_PLATFORM: '建设平台',
  ENTERPRISE_SELF: '企业自建',
  OTHER: '其他'
}
const platformTypeLabel = computed(() => {
  const raw = props.data?.platformType
  if (!raw) return ''
  return PLATFORM_TYPE_MAP[raw] || raw
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

watch(() => props.data, (newVal) => {
  activeTab.value = 'info'
  borrowRecords.value = []
  if (newVal) {
    loadBorrowRecords()
  }
})
</script>

<style scoped>
.password-cell { display: inline-flex; align-items: center; gap: 8px; }
.password-text { font-family: monospace; }
</style>
