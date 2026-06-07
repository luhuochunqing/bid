<template>
  <el-dialog :model-value="modelValue" title="市场洞察与趋势预测" width="900px" @update:model-value="$emit('update:modelValue', $event)">
    <template #header>
      <div class="market-header">
        <span>市场洞察与趋势预测</span>
        <el-button :icon="Refresh" :loading="loading" size="small" text @click="$emit('refresh')">刷新数据</el-button>
      </div>
    </template>
    <div v-if="loading" class="trend-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在加载趋势数据...</span>
    </div>
    <el-tabs v-else v-model="activeTabProxy">
      <el-tab-pane label="行业趋势" name="industry">
        <el-table :data="industryTrends" size="small" stripe>
          <el-table-column prop="industry" label="行业" min-width="140" />
          <el-table-column prop="count" label="标讯数量" width="110" align="center" />
          <el-table-column prop="amount" label="总预算(万元)" width="130" align="center">
            <template #default="{ row = {} } = {}">{{ Number(row.amount || 0).toLocaleString() }}</template>
          </el-table-column>
          <el-table-column prop="growth" label="同比增长" width="110" align="center">
            <template #default="{ row = {} } = {}">
              <span :class="Number(row.growth) >= 0 ? 'growth-up' : 'growth-down'">
                {{ Number(row.growth) >= 0 ? '+' : '' }}{{ row.growth }}%
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="hotLevel" label="热度" width="140" align="center">
            <template #default="{ row = {} } = {}">
              <el-rate :model-value="row.hotLevel" disabled size="small" />
            </template>
          </el-table-column>
        </el-table>
        <el-alert class="insight-alert" type="success" :closable="false" show-icon :title="industryInsight" />
      </el-tab-pane>
      <el-tab-pane label="高潜力机会" name="opportunity">
        <div class="opportunity-grid">
          <el-card v-for="item in opportunities" :key="item.id" shadow="never">
            <div class="opportunity-title">
              <strong>{{ item.title }}</strong>
              <el-tag size="small" :type="item.priority === 'high' ? 'danger' : 'warning'">
                {{ item.match }}%
              </el-tag>
            </div>
            <p>{{ item.purchaser }} · {{ item.region }} · {{ item.budget }}万元</p>
            <p class="muted">{{ item.reason }}</p>
          </el-card>
        </div>
      </el-tab-pane>
      <el-tab-pane label="预测分析" name="forecast">
        <ul class="tips-list">
          <li v-for="(tip, index) in forecastTips" :key="index">
            <el-icon :color="tip.color"><CircleCheck /></el-icon>
            {{ tip.text }}
          </li>
        </ul>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, Loading, Refresh } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  activeTab: { type: String, default: 'industry' },
  loading: { type: Boolean, default: false },
  industryTrends: { type: Array, default: () => [] },
  opportunities: { type: Array, default: () => [] },
  industryInsight: { type: String, default: '' },
  forecastTips: { type: Array, default: () => [] },
})

const emit = defineEmits(['update:modelValue', 'update:activeTab', 'refresh'])

const activeTabProxy = computed({
  get: () => props.activeTab,
  set: (value) => emit('update:activeTab', value),
})
</script>
