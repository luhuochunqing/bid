<template>
  <section v-if="tenders.length > 0" class="ai-recommend-section">
    <div class="section-header">
      <div class="section-title">
        <el-icon><MagicStick /></el-icon>
        AI推荐
        <el-tag size="small" type="success">高匹配</el-tag>
      </div>
      <el-link type="primary" underline="hover" @click="$emit('view-all')">
        查看全部
        <el-icon><ArrowRight /></el-icon>
      </el-link>
    </div>
    <div class="recommend-cards">
      <article
        v-for="tender in tenders"
        :key="tender.id"
        class="recommend-card"
        @click="$emit('view-detail', tender.id)"
      >
        <div class="recommend-title-row">
          <h4>{{ tender.title }}</h4>
          <span class="ai-score" :class="getScoreClass(tender.aiScore)">{{ tender.aiScore }}分</span>
        </div>
        <div class="card-info">
          <span><el-icon><Location /></el-icon>{{ tender.region }}</span>
          <span><el-icon><Wallet /></el-icon>{{ formatBudgetWan(tender.budget) }}万元</span>
          <span><el-icon><Calendar /></el-icon>{{ tender.deadline }}截止</span>
        </div>
        <div class="card-tags">
          <el-tag v-for="tag in tender.tags || []" :key="tag" size="small">{{ tag }}</el-tag>
          <el-tag v-if="tender.source" size="small" :type="getSourceTagType(tender.source)">
            {{ getSourceText(tender.source) }}
          </el-tag>
        </div>
        <p class="recommend-reason">{{ tender.aiReason || '系统推荐关注该标讯' }}</p>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ArrowRight, Calendar, Location, MagicStick, Wallet } from '@element-plus/icons-vue'
import { formatBudgetWan, getScoreClass, getSourceTagType, getSourceText } from '../helpers.js'

defineProps({
  tenders: { type: Array, default: () => [] },
})

defineEmits(['view-all', 'view-detail'])
</script>
