<template>
  <el-form ref="formRef" :model="taskForm" label-width="120px">
    <div class="task-step-heading">
      <el-divider content-position="left">任务分解</el-divider>
      <el-button
        type="primary"
        :icon="Connection"
        :loading="decomposing"
        @click="$emit('auto-decompose')"
      >
        自动拆解任务
      </el-button>
    </div>

    <div class="task-list">
      <div v-for="(task, index) in taskForm.tasks" :key="index" class="task-item">
        <el-card>
          <template #header>
            <div class="task-header">
              <span>任务 {{ index + 1 }}</span>
              <el-button
                v-if="taskForm.tasks.length > 1"
                link
                type="danger"
                :icon="Delete"
                @click="$emit('remove-task', index)"
              >
                删除
              </el-button>
            </div>
          </template>
          <el-form :model="task" label-width="100px">
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="任务名称">
                  <el-input v-model="task.name" placeholder="请输入任务名称" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="负责人">
                  <el-select v-model="task.owner" placeholder="请选择负责人">
                    <el-option
                      v-for="user in userList"
                      :key="user.id"
                      :label="user.name"
                      :value="user.name"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="截止日期">
                  <el-date-picker
                    v-model="task.deadline"
                    type="date"
                    placeholder="请选择日期"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="优先级">
                  <el-select v-model="task.priority" placeholder="请选择优先级">
                    <el-option label="高" value="high" />
                    <el-option label="中" value="medium" />
                    <el-option label="低" value="low" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </el-card>
      </div>
    </div>

    <el-button :icon="Plus" class="add-task-btn" @click="$emit('add-task')">添加任务</el-button>
  </el-form>
</template>

<script setup>
import { ref } from 'vue'
import { Connection, Plus, Delete } from '@element-plus/icons-vue'

defineProps({
  taskForm: { type: Object, required: true },
  userList: { type: Array, default: () => [] },
  decomposing: { type: Boolean, default: false }
})

defineEmits(['add-task', 'remove-task', 'auto-decompose'])

const formRef = ref(null)

async function validate() {
  return true
}

defineExpose({ validate })
</script>

<style scoped>
.task-list { margin-bottom: 16px; }

.task-step-heading {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 16px;
}

.task-item { margin-bottom: 16px; }

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.add-task-btn {
  width: 100%;
  border-style: dashed;
}

@media (max-width: 768px) {
  .task-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .task-step-heading {
    grid-template-columns: 1fr;
  }
}

@media (hover: none) and (pointer: coarse) {
  .add-task-btn { min-height: 44px; }
  .task-item { min-height: 60px; }
  .task-item:active { background: var(--bg-subtle); }
}
</style>
