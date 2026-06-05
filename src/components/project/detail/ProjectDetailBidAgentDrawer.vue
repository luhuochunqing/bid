<template>
  <el-drawer v-model="visible" title="AI 生成初稿" size="560px" append-to-body>
    <div class="bid-agent-drawer">
      <section class="agent-hero">
        <div>
          <p class="agent-eyebrow">Bid Writing Agent</p>
          <h3>从招标文件生成可审阅标书初稿</h3>
          <p>上传招标文件后自动拆解要求、匹配资料、生成初稿并写入文档编辑器。</p>
        </div>
        <el-tag :type="statusType" effect="dark">{{ statusText }}</el-tag>
      </section>

      <el-alert v-if="agent.error.value" :title="agent.error.value" type="error" show-icon :closable="false" />

      <ProjectDetailBidAgentTenderUpload />

      <el-button
        type="primary"
        :loading="agent.fullAnalysisLoading?.value"
        @click="agent.fetchFullAnalysis()"
        class="full-analysis-btn"
      >
        AI 评分标准一键解析
      </el-button>

      <BidAgentRiskSummary :summary="agent.riskSummary?.value" />

      <QualificationMatchPanel
        :knowledge-base-match="agent.knowledgeBaseMatch?.value"
        :loading="agent.fullAnalysisLoading?.value"
        :matched="!!agent.knowledgeBaseMatch?.value"
        @match="agent.fetchFullAnalysis()"
      />
      <TechnicalRequirementsPanel
        :items="agent.technicalRequirements?.value?.items"
        :loading="agent.fullAnalysisLoading?.value"
        :fetched="!!agent.knowledgeBaseMatch?.value"
        @fetch="agent.fetchFullAnalysis()"
      />
      <CommercialRequirementsPanel
        :items="agent.commercialRequirements?.value?.items"
        :loading="agent.fullAnalysisLoading?.value"
        :fetched="!!agent.knowledgeBaseMatch?.value"
        @fetch="agent.fetchFullAnalysis()"
      />
      <RiskRedLinePanel
        :items="agent.riskClassification?.value?.items"
        :fetched="!!agent.knowledgeBaseMatch?.value"
      />
      <ScoringCriteriaPanel
        :items="agent.scoringCriteria?.value?.structuredItems || agent.scoringCriteria?.value?.textItems || agent.scoringCriteria?.value?.items"
        :totalScore="agent.scoringCriteria?.value?.totalScore"
        :loading="agent.fullAnalysisLoading?.value"
        :fetched="!!agent.knowledgeBaseMatch?.value"
        @fetch="agent.fetchFullAnalysis()"
      />

      <el-dialog
        v-model="agent.showWorkbench.value"
        title="项目立项核对"
        fullscreen
        destroy-on-close
        :append-to-body="true"
        custom-class="workbench-dialog"
      >
        <DocVerificationWorkbench 
          v-if="agent.importResult.value"
          title="项目要求深度核对 (AI 证据驱动)"
          :schema="tenderSchema"
          :data="agent.importResult.value.requirementProfile"
          :requirements="agent.importResult.value.requirementProfile?.items"
          :markdown="agent.importResult.value.document?.extractedText || agent.importResult.value.requirementProfile?.rawMarkdown"
          @cancel="agent.showWorkbench.value = false"
          @confirm="agent.confirmWorkbench"
        />
      </el-dialog>

      <div class="agent-actions">
        <el-button type="primary" plain :loading="agent.creating.value" @click="agent.createRun()">使用已有项目资料生成</el-button>
        <el-button :disabled="!agent.currentRunId.value" :loading="agent.fetching.value" @click="agent.fetchRun()">刷新状态</el-button>
        <el-button type="success" :loading="agent.fullAnalysisLoading?.value" @click="agent.fetchFullAnalysis()">AI 评分标准一键解析</el-button>
      </div>

      <el-empty v-if="!run" description="尚未启动 AI 初稿生成" :image-size="96" />

      <template v-else>
        <section class="agent-section">
          <header>运行阶段</header>
          <ol class="stage-list">
            <li v-for="stage in displayStages" :key="stage.key || stage.title" :class="stageClass(stage.status)">
              <span class="stage-dot" />
              <div>
                <strong>{{ stage.title }}</strong>
                <p>{{ stage.message || getStageText(stage.status) }}</p>
              </div>
            </li>
          </ol>
        </section>

        <section v-if="warnings.length" class="agent-section">
          <header>风险与人工确认</header>
          <el-alert v-for="warning in warnings" :key="warning" :title="warning" type="warning" show-icon :closable="false" />
        </section>

        <section class="agent-section">
          <header>初稿章节</header>
          <div v-if="draftSections.length" class="draft-list">
            <article v-for="section in draftSections" :key="section.id || section.title" class="draft-card">
              <div class="draft-title-row">
                <h4>{{ section.title }}</h4>
                <el-tag v-if="section.confidence !== null && section.confidence !== undefined" size="small" type="success">{{ section.confidence }}%</el-tag>
              </div>
              <p>{{ section.content || '后端暂未返回章节正文预览' }}</p>
              <small v-if="section.source">来源：{{ section.source }}</small>
            </article>
          </div>
          <el-empty v-else description="等待后端返回初稿章节" :image-size="72" />
        </section>

        <section v-if="agent.applyResult.value" class="apply-result">
          <strong>写入结果</strong>
          <span>{{ applyResultText }}</span>
        </section>
      </template>

      <div class="drawer-footer">
        <el-button :disabled="!canReview" :loading="agent.reviewing.value" @click="agent.createReview()">发起审查</el-button>
        <el-button type="primary" :disabled="!canApply" :loading="agent.applying.value" @click="agent.applyBidAgentResult()">写入文档编辑器</el-button>
        <el-button v-if="agent.applyResult.value" type="success" plain tag="a" :href="editorHref" @click="openEditor">打开文档编辑器</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { useProjectDetailContext } from '@/composables/projectDetail/context.js'
