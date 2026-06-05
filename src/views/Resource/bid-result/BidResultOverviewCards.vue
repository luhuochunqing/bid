<template>
  <el-row :gutter="16" class="overview-row">
    <el-col :xs="24" :sm="12" :lg="6">
      <el-card shadow="hover" class="overview-card">
        <div class="icon system"><el-icon><Connection /></el-icon></div>
        <h3>内部系统同步</h3>
        <p>同步内部系统的中标结果，日志与数据入库全流程打通。</p>
        <el-button type="primary" plain :loading="syncing" @click="$emit('sync')">立即同步</el-button>
        <div class="foot">最近同步：{{ overview.lastSyncTime || '暂无' }}</div>
      </el-card>
    </el-col>

    <el-col :xs="24" :sm="12" :lg="6">
      <el-card shadow="hover" class="overview-card">
        <div class="icon fetch"><el-icon><Search /></el-icon></div>
        <h3>公开结果同步</h3>
        <p>生成待确认结果，进入人工核验和补录。</p>
        <el-button type="success" plain :loading="fetching" @click="$emit('fetch')">开始同步</el-button>
        <div class="foot">{{ overview.pendingCount }} 条待确认</div>
      </el-card>
    </el-col>

    <el-col :xs="24" :sm="12" :lg="6">
      <el-card shadow="hover" class="overview-card">
        <div class="icon remind"><el-icon><Bell /></el-icon></div>
        <h3>资料上传提醒</h3>
        <p>提醒销售上传通知书或分析报告，完成闭环。</p>
        <el-button type="warning" plain :loading="sending" @click="$emit('send-all-reminders')">全部提醒</el-button>
        <div class="foot">{{ overview.uploadPending }} 条待上传</div>
      </el-card>
    </el-col>

    <el-col :xs="24" :sm="12" :lg="6">
      <el-card shadow="hover" class="overview-card">
        <div class="icon report"><el-icon><DataAnalysis /></el-icon></div>
        <h3>竞争对手报表</h3>
        <p>基于已录入竞对信息汇总 SKU、品类、折扣、账期。</p>
        <el-button type="danger" plain :loading="reportLoading" @click="$emit('show-report')">查看报表</el-button>
        <div class="foot">{{ overview.competitorCount }} 条竞争记录</div>
      </el-card>
    </el-col>
  </el-row>
</template>

<script setup>
import { Bell, Connection, DataAnalysis, Search } from '@element-plus/icons-vue'

defineProps({
  overview: {
    type: Object,
    required: true
  },
  syncing: Boolean,
  fetching: Boolean,
  sending: Boolean,
  reportLoading: Boolean
})

defineEmits(['sync', 'fetch', 'send-all-reminders', 'show-report'])
</script>

<style scoped>
.overview-row {
  margin-bottom: 16px;
}

.overview-card {
  height: 100%;
}

.overview-card h3 {
  margin: 0 0 8px;
}

.overview-card p {
  min-height: 40px;
  color: var(--text-secondary-ui);
  font-size: 13px;
}

.icon {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bg-card);
  font-size: 20px;
  margin-bottom: 12px;
}

.system { background: #409eff; }
.fetch { background: #67c23a; }
.remind { background: #e6a23c; }
.report { background: #f56c6c; }

.foot {
  margin-top: 10px;
  font-size: 12px;
  color: var(--text-muted);
}
</style>
