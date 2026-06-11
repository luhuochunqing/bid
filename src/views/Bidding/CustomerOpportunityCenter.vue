<template>
  <div class="customer-opportunity-page" :class="{ 'is-loading': loading }">
    <div class="page-header">
      <div class="header-text">
        <h2 class="animate-fade-in">客户商机中心</h2>
        <p class="animate-fade-in-delay">基于销售情报（Sales Intelligence）的客户经营视图，智能研判历史规律与潜在商机。</p>
      </div>
      <div class="header-actions">
        <el-button
          @click="refreshInsights"
          :loading="isScanning"
          class="btn-refresh"
        >
          <el-icon><Refresh /></el-icon>
          刷新洞察
        </el-button>
        <el-button v-if="selectedCustomer" type="primary" class="btn-primary" @click="convertSelectedCustomerToProject" :loading="isConverting">
          {{ selectedCustomer.prediction.convertedProjectId ? '查看项目' : '转为正式项目' }}
        </el-button>
      </div>
    </div>

    <transition name="fade">
      <div v-if="isScanning" class="scanning-overlay">
        <div class="scan-grid"></div>
        <div class="scan-line"></div>
        <div class="scan-content">
          <div class="hologram-box">
            <el-icon class="rotating"><Refresh /></el-icon>
          </div>
          <h3>AI 引擎正在分析全域数据...</h3>
          <p>正在研判采购规律 · 识别机会评分 · 测算预算窗口</p>
        </div>
      </div>
    </transition>

    <el-skeleton v-if="loading" animated>
      <template #template>
        <div class="top-board skeleton-grid">
          <div v-for="i in 4" :key="i" class="board-card skeleton-card">
            <el-skeleton-item variant="text" style="width: 50%" />
            <el-skeleton-item variant="h3" style="width: 80%; margin-top: 12px" />
            <el-skeleton-item variant="text" style="width: 60%; margin-top: 8px" />
          </div>
        </div>
      </template>
    </el-skeleton>
    <CustomerOpportunityBoard v-else :summaries="boardSummaries" />

    <div class="content-grid">
      <CustomerOpportunityPool
        :customers="filteredCustomers"
        :sales-users="salesUsers"
        :regions="regions"
        :industries="industries"
        :status-options="statusOptions"
        v-model:filters="filters"
        :loading="loading"
        :demo-enabled="customerOpportunityDemoEnabled"
        :row-class-name="rowClass"
        @select="selectCustomer"
      />

      <CustomerOpportunityDetail
        :customer="selectedCustomer"
        :loading="loading"
        :converting="isConverting"
        :demo-enabled="customerOpportunityDemoEnabled"
        @open-history="historyDrawer = true"
        @convert="convertSelectedCustomerToProject"
        @select-first="selectFirstHighValue"
        @filter-recommend="setRecommendFilter"
        @go-bidding="goBidding"
      />
    </div>

    <CustomerOpportunityHistoryDrawer
      v-model:visible="historyDrawer"
      :history="customerHistory"
      :drawer-stats="drawerStats"
      :category-stats="categoryStats"
    />
  </div>
</template>

<script setup>
import CustomerOpportunityBoard from './customer-opportunity/CustomerOpportunityBoard.vue'
import CustomerOpportunityDetail from './customer-opportunity/CustomerOpportunityDetail.vue'
import CustomerOpportunityHistoryDrawer from './customer-opportunity/CustomerOpportunityHistoryDrawer.vue'
import CustomerOpportunityPool from './customer-opportunity/CustomerOpportunityPool.vue'
import { useCustomerOpportunityCenter } from './customer-opportunity/useCustomerOpportunityCenter.js'
import { Refresh } from '@element-plus/icons-vue'

const statusOptions = [
  { label: '待判断机会', value: 'watch' },
  { label: '建议转项目', value: 'recommend' },
  { label: '已转化项目', value: 'converted' },
]

const {
  loading,
  isScanning,
  isConverting,
  customerOpportunityDemoEnabled,
  historyDrawer,
  filters,
  salesUsers,
  regions,
  industries,
  filteredCustomers,
  selectedCustomer,
  customerHistory,
  drawerStats,
  categoryStats,
  boardSummaries,
  rowClass,
  selectCustomer,
  selectFirstHighValue,
  refreshInsights,
  convertSelectedCustomerToProject,
  goBidding,
  setRecommendFilter,
} = useCustomerOpportunityCenter()
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&display=swap');

.customer-opportunity-page {
  padding: var(--space-6, 24px);
  min-height: 100vh;
  background: var(--bg-page);
  font-family: 'Plus Jakarta Sans', -apple-system, system-ui, sans-serif;
  color: var(--text-primary);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: var(--space-8, 32px);
}

.header-text h2 {
  font-size: 28px;
  font-weight: 700;
  color: #0f172a;
  margin: 0;
  letter-spacing: -0.02em;
}

.header-text p {
  color: var(--text-slate);
  margin: 8px 0 0;
  font-size: 15px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.btn-refresh {
  background: white;
  border-color: var(--gray-200);
  color: var(--sidebar-text-secondary);
  font-weight: 500;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
}

.btn-refresh:hover {
  background: var(--gray-50);
  transform: translateY(-1px);
}

.content-grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: 20px;
  align-items: start;
}

.scanning-overlay {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(6px);
}

.scan-grid,
.scan-line,
.scan-content {
  pointer-events: none;
}

.scan-content {
  position: relative;
  z-index: 1;
  text-align: center;
  color: white;
}

.hologram-box {
  width: 88px;
  height: 88px;
  margin: 0 auto 16px;
  border-radius: 28px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.08);
}

.rotating {
  animation: spin 1.8s linear infinite;
  font-size: 30px;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.skeleton-grid {
  margin-bottom: 32px;
}

.skeleton-card {
  background: white;
  padding: 24px;
  border-radius: 16px;
  border: 1px solid var(--gray-50);
}

@media (max-width: 1200px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
