<template>
  <el-card class="task-card">
    <template #header>
      <div class="card-title">
        <div class="title-main">
          <el-icon><List /></el-icon>
          <span>任务看板</span>
        </div>
        <div class="actions">
          <el-button
            v-if="canManageProjectTasks"
            link
            type="success"
            class="header-action header-action--tender"
            :icon="DocumentChecked"
            data-test="tender-breakdown-button"
            @click="$emit('tender-breakdown')"
          >
            AI评分标准解析
          </el-button>
          <el-button
            v-if="canManageProjectTasks"
            link
            type="warning"
            class="header-action header-action--score"
            :icon="DocumentChecked"
            data-test="score-draft-button"
            @click="$emit('score-draft-decompose')"
          >
            AI 自动拆解任务
          </el-button>
          <el-button
            v-if="canManageProjectTasks"
            link
            type="primary"
            class="header-action header-action--add"
            :icon="Plus"
            data-test="add-task-button"
            @click="handleAddTaskClick"
          >
            添加任务
          </el-button>
          <el-button
            v-if="isDemoMode"
            link
            type="warning"
            @click="$emit('reset-tasks')"
          >
            重置任务
          </el-button>
        </div>
      </div>
    </template>

    <TaskBoard
      :tasks="tasks"
      :project-id="normalizedProjectId"
      :can-generate="!tasks || tasks.length === 0"
      @task-click="handleTaskClick"
      @status-change="(...args) => $emit('status-change', ...args)"
      @generate-tasks="$emit('generate-tasks')"
      @add-deliverable="(...args) => $emit('add-deliverable', ...args)"
      @remove-deliverable="(...args) => $emit('remove-deliverable', ...args)"
      @submit-to-document="$emit('submit-to-document', $event)"
    />

    <el-drawer
      v-model="drawerVisible"
      :title="drawerTitle"
      size="520px"
      direction="rtl"
      :destroy-on-close="true"
    >
      <TaskForm
        ref="taskFormRef"
        v-model="editingTask"
        :mode="drawerMode"
      />
      <template #footer>
        <div class="drawer-footer">
          <el-button data-test="task-drawer-cancel" @click="handleCancelTask">取消</el-button>
          <el-button
            v-if="drawerMode !== 'view'"
            type="primary"
            data-test="task-drawer-save"
            @click="handleSaveTask"
          >
            保存
          </el-button>
        </div>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, getCurrentInstance, ref } from 'vue'
import { DocumentChecked, List, Plus } from '@element-plus/icons-vue'
import TaskBoard from '@/components/common/TaskBoard.vue'
import TaskForm from '@/components/project/TaskForm.vue'

const emit = defineEmits([
  'add-task',
  'reset-tasks',
  'task-click',
  'status-change',
  'open-score-parse',
  'open-decompose',
  'tender-breakdown',
  'score-draft-decompose',
  'generate-tasks',
  'add-deliverable',
  'remove-deliverable',
  'submit-to-document',
  'save-task',
])

const props = defineProps({
  tasks: {
    type: Array,
    default: () => [],
  },
  projectId: {
    type: [String, Number],
    default: '',
  },
  canManageProjectTasks: {
    type: Boolean,
    default: false,
  },
  isDemoMode: {
    type: Boolean,
    default: false,
  },
})

const normalizedProjectId = computed(() => String(props.projectId ?? ''))

const drawerVisible = ref(false)
const drawerMode = ref('create')
const editingTask = ref({})
const taskFormRef = ref(null)
const instance = getCurrentInstance()

const drawerTitle = computed(() => {
  if (drawerMode.value === 'edit') return '编辑任务'
  if (drawerMode.value === 'view') return '任务详情'
  return '新增任务'
})

function openCreate() {
  drawerMode.value = 'create'
  editingTask.value = {}
  drawerVisible.value = true
}

function openView(task) {
  drawerMode.value = 'view'
  editingTask.value = { ...(task || {}) }
  drawerVisible.value = true
}

function handleAddTaskClick() {
  openCreate()
}

function handleTaskClick(task) {
  emit('task-click', task)
  openView(task)
}

function handleCancelTask() {
  drawerVisible.value = false
}

async function handleSaveTask() {
  // Regression for IJSVX7：view mode 下不应触发保存。分配人/执行人点开已有任务时
  // drawer 为只读，不暴露"保存"按钮；即便外部残留调用直达 handleSaveTask，也要兜底退出。
  if (drawerMode.value === 'view') {
    drawerVisible.value = false
    return
  }
  const form = taskFormRef.value
  const result = form && typeof form.submit === 'function'
    ? form.submit()
    : { valid: true, data: { ...editingTask.value } }
  if (!result || result.valid === false) return
  const done = () => { drawerVisible.value = false }
  emit('save-task', { mode: drawerMode.value, data: result.data, done })
  if (!instance?.vnode.props?.onSaveTask) done()
}

defineExpose({
  drawerVisible,
  drawerMode,
  editingTask,
  taskFormRef,
  openCreate,
  openView,
  handleSaveTask,
  handleCancelTask,
})
</script>

<style scoped>
.task-card {
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 500;
}

.title-main,
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.header-action.el-button.is-link {
  min-height: 32px;
  padding: 0 10px;
  border-radius: 7px;
  font-weight: 600;
  transition: background-color 0.16s ease, color 0.16s ease, box-shadow 0.16s ease;
}

.header-action--tender.el-button.is-link {
  --el-button-text-color: #23785d;
  --el-button-hover-link-text-color: #14684d;
  --el-button-active-color: #10563f;
}

.header-action--score.el-button.is-link {
  --el-button-text-color: #98620a;
  --el-button-hover-link-text-color: #805005;
  --el-button-active-color: #6c4200;
}

.header-action--add.el-button.is-link {
  --el-button-text-color: #2f6fba;
  --el-button-hover-link-text-color: #225b9b;
  --el-button-active-color: #1d4c82;
}

.header-action.el-button.is-link:hover,
.header-action.el-button.is-link:focus {
  background: #f4f8f6;
}

.header-action--score.el-button.is-link:hover,
.header-action--score.el-button.is-link:focus {
  background: #fff7e8;
}

.header-action--add.el-button.is-link:hover,
.header-action--add.el-button.is-link:focus {
  background: #eef5ff;
}

.header-action.el-button.is-link:focus-visible {
  box-shadow: 0 0 0 3px rgba(35, 120, 93, 0.16);
  outline: none;
}

.header-action--score.el-button.is-link:focus-visible {
  box-shadow: 0 0 0 3px rgba(152, 98, 10, 0.16);
}

.header-action--add.el-button.is-link:focus-visible {
  box-shadow: 0 0 0 3px rgba(47, 111, 186, 0.16);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
