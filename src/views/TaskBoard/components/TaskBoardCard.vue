<template>
  <div
    class="task-card"
    :class="{ 'task-high': item.priority === 'HIGH', 'task-review': item.type === 'BID_REVIEW' }"
    @click="$emit('click', item)"
  >
    <div class="task-header">
      <div class="header-tags">
        <el-tag v-if="item.type" :type="item.type === 'BID_REVIEW' ? 'danger' : 'info'" size="small" class="type-tag">
          {{ item.type === 'BID_REVIEW' ? '标书审核' : '任务' }}
        </el-tag>
        <el-tag v-if="item.priority" :type="priorityType" size="small">
          {{ priorityText }}
        </el-tag>
      </div>
      <el-dropdown v-if="canUpdate" trigger="click" @click.stop>
        <el-icon class="more-icon"><MoreFilled /></el-icon>
        <template #dropdown>
          <el-dropdown-item
            v-for="s in availableStatuses"
            :key="s.code"
            :disabled="item.status === s.code"
            @click="$emit('status-change', item, s.code)"
          >
            设为{{ s.name }}
          </el-dropdown-item>
        </template>
      </el-dropdown>
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
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { MoreFilled, User, Calendar, OfficeBuilding } from '@element-plus/icons-vue'

const props = defineProps({
  item: { type: Object, required: true },
  availableStatuses: { type: Array, required: true }
})

defineEmits(['click', 'status-change'])

const PRIORITY_TYPE_MAP = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
const PRIORITY_TEXT_MAP = { HIGH: '高', MEDIUM: '中', LOW: '低' }

const priorityType = computed(() => PRIORITY_TYPE_MAP[props.item.priority] || 'info')
const priorityText = computed(() => PRIORITY_TEXT_MAP[props.item.priority] || props.item.priority)
const canUpdate = computed(() => props.item.type === 'TASK' && !!props.item.id)

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
  cursor: pointer;
  transition: box-shadow 0.2s ease, transform 0.1s ease;

  &:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12); }
  &.task-high { border-left: 3px solid #f56c6c; }
  &.task-review { border-left: 3px solid #e6a23c; }
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;

  .header-tags { display: flex; align-items: center; gap: 6px; }
  .type-tag { flex-shrink: 0; }
  .more-icon {
    cursor: pointer;
    color: #909399;
    &:hover { color: #409eff; }
  }
}

.task-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 6px;
  color: #303133;
}

.task-desc {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #606266;

  .task-owner, .task-deadline { display: flex; align-items: center; gap: 4px; }
  .deadline-urgent { color: #f56c6c; }
}

.task-project {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  font-size: 12px;
  color: #909399;

  .el-icon { flex-shrink: 0; }
}
</style>
