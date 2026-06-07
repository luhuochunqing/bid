<template>
  <div class="bar-check-panel">
    <!-- 页面标题 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">可投标能力检查</h2>
        <p class="page-subtitle">快速检查站点账号、UK、责任人等资产状态</p>
      </div>
      <div class="header-actions">
        <el-button @click="$router.push('/resource/bar/sites')">
          <el-icon><List /></el-icon>
          站点台账
        </el-button>
      </div>
    </div>

    <!-- 搜索检查区 -->
    <el-card class="search-card" shadow="never">
      <div class="search-box">
        <el-input
          v-model="searchKeyword"
          placeholder="请输入站点名称或网址"
          size="large"
          clearable
          @keyup.enter="handleCheck"
        >
          <template #prepend>
            <el-icon><Search /></el-icon>
          </template>
          <template #append>
            <el-button type="primary" @click="handleCheck">检查</el-button>
          </template>
        </el-input>
      </div>

      <!-- 快捷入口 -->
      <div class="quick-sites">
        <span class="quick-label">常用站点：</span>
        <el-tag
          v-for="site in quickSites"
          :key="site.id"
          class="quick-tag"
          @click="quickCheck(site)"
        >
          {{ site.name }}
        </el-tag>
      </div>
    </el-card>

    <!-- 检查结果 -->
    <div v-if="checkResult" class="result-section">
      <h3 class="result-title">检查结果</h3>

      <!-- 未找到站点 -->
      <el-empty
        v-if="!checkResult.found"
        description="未找到该站点，您可以前往台账添加"
        :image-size="120"
      >
        <el-button type="primary" @click="goToAddSite">添加站点</el-button>
        <el-button @click="checkResult = null">重新检查</el-button>
      </el-empty>

      <!-- 找到站点 -->
      <AssetCard
        v-else
        :site="checkResult.site"
        :capability="checkResult.capability"
        @view-detail="goToSiteDetail"
        @borrow="handleBorrow"
        @contact="handleContact"
        @view-sop="goToSOP"
      />
    </div>

    <!-- 历史检查记录 -->
    <el-card v-if="checkHistory.length > 0" class="history-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>检查记录</span>
          <el-button link type="primary" @click="clearHistory">清空</el-button>
        </div>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="(record, index) in checkHistory"
          :key="index"
          :timestamp="record.time"
          placement="top"
        >
          <div class="history-item">
            <span class="history-site">{{ record.siteName }}</span>
            <el-tag
              :type="record.status === 'available' ? 'success' : record.status === 'risk' ? 'warning' : 'danger'"
              size="small"
            >
              {{ getStatusText(record.status) }}
            </el-tag>
            <el-button link type="primary" size="small" @click="quickCheckBySiteId(record.siteId)">
              再次检查
            </el-button>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

    <!-- 借用弹窗 -->
    <BorrowDialog
      v-model="showBorrowDialog"
      :site="currentSite"
      @confirm="handleBorrowConfirm"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useBarStore } from '@/stores/bar'
import { Search, List } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import AssetCard from './components/AssetCard.vue'
import BorrowDialog from './components/BorrowDialog.vue'

const router = useRouter()
const barStore = useBarStore()

const searchKeyword = ref('')
const checkResult = ref(null)
const checkHistory = ref([])
const showBorrowDialog = ref(false)
const currentSite = ref(null)

// 快捷站点（取前3个活跃站点）
const quickSites = computed(() => {
  return barStore.activeSites.slice(0, 3)
})

const getStatusText = (status) => {
  const map = {
    'available': '可投标',
    'risk': '有风险',
    'unavailable': '不可投标'
  }
  return map[status] || status
}

// 添加到历史记录
const addToHistory = (site, status) => {
  const exists = checkHistory.value.find(r => r.siteId === site.id)
  if (exists) {
    // 更新时间
    exists.time = new Date().toLocaleString('zh-CN')
    exists.status = status
  } else {
    checkHistory.value.unshift({
      siteId: site.id,
      siteName: site.name,
      status,
      time: new Date().toLocaleString('zh-CN')
    })
  }
  // 只保留最近10条
  if (checkHistory.value.length > 10) {
    checkHistory.value = checkHistory.value.slice(0, 10)
  }
}

// 检查站点
const handleCheck = async () => {
  if (!searchKeyword.value.trim()) {
    ElMessage.warning('请输入站点名称或网址')
    return
  }

  const result = await barStore.checkSiteCapability(searchKeyword.value.trim())
  checkResult.value = result

  // 添加到历史记录
  if (result.found) {
    addToHistory(result.site, result.capability.status)
  }
}

// 快捷检查
const quickCheck = (site) => {
  searchKeyword.value = site.name
  handleCheck()
}

const quickCheckBySiteId = (siteId) => {
  const site = barStore.sites.find(s => s.id === siteId)
  if (site) {
    searchKeyword.value = site.name
    handleCheck()
  }
}

const clearHistory = () => {
  checkHistory.value = []
}

const goToAddSite = () => {
  router.push('/resource/bar/sites?action=add')
}

const goToSiteDetail = (site) => {
  router.push(`/resource/bar/site/${site.id}`)
}

const goToSOP = (site) => {
  router.push(`/resource/bar/sop/${site.id}`)
}

const handleBorrow = (site) => {
  currentSite.value = site
  showBorrowDialog.value = true
}

const handleBorrowConfirm = async (data) => {
  const response = await barStore.borrowUk(currentSite.value?.id, data.ukId, data)
  if (!response?.success) {
    ElMessage.error(response?.msg || '借用申请提交失败')
    return
  }
  ElMessage.success('借用申请已提交')
  showBorrowDialog.value = false
  // 重新检查
  if (checkResult.value && checkResult.value.found) {
    await handleCheck()
  }
}

const handleContact = (site) => {
  const owner = site.accounts?.[0]?.owner
  const phone = site.accounts?.[0]?.phone
  if (owner && phone) {
    ElMessage.info(`责任人：${owner}，电话：${phone}`)
  } else {
    ElMessage.warning('暂无责任人信息')
  }
}

onMounted(async () => {
  const response = await barStore.getSites()
  if (!response?.success) {
    ElMessage.error(response?.msg || 'BAR 站点数据加载失败')
  }
})
</script>

<style scoped>
.bar-check-panel {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--text-primary);
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.search-card {
  margin-bottom: 24px;
}

.search-box {
  margin-bottom: 16px;
}

.quick-sites {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-label {
  font-size: 14px;
  color: var(--text-secondary-ui);
}

.quick-tag {
  cursor: pointer;
  transition: all 0.2s;
}

.quick-tag:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.result-section {
  margin-bottom: 24px;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 16px 0;
  color: var(--text-primary);
}

.history-card .card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 12px;
}

.history-site {
  font-weight: 500;
  color: var(--gray-750);
}
</style>
