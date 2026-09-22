<template>
  <el-card v-if="detail.showAICheckCard.value" class="ai-check-card">
    <template #header>
      <div class="card-title">
        <el-icon><MagicStick /></el-icon>
        <span>AI智能检查</span>
        <el-button v-if="detail.canRunAICheck.value" type="primary" size="small" :loading="detail.aiChecking.value" @click="detail.runAICheck">
          <el-icon v-if="!detail.aiChecking.value"><VideoPlay /></el-icon>
          {{ detail.aiChecking.value ? '检查中...' : '开始检查' }}
        </el-button>
      </div>
    </template>

    <el-tabs v-model="detail.activeAITab.value" class="ai-tabs">
      <el-tab-pane name="compliance">
        <template #label><div class="tab-label"><span>合规性检查</span><el-badge v-if="detail.aiResult.value.compliance" :value="detail.aiResult.value.compliance.score" :type="detail.getBadgeType(detail.aiResult.value.compliance.score)" :max="100" /></div></template>
        <ProjectDetailCompliancePane />
      </el-tab-pane>
      <el-tab-pane name="asset-check">
        <template #label><div class="tab-label"><span>可投标能力</span><el-badge v-if="detail.assetCheckResult.value" :value="detail.assetCheckResult.value.capability?.status === 'available' ? 1 : 0" :type="detail.assetCheckResult.value.capability?.status === 'available' ? 'success' : 'warning'" /></div></template>
        <ProjectDetailAssetCheckPane />
      </el-tab-pane>
      <el-tab-pane name="quality">
        <template #label><div class="tab-label"><span>文书质量</span><el-badge v-if="detail.qualityResult.value" :value="detail.qualityResult.value.errors?.length || 0" type="warning" /></div></template>
        <ProjectDetailQualityPane />
      </el-tab-pane>
      <el-tab-pane name="score">
        <template #label><div class="tab-label"><span>智能评分</span><el-badge v-if="detail.aiResult.value.score" :value="detail.aiResult.value.score.total || 0" type="primary" /></div></template>
        <ProjectDetailScorePane />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { inject } from 'vue'
import { MagicStick, VideoPlay } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectDetailAssetCheckPane from './ProjectDetailAssetCheckPane.vue'
import ProjectDetailCompliancePane from './ProjectDetailCompliancePane.vue'
import ProjectDetailQualityPane from './ProjectDetailQualityPane.vue'
import ProjectDetailScorePane from './ProjectDetailScorePane.vue'

const detail = inject(projectDetailKey)
</script>
