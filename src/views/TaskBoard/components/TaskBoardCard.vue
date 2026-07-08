<template>
  <div
    class="task-card"
    :class="{ 'task-high': item.priority === 'HIGH', 'task-review': item.type === 'BID_REVIEW' }"
    @click="emit('task-click', item)"
  >
    <div class="task-header">
      <div class="header-tags">
        <el-tag v-if="item.type" :type="item.type === 'BID_REVIEW' ? 'danger' : 'info'" size="small" class="type-tag">
          {{ item.type === 'BID_REVIEW' ? '标书审核' : '任务' }}
        </el-tag>
        <el-tag v-if="item.priority" :type="priorityType" size="small">
          {{ priorityText }}
        </el-tag>
        <el-tag v-if="item.type === 'TASK' && hasDeliverable(item)" type="success" size="small">已上传交付物</el-tag>
        <el-tag v-else-if="item.type === 'TASK' && !hasDeliverable(item)" type="warning" size="small">交付物必填</el-tag>
      </div>
    </div>
    <div class="task-name">{{ item.title }}</div>
    <div class="task-desc" v-if="item.description">{{ item.description }}</div>
    <div class="task-meta">
      <div class="task-owner" v-if="item.assigneeName || item.submitterName">
        <el-icon><User /></el-icon>
        <span>{{ item.assigneeName || item.submitterName }}</span>
      </div>
      <div class="task-deadline" :class="{ 'deadline-urgent': isUrgent }" v-if="item.dueDate">
        <el-icon><Calendar /></el-icon>
        <span>{{ formattedDate }}</span>
      </div>
    </div>
    <div class="task-project" v-if="item.projectName">
      <el-icon><OfficeBuilding /></el-icon>
      <span>{{ item.projectName }}</span>
    </div>

    <!-- BID_REVIEW：标书文件列表（只读下载，包 overflow-x 防止撑破列宽） -->
    <div v-if="item.type === 'BID_REVIEW' && item.projectId" class="bid-review-documents" @click.stop>
      <ProjectDocumentTable :project-id="item.projectId" readonly />
    </div>

    <!-- BID_REVIEW 操作：通过/驳回 -->
    <TaskBoardBidReviewActions
      v-if="item.type === 'BID_REVIEW'"
      :item="item"
      @click.stop
      @deliverable-changed="(t) => emit('deliverable-changed', t)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { User, Calendar, OfficeBuilding } from '@element-plus/icons-vue'
import ProjectDocumentTable from '@/views/Project/stages/components/ProjectDocumentTable.vue'
import TaskBoardBidReviewActions from './TaskBoardBidReviewActions.vue'

const props = defineProps({
  item: { type: Object, required: true },
  availableStatuses: { type: Array, required: true }
})
const emit = defineEmits(['status-change', 'deliverable-changed', 'task-click'])

function hasDeliverable(task) {
  return Array.isArray(task?.deliverables) && task.deliverables.length > 0
}

const PRIORITY_TYPE_MAP = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const PRIORITY_TEXT_MAP = { HIGH: '高', MEDIUM: '中', LOW: '低' }
const priorityType = computed(() => PRIORITY_TYPE_MAP[props.item.priority] || 'info')
const priorityText = computed(() => PRIORITY_TEXT_MAP[props.item.priority] || props.item.priority)

const isUrgent = computed(() => {
  if (!props.item.dueDate) return false
  const diff = new Date(props.item.dueDate) - new Date()
  return diff > 0 && diff < 3 * 24 * 60 * 60 * 1000
})

const formattedDate = computed(() => {
  const d = new Date(props.item.dueDate)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
})
</script>

<style scoped lang="scss">
.task-card {
  background: #fff;
  border-radius: 6px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  transition: box-shadow 0.2s ease;
  cursor: pointer;
  min-width: 0;
  &:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12); }
  &.task-high { border-left: 3px solid #f56c6c; }
  &.task-review { border-left: 3px solid #e6a23c; }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  .header-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .type-tag { flex-shrink: 0; }
}

.task-name { font-size: 14px; font-weight: 500; margin-bottom: 6px; color: #303133; word-break: break-all; }
.task-desc { font-size: 12px; color: #909399; margin-bottom: 8px; line-height: 1.4; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;
  .task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
  .deadline-urgent { color: #f56c6c; }
}

.task-project { display: flex; align-items: center; gap: 4px; margin-top: 8px; font-size: 12px; color: #909399; word-break: break-all; .el-icon { flex-shrink: 0; } }
.bid-review-documents { margin-top: 8px; overflow-x: auto; }
:deep(.project-documents) { margin-top: 8px; .el-card__header { padding: 8px 12px; } .el-card__body { padding: 8px; } }
</style>
