<template>
  <section class="customer-detail-panel">
    <el-skeleton :loading="loading" animated :rows="15">
      <div v-if="customer" class="detail-container scrollable">
        <div class="detail-header-card">
          <div class="header-main">
            <div class="avatar-box">
              {{ customer.customerName.charAt(0) }}
            </div>
            <div class="info-box">
              <div class="info-top">
                <h3>{{ customer.customerName }}</h3>
                <el-tag size="small" :type="getOpportunityStatusType(customer.status)" effect="dark">
                  {{ getOpportunityStatusLabel(customer.status) }}
                </el-tag>
              </div>
              <p class="info-sub">{{ customer.industry }} · {{ customer.region }}</p>
            </div>
          </div>
          <div class="header-actions">
            <el-button link type="primary" @click="$emit('open-history')">购买全记录</el-button>
            <el-button type="primary" :loading="converting" @click="$emit('convert')">
              {{ customer.prediction.convertedProjectId ? '查看项目' : '转为正式项目' }}
            </el-button>
          </div>
        </div>

        <div class="glass-section">
          <h4 class="section-title">客户画像</h4>
          <div class="profiling-grid">
            <div class="profiling-item">
              <span class="label">主要经营品类</span>
              <div class="tags-row">
                <el-tag v-for="cat in customer.mainCategories" :key="cat" size="small" effect="plain" class="m-1">{{ cat }}</el-tag>
              </div>
            </div>
            <div class="profiling-item">
              <span class="label">平均预算规模</span>
              <p class="value">¥ {{ customer.avgBudget }} <small>万元</small></p>
            </div>
            <div class="profiling-item">
              <span class="label">采购周期特征</span>
              <p class="value">{{ customer.cycleType }}</p>
            </div>
          </div>
        </div>

        <div class="insight-section">
          <div class="section-header">
            <h4 class="section-title">智能商机研判</h4>
            <div class="confidence-badge" :style="{ color: confidenceColor(normalizeConfidence(customer.prediction.confidence)) }">
              可信度 {{ normalizeConfidence(customer.prediction.confidence) }}%
            </div>
          </div>

          <div class="prediction-card">
            <div class="pred-grid">
              <div class="pred-item highlight">
                <span class="label">预测项目名称</span>
                <p>{{ customer.prediction.suggestedProjectName }}</p>
              </div>
              <div class="pred-item">
                <span class="label">预测品类</span>
                <p>{{ customer.prediction.predictedCategory }}</p>
              </div>
              <div class="pred-item">
                <span class="label">预测时间窗口</span>
                <p>{{ customer.prediction.predictedWindow }}</p>
              </div>
              <div class="pred-item">
                <span class="label">预测预算</span>
                <p>¥ {{ customer.prediction.predictedBudgetMin }} - {{ customer.prediction.predictedBudgetMax }} <small>万</small></p>
              </div>
            </div>
            <div class="reason-box">
              <el-icon><MagicStick /></el-icon>
              <span>{{ customer.prediction.reasoningSummary }}</span>
            </div>
          </div>
        </div>

        <div class="glass-section purchase-patterns">
          <h4 class="section-title">近一年采购规律</h4>
          <div class="timeline-container">
            <el-timeline>
              <el-timeline-item
                v-for="record in customer.purchaseHistory.slice(0, 3)"
                :key="record.recordId"
                :timestamp="record.publishDate"
                :type="record.isKey ? 'primary' : ''"
              >
                <div class="timeline-content">
                  <p class="t-title">{{ record.title }}</p>
                  <div class="t-meta">
                    <span>{{ record.category }}</span>
                    <span class="divider"></span>
                    <span>¥{{ record.budget }}万</span>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </div>
          <p class="insight-summary">
            <el-icon><MagicStick /></el-icon>
            {{ customer.predictionSummary }}
          </p>
        </div>
      </div>
      <div v-else-if="demoEnabled" class="smart-onboarding">
        <div class="onboarding-content">
          <div class="ai-avatar-large shadow-glow">
            <el-icon><MagicStick /></el-icon>
          </div>
          <h2>欢迎访问商机中心</h2>
          <p>我是您的 AI 销售助理。我已经为您分析了最新的市场动向与采购规律。</p>

          <div class="onboarding-suggestions">
            <div class="suggest-title">您可以尝试：</div>
            <div class="suggest-cards">
              <div class="s-card" @click="$emit('select-first')">
                <el-icon><Star /></el-icon>
                <span>查看高价值潜力客户</span>
              </div>
              <div class="s-card" @click="$emit('filter-recommend')">
                <el-icon><TrendCharts /></el-icon>
                <span>筛选建议立项的机会</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="api-empty-state">
        <div class="api-empty-card">
          <el-tag size="small" type="info" effect="light" class="api-empty-tag">API 模式</el-tag>
          <h2>客户商机中心暂未接入真实数据源</h2>
          <p>真实数据源尚未接入。请等待后端商机数据落地后再查看洞察与推荐结果。</p>
          <div class="api-empty-actions">
            <el-button disabled>刷新洞察</el-button>
            <el-button link type="primary" @click="$emit('go-bidding')">返回标讯中心</el-button>
          </div>
        </div>
      </div>
    </el-skeleton>
  </section>
</template>

<script setup>
import { MagicStick, Star, TrendCharts } from '@element-plus/icons-vue'
import { confidenceColor, getOpportunityStatusLabel, getOpportunityStatusType, normalizeConfidence } from './customerOpportunityCenter.helpers.js'

defineProps({
  customer: {
    type: Object,
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  converting: {
    type: Boolean,
    default: false,
  },
  demoEnabled: {
    type: Boolean,
    default: true,
  },
})

defineEmits(['open-history', 'convert', 'select-first', 'filter-recommend', 'go-bidding'])
</script>

<style scoped src="./CustomerOpportunityDetail.css"></style>
