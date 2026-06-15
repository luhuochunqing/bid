<!-- Input: Workbench ProjectList props and user actions
Output: presentational Workbench ProjectList section
Pos: src/views/Dashboard/components/ - Dashboard display components
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="section-card projects-card">
    <div class="section-header">
      <h3 class="section-title">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="section-icon">
          <rect x="2" y="7" width="20" height="14" rx="2" ry="2"/>
          <path d="M16 21V5a2 2 0 00-2-2h-4a2 2 0 00-2 2v16"/>
        </svg>
        {{ title }}
      </h3>
      <el-link v-if="showViewAll" type="primary" underline="hover" @click="emit('view-all')">
        {{ viewAllLabel }}
        <el-icon class="el-icon--right"><ArrowRight /></el-icon>
      </el-link>
    </div>
    <EmptyState
      v-if="error"
      state="error"
      icon="!"
      title="项目加载失败"
      :description="error"
      action-label="重试"
      @action="emit('retry')"
    />
    <EmptyState
      v-else-if="projects.length === 0"
      icon="项"
      title="暂无进行中项目"
      description="当前筛选下没有需要跟进的项目。"
      :action-label="showViewAll ? viewAllLabel : ''"
      @action="emit('view-all')"
    />
    <div v-else class="projects-list">
      <div
        v-for="project in projects"
        :key="project.id"
        class="project-card"
        role="button"
        tabindex="0"
        @click="selectProject(project)"
        @keydown.enter.prevent="selectProject(project)"
        @keydown.space.prevent="selectProject(project)"
      >
        <div class="project-progress-ring">
          <svg viewBox="0 0 36 36" class="progress-ring">
            <path
              class="progress-ring-bg"
              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              fill="none"
              stroke="#E5E7EB"
              stroke-width="3"
            />
            <path
              class="progress-ring-fill"
              :stroke-dasharray="`${project.progress}, 100`"
              d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
              fill="none"
              :stroke="resolveProgressColor(project.progress)"
              stroke-width="3"
              stroke-linecap="round"
            />
          </svg>
          <span class="progress-text">{{ project.progress }}%</span>
        </div>
        <div class="project-info">
          <h4 class="project-name">{{ project.name }}</h4>
          <div class="project-meta">
            <span v-for="field in metaFields" :key="field" class="meta-tag">
              <el-icon><component :is="field === 'manager' ? User : Calendar" /></el-icon>
              {{ project[field] }}
            </span>
          </div>
        </div>
        <div class="project-actions">
          <el-button 
            circle 
            size="small" 
            class="share-btn" 
            @click="handleShare($event, project)"
          >
            <el-icon><Share /></el-icon>
          </el-button>
          <el-tag :type="resolveStatusType(project.status)" size="small">{{ project.status }}</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight, Calendar, User, Share } from '@element-plus/icons-vue'
import { getProjectStatusType } from '@/views/Dashboard/workbench-formatters.js'
import EmptyState from './EmptyState.vue'

const props = defineProps({
  title: { type: String, default: '进行中项目' },
  projects: { type: Array, default: () => [] },
  showViewAll: { type: Boolean, default: true },
  viewAllLabel: { type: String, default: '查看全部' },
  metaFields: { type: Array, default: () => ['deadline', 'manager'] },
  progressColorResolver: { type: Function, default: null },
  statusTypeResolver: { type: Function, default: null },
  error: { type: String, default: '' },
})

const emit = defineEmits(['view-all', 'project-click', 'share-click', 'retry'])
const selectProject = (project) => emit('project-click', project)
const handleShare = (event, project) => {
  event.stopPropagation()
  emit('share-click', project)
}

const defaultProgressColor = (progress) => {
  if (progress >= 80) return 'var(--color-success-dark)'
  if (progress >= 50) return '#3B82F6'
  if (progress >= 20) return '#F59E0B'
  return 'var(--color-danger)'
}

const resolveProgressColor = (progress) => (
  props.progressColorResolver ? props.progressColorResolver(progress) : defaultProgressColor(progress)
)

const resolveStatusType = (status) => (
  props.statusTypeResolver ? props.statusTypeResolver(status) : getProjectStatusType(status)
)
</script>

<style scoped>
.project-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.share-btn {
  opacity: 0;
  transition: opacity 0.2s;
}
.project-card:hover .share-btn {
  opacity: 1;
}
</style>
