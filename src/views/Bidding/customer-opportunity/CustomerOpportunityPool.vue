<template>
  <section class="customer-list-panel">
    <div class="panel-header search-integrated">
      <div class="panel-title">
        <el-icon class="title-icon"><User /></el-icon>
        <h3>客户池</h3>
      </div>
      <div class="header-filters multi-row">
        <div class="filter-row">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索名称..."
            clearable
            size="default"
            :disabled="!demoEnabled"
            class="search-input"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <UserPicker
            v-model="filters.sales"
            mode="search"
            value-field="name"
            placeholder="销售负责人"
            :initial-options="salesUsers"
            :disabled="!demoEnabled"
            class="filter-item"
          />
        </div>
        <div class="filter-row">
          <el-select v-model="filters.region" placeholder="全部地区" size="default" clearable :disabled="!demoEnabled" class="filter-item">
            <el-option v-for="region in regions" :key="region" :label="region" :value="region" />
          </el-select>
          <el-select v-model="filters.industry" placeholder="全部行业" size="default" clearable :disabled="!demoEnabled" class="filter-item">
            <el-option v-for="ind in industries" :key="ind" :label="ind" :value="ind" />
          </el-select>
          <el-select v-model="filters.status" placeholder="全部分类" size="default" clearable :disabled="!demoEnabled" class="filter-item">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </div>
      </div>
    </div>

    <el-skeleton :loading="loading" animated :rows="10">
      <el-table
        :data="customers"
        size="default"
        row-key="customerId"
        @row-click="(row) => $emit('select', row)"
        :row-class-name="rowClassName"
        class="premium-table"
      >
        <template #empty>
          <el-empty :description="demoEnabled ? '暂无符合条件的客户' : '客户商机数据源未接入'" />
        </template>
        <el-table-column prop="customerName" label="客户名称" min-width="220">
          <template #default="{ row }">
            <div class="customer-name-cell">
              <strong>{{ row.customerName }}</strong>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="region" label="地区" width="100" />
        <el-table-column prop="industry" label="行业" width="100" />
        <el-table-column prop="salesRep" label="销售负责人" width="140" />
        <el-table-column prop="opportunityScore" label="机会评分" width="110" align="center">
          <template #default="{ row }">
            <div class="score-container">
              <span class="score-num" :class="getScoreClass(row.opportunityScore)">{{ row.opportunityScore }}</span>
              <el-progress
                :percentage="row.opportunityScore"
                :show-text="false"
                :stroke-width="4"
                :color="getScoreColor(row.opportunityScore)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="predictedNextWindow" label="预测窗口" width="140" align="center">
          <template #default="{ row }">
            <span class="window-tag">{{ row.predictedNextWindow }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-skeleton>
  </section>
</template>

<script setup>
import { Search, User } from '@element-plus/icons-vue'
import UserPicker from '@/components/common/UserPicker.vue'
import { getScoreClass, getScoreColor } from './customerOpportunityCenter.helpers.js'

defineModel('filters', {
  type: Object,
  default: () => ({}),
})
defineProps({
  customers: {
    type: Array,
    default: () => [],
  },
  salesUsers: {
    type: Array,
    default: () => [],
  },
  regions: {
    type: Array,
    default: () => [],
  },
  industries: {
    type: Array,
    default: () => [],
  },
  statusOptions: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  demoEnabled: {
    type: Boolean,
    default: true,
  },
  rowClassName: {
    type: Function,
    default: () => '',
  },
})

defineEmits(['select'])
</script>

<style scoped>
.customer-list-panel {
  background: white;
  border-radius: 20px;
  border: 1px solid var(--gray-200);
  padding: 24px;
  min-width: 0;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
  margin-bottom: 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.title-icon {
  color: #2563eb;
}

.panel-title h3 {
  margin: 0;
  font-size: 20px;
  color: #0f172a;
}

.header-filters {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.filter-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-item,
.search-input {
  min-width: 160px;
}

.search-input {
  width: 260px;
}

.customer-name-cell strong {
  color: #0f172a;
}

.score-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.score-num {
  font-weight: 700;
}

.score-num.high {
  color: var(--color-success);
}

.score-num.mid {
  color: #f59e0b;
}

.score-num.low {
  color: var(--text-slate);
}

.window-tag {
  color: var(--sidebar-text-secondary);
  font-size: 13px;
}

:deep(.row-active td) {
  background: #eff6ff !important;
}

:deep(.el-table__empty-text) {
  color: var(--gray-400);
}

@media (max-width: 960px) {
  .panel-header {
    flex-direction: column;
  }

  .search-input {
    width: 100%;
  }
}
</style>
