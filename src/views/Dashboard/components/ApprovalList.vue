<!-- Input: Workbench ApprovalList props and user actions
Output: presentational Workbench ApprovalList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card approvals-card" :class="{ 'side-balance-card': compact }">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <path d="M9 11l3 3L22 4"/>
          <path d="M21 12v7a2 2 0 01-2 2H5a2 2 0 01-2-2V5a2 2 0 012-2h11"/>
        </svg>
        {{ title }}
      </h3>
      <el-tag size="small" type="danger">{{ count ?? approvals.length }}</el-tag>
    </div>
    <EmptyState
      v-if="error"
      state="error"
      icon="!"
      title="审批加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="approvals.length === 0"
      icon="审"
      title="暂无待审批事项"
      description="当前没有需要处理的审批，新的申请会出现在这里。"
    />
    <div v-else class="approvals-list" :class="{ compact }">
      <div v-for="item in approvals" :key="item.id" class="approval-item">
        <div class="approval-icon" :class="`type-${item.type}`">
          <el-icon><Document /></el-icon>
        </div>
        <div class="approval-content">
          <span class="approval-title">{{ item.title }}</span>
          <span class="approval-meta">{{ item.department }} · {{ item.time }}</span>
        </div>
        <div class="approval-actions">
          <el-button size="small" type="success" @click="emit('approve', item)">通过</el-button>
          <el-button size="small" @click="emit('reject', item)">驳回</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Document } from '@element-plus/icons-vue'
import EmptyState from './EmptyState.vue'

defineProps({
  title: { type: String, default: '待审批事项' },
  approvals: { type: Array, default: () => [] },
  count: { type: Number, default: null },
  compact: { type: Boolean, default: true },
  error: { type: String, default: '' },
})

const emit = defineEmits(['approve', 'reject', 'retry'])
</script>
