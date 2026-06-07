<template>
  <el-card class="process-card">
    <template #header>
      <div class="card-title">
        <el-icon><DocumentChecked /></el-icon>
        <span>标书编制流程</span>
        <div class="workflow-header-actions" aria-label="标书编制操作">
          <el-button type="success" size="small" plain @click="detail.bidAgent.openDrawer">AI 生成初稿</el-button>
          <el-button v-if="!detail.bidProcess.initiated" type="primary" size="small" @click="detail.handleInitiateProcess">发起流程</el-button>
        </div>
      </div>
    </template>

    <div v-if="!detail.bidProcess.initiated" class="process-empty">
      <el-empty description="暂未发起标书编制流程">
        <el-button type="primary" @click="detail.handleInitiateProcess">立即发起</el-button>
      </el-empty>
    </div>

    <div v-else class="process-content">
      <el-steps :active="detail.bidProcess.currentStep" align-center finish-status="success">
        <el-step title="初稿编制" :description="detail.getStepStatusText('draft')" />
        <el-step title="内部评审" :description="detail.getStepStatusText('review')" />
        <el-step title="用印申请" :description="detail.getStepStatusText('seal')" />
        <el-step title="封装提交" :description="detail.getStepStatusText('submit')" />
      </el-steps>

      <div class="process-actions">
        <el-button :type="detail.bidProcess.steps.draft.completed ? 'success' : 'primary'" :disabled="!detail.canOperateStep('draft')" @click="detail.handleDraftSubmit">{{ detail.bidProcess.steps.draft.completed ? '初稿已提交' : '提交初稿' }}</el-button>
        <el-button :type="detail.bidProcess.steps.review.completed ? 'success' : 'warning'" :disabled="!detail.canOperateStep('review')" @click="detail.handleReview">{{ detail.bidProcess.steps.review.completed ? '评审已完成' : '发起评审' }}</el-button>
        <el-button :type="detail.bidProcess.steps.seal.completed ? 'success' : 'primary'" :disabled="!detail.canOperateStep('seal')" @click="detail.handleSealApply">{{ detail.bidProcess.steps.seal.completed ? '用印已完成' : '用印申请' }}</el-button>
        <el-button :type="detail.bidProcess.steps.submit.completed ? 'success' : 'primary'" :disabled="!detail.canOperateStep('submit')" @click="detail.handleSubmit">{{ detail.bidProcess.steps.submit.completed ? '已封装提交' : '封装提交' }}</el-button>
      </div>

      <div class="process-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="流程发起人">{{ detail.bidProcess.initiator }}</el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ detail.bidProcess.initiateTime }}</el-descriptions-item>
          <el-descriptions-item label="当前阶段"><el-tag :type="detail.getCurrentPhaseType()">{{ detail.getCurrentPhaseText() }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="整体进度"><el-progress :percentage="detail.getProcessProgress()" :stroke-width="12" :show-text="true" /></el-descriptions-item>
        </el-descriptions>

        <div v-if="detail.bidProcess.deliverables.length" class="deliverables-section">
          <div class="section-title">交付物清单</div>
          <el-table :data="detail.bidProcess.deliverables" size="small" max-height="200">
            <el-table-column prop="name" label="交付物名称" min-width="150" />
            <el-table-column prop="type" label="类型" width="100"><template #default="{ row }"><el-tag size="small">{{ row.type }}</el-tag></template></el-table-column>
            <el-table-column prop="uploader" label="上传者" width="100" />
            <el-table-column prop="time" label="上传时间" width="140" />
            <el-table-column label="操作" width="80"><template #default="{ row }"><el-button link type="primary" size="small" @click="detail.handleDownloadDeliverable(row)">下载</el-button></template></el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { inject } from 'vue'
import { DocumentChecked } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
