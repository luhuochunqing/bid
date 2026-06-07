<template>
  <section class="customer-type-panel">
    <div class="customer-type-header">
      <div>
        <h3 class="customer-type-title">客户类型维度</h3>
      </div>
      <el-button :icon="Refresh" size="small" @click="loadCustomerTypes">刷新</el-button>
    </div>

    <div class="customer-type-summary">
      <div class="summary-item">
        <span class="summary-label">项目数</span>
        <strong>{{ summary.totalProjectCount }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">已分类</span>
        <strong>{{ summary.classifiedProjectCount }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">未分类</span>
        <strong>{{ summary.uncategorizedProjectCount }}</strong>
      </div>
      <div class="summary-item">
        <span class="summary-label">金额</span>
        <strong>{{ formatAmount(summary.totalAmount) }}</strong>
      </div>
    </div>

    <el-skeleton v-if="loading" animated :rows="4" />
    <el-empty v-else-if="dimensions.length === 0" description="暂无客户类型数据" />
    <div v-else class="customer-type-content">
      <div class="dimension-list">
        <button
          v-for="dimension in dimensions"
          :key="dimension.customerType"
          class="dimension-button"
          type="button"
          @click="openDrillDown(dimension.customerType)"
        >
          <span class="dimension-name">{{ dimension.customerType }}</span>
          <span class="dimension-meta">
            {{ dimension.projectCount }} 个项目 · {{ formatRate(dimension.winRate) }}
          </span>
          <el-progress
            :percentage="Number(dimension.percentage || 0)"
            :stroke-width="8"
            :show-text="false"
          />
        </button>
      </div>

      <el-table :data="dimensions" size="small" stripe>
        <el-table-column prop="customerType" label="客户类型" min-width="140" />
        <el-table-column prop="projectCount" label="项目数" width="90" align="right" />
        <el-table-column prop="activeProjectCount" label="进行中" width="90" align="right" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="中标率" width="100" align="right">
          <template #default="{ row }">{{ formatRate(row.winRate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDrillDown(row.customerType)">
              明细
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer
      v-model="drawerVisible"
      :title="`${selectedCustomerType || '全部'}项目明细`"
      size="60%"
      destroy-on-close
    >
      <el-table v-loading="drillDownLoading" :data="drillDownRows" stripe>
        <el-table-column prop="projectName" label="项目名称" min-width="220" show-overflow-tooltip />
        <el-table-column prop="customer" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerType" label="客户类型" width="120" />
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="getStatusTagType(row.status)">
              {{ formatProjectStatus(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="managerName" label="负责人" width="120" />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始时间" width="150">
          <template #default="{ row }">{{ formatDateTime(row.startDate) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </section>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { dashboardApi } from '@/api'
import './customerTypePanel.css'

const props = defineProps({
  dateRange: {
    type: Array,
    default: () => []
  },
  refreshKey: {
    type: Number,
    default: 0
  }
})

const loading = ref(false)
const responseData = ref(null)
const drawerVisible = ref(false)
const selectedCustomerType = ref('')
const drillDownRows = ref([])
const drillDownLoading = ref(false)

const dimensions = computed(() => responseData.value?.dimensions || [])
const summary = computed(() => ({
  totalProjectCount: Number(responseData.value?.totalProjectCount || 0),
  classifiedProjectCount: Number(responseData.value?.classifiedProjectCount || 0),
  uncategorizedProjectCount: Number(responseData.value?.uncategorizedProjectCount || 0),
  totalAmount: Number(responseData.value?.totalAmount || 0)
}))

const formatDateParam = (value) => {
  if (typeof value === 'string') return value.slice(0, 10)
  return new Date(value).toISOString().slice(0, 10)
}

const buildDateParams = () => {
  const [startDate, endDate] = Array.isArray(props.dateRange) ? props.dateRange : []
  const params = {}
  if (startDate) params.startDate = formatDateParam(startDate)
  if (endDate) params.endDate = formatDateParam(endDate)
  return params
}

const loadCustomerTypes = async () => {
  loading.value = true
  try {
    const response = await dashboardApi.getCustomerTypes(buildDateParams())
    if (!response?.success) {
      throw new Error(response?.msg || '客户类型数据加载失败')
    }
    responseData.value = response.data || { dimensions: [] }
  } catch (error) {
    responseData.value = { dimensions: [] }
    ElMessage.error(error?.message || '客户类型数据加载失败')
  } finally {
    loading.value = false
  }
}

const openDrillDown = async (customerType) => {
  selectedCustomerType.value = customerType
  drawerVisible.value = true
  drillDownLoading.value = true
  try {
    const response = await dashboardApi.getCustomerTypeDrillDown({
      ...buildDateParams(),
      customerType
    })
    if (!response?.success) {
      throw new Error(response?.msg || '客户类型明细加载失败')
    }
    drillDownRows.value = Array.isArray(response.data) ? response.data : []
  } catch (error) {
    drillDownRows.value = []
    ElMessage.error(error?.message || '客户类型明细加载失败')
  } finally {
    drillDownLoading.value = false
  }
}

const formatAmount = (value) => {
  const amount = Number(value || 0)
  if (amount >= 100000000) return `${(amount / 100000000).toFixed(1)}亿`
  if (amount >= 10000) return `${(amount / 10000).toFixed(1)}万`
  return `${amount.toLocaleString('zh-CN')}`
}

const formatRate = (value) => `${Number(value || 0).toFixed(1)}%`

const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

const formatProjectStatus = (status) => ({
  PENDING_INITIATION: '待立项',
  INITIATED: '已立项',
  BIDDING: '投标中',
  EVALUATING: '评标中',
  WON: '已中标',
  LOST: '未中标',
  FAILED: '已流标',
  ABANDONED: '已放弃'
}[status] || status || '-')

const getStatusTagType = (status) => ({
  PENDING_INITIATION: 'info',
  INITIATED: 'info',
  BIDDING: 'success',
  EVALUATING: 'primary',
  WON: 'success',
  LOST: 'danger',
  FAILED: 'warning',
  ABANDONED: 'info'
}[status] || 'info')

watch(() => props.refreshKey, loadCustomerTypes)
watch(() => props.dateRange, loadCustomerTypes, { deep: true })

onMounted(loadCustomerTypes)
</script>