import { useBidAgentDrawerView } from '@/composables/projectDetail/useBidAgentDrawerView.js'
import ProjectDetailBidAgentTenderUpload from './ProjectDetailBidAgentTenderUpload.vue'
import QualificationMatchPanel from './QualificationMatchPanel.vue'
import TechnicalRequirementsPanel from './TechnicalRequirementsPanel.vue'
import CommercialRequirementsPanel from './CommercialRequirementsPanel.vue'
import RiskRedLinePanel from './RiskRedLinePanel.vue'
import ScoringCriteriaPanel from './ScoringCriteriaPanel.vue'
import BidAgentRiskSummary from './BidAgentRiskSummary.vue'

import DocVerificationWorkbench from '../../common/doc-insight/DocVerificationWorkbench.vue'

const detail = useProjectDetailContext()
const agent = detail.bidAgent

const tenderSchema = {
  groups: [
    {
      id: 'basic',
      title: '基本信息',
      fields: [
        { key: 'projectName', label: '项目名称', type: 'string' },
        { key: 'purchaserName', label: '采购人', type: 'string' },
        { key: 'budget', label: '项目预算', type: 'number' }
      ]
    },
    {
      id: 'timeline',
      title: '关键节点',
      fields: [
        { key: 'publishDate', label: '发布日期', type: 'date' },
        { key: 'deadline', label: '投标截止', type: 'datetime' }
      ]
    }
  ]
}

const visible = computed({
  get: () => agent.drawerVisible.value,
  set: (value) => { agent.drawerVisible.value = value },
})

const {
  run,
  draftSections,
  displayStages,
  warnings,
  applyResultText,
  editorHref,
  canApply,
  canReview,
  statusText,
  statusType,
  openEditor,
  getStageText,
  stageClass,
} = useBidAgentDrawerView(detail, agent)
</script>

<style scoped>
.bid-agent-drawer {
  display: grid;
  gap: 18px;
}

.agent-hero {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 18px;
  border: 1px solid #d8e6df;
  border-radius: 18px;
  background: linear-gradient(135deg, #f5fbf7 0%, #eef6ee 55%, #fff8eb 100%);
}

.agent-eyebrow {
  margin: 0 0 6px;
  color: #5b7f64;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.agent-hero h3 {
  margin: 0 0 8px;
  color: #173d2a;
}

.agent-hero p,
.stage-list p,
.draft-card p {
  margin: 0;
  color: #5c6f65;
  line-height: 1.6;
}

.agent-actions,
.drawer-footer,
.draft-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.agent-section header {
  margin-bottom: 10px;
  color: #21372b;
  font-weight: 700;
}

.stage-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.stage-list li {
  display: grid;
  grid-template-columns: 14px 1fr;
  gap: 10px;
  padding: 12px;
  border-radius: 14px;
  background: #f7faf8;
}

.stage-dot {
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: #9ca3af;
}

.stage-completed .stage-dot,
.stage-ready .stage-dot,
.stage-applied .stage-dot {
  background: #13a46b;
}

.stage-running .stage-dot {
  background: #2f80ed;
}

.stage-failed .stage-dot, .stage-error .stage-dot { background: #d64545; }

.draft-list {
  display: grid;
  gap: 12px;
}

.draft-card {
  padding: 14px;
  border: 1px solid #e5ece8;
  border-radius: 16px;
  background: var(--bg-card);
}

.draft-title-row {
  justify-content: space-between;
  margin-bottom: 8px;
}

.draft-title-row h4 {
  margin: 0;
  color: #24382d;
}

.draft-card small { display: inline-block; margin-top: 10px; color: #789083; }

.apply-result {
  display: grid;
  gap: 4px;
  padding: 12px;
  border-radius: 14px;
  background: #effaf3;
  color: #23613f;
}

.drawer-footer {
  justify-content: flex-end;
}

.full-analysis-btn {
  width: 100%;
  font-weight: 600;
}
</style>
