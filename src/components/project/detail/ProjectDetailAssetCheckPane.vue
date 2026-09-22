<template>
  <div class="asset-check-tab">
    <div v-if="detail.assetCheckResult.value && detail.assetCheckResult.value.found" class="asset-check-content">
      <div class="check-header">
        <div class="site-info">
          <h3>{{ detail.assetCheckResult.value.site?.name }}</h3>
          <el-tag v-if="detail.assetCheckResult.value.capability?.status === 'available'" type="success" size="large">可投标</el-tag>
          <el-tag v-else-if="detail.assetCheckResult.value.capability?.status === 'risk'" type="warning" size="large">有风险</el-tag>
          <el-tag v-else type="danger" size="large">不可投标</el-tag>
        </div>
        <a :href="detail.assetCheckResult.value.site?.url" target="_blank" class="site-link">{{ detail.assetCheckResult.value.site?.url }}</a>
      </div>
      <div class="check-items-grid">
        <div class="check-item-card"><div class="item-icon"><el-icon v-if="detail.assetCheckResult.value.capability?.hasAccount" class="icon-success"><CircleCheck /></el-icon><el-icon v-else class="icon-error"><CircleClose /></el-icon></div><div class="item-content"><div class="item-label">账号状态</div><div class="item-value">{{ detail.assetCheckResult.value.capability?.hasAccount ? '已注册' : '未注册' }}<span v-if="detail.assetCheckResult.value.capability?.accountCount > 0">({{ detail.assetCheckResult.value.capability?.accountCount }}个)</span></div></div></div>
        <div class="check-item-card"><div class="item-icon"><el-icon v-if="detail.assetCheckResult.value.capability?.hasAvailableUK" class="icon-success"><CircleCheck /></el-icon><el-icon v-else-if="detail.assetCheckResult.value.capability?.ukCount > 0" class="icon-warning"><Warning /></el-icon><el-icon v-else class="icon-success"><CircleCheck /></el-icon></div><div class="item-content"><div class="item-label">UK状态</div><div class="item-value"><span v-if="detail.assetCheckResult.value.capability?.ukCount === 0">不需要</span><span v-else-if="detail.assetCheckResult.value.capability?.hasAvailableUK">在库</span><span v-else>已借出</span></div></div></div>
        <div v-if="detail.assetCheckResult.value.capability?.primaryOwner" class="check-item-card"><div class="item-icon"><el-icon class="icon-user"><User /></el-icon></div><div class="item-content"><div class="item-label">责任人</div><div class="item-value">{{ detail.assetCheckResult.value.capability?.primaryOwner }}<span class="phone">({{ detail.assetCheckResult.value.capability?.primaryPhone }})</span></div></div></div>
      </div>
      <div class="check-actions"><el-button type="primary" @click="detail.goToSiteDetail">查看详情</el-button><el-button v-if="detail.assetCheckResult.value.capability?.ukCount > 0" @click="detail.borrowUK">借用UK</el-button><el-button @click="detail.viewSOP">查看SOP</el-button></div>
    </div>
    <el-empty v-else description="未找到该站点的资产信息" :image-size="100"><el-button type="primary" @click="detail.goToAssetManagement">前往资产管理</el-button></el-empty>
  </div>
</template>

<script setup>
import { inject } from 'vue'
import { CircleCheck, CircleClose, User, Warning } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
