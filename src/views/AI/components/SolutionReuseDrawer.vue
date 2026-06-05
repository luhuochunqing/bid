<template>
  <el-drawer
    v-model="visible"
    :title="solution?.name || '方案详情'"
    size="50%"
    destroy-on-close
  >
    <template v-if="solution">
      <div class="drawer-content">
        <div class="meta-section">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="来源项目">{{ solution.projectName }}</el-descriptions-item>
            <el-descriptions-item label="行业">{{ solution.industry }}</el-descriptions-item>
            <el-descriptions-item label="日期">{{ solution.date }}</el-descriptions-item>
            <el-descriptions-item label="匹配度">
              <el-tag :type="solution.matchScore >= 80 ? 'success' : solution.matchScore >= 60 ? 'warning' : 'info'" size="small">
                {{ solution.matchScore }}%
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="content-section">
          <h3 class="section-title">方案内容</h3>
          <div class="content-body">
            <p>{{ solution.content || solution.description || '暂无详细内容' }}</p>
          </div>
        </div>

        <div class="actions-section">
          <el-button type="primary" @click="copyContent">
            <el-icon><CopyDocument /></el-icon>
            复制内容
          </el-button>
          <el-button @click="visible = false">关闭</el-button>
        </div>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  modelValue: Boolean,
  solution: Object
})

const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const copyContent = async () => {
  if (!props.solution) return
  const text = props.solution.content || props.solution.description || ''
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('内容已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择复制')
  }
}
</script>

<style scoped>
.drawer-content {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.meta-section {
  flex-shrink: 0;
}

.content-section {
  flex: 1;
  overflow: auto;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 12px 0;
  color: var(--text-primary);
}

.content-body {
  background: var(--bg-subtle);
  border-radius: 8px;
  padding: 20px;
  line-height: 1.8;
  font-size: 14px;
  color: var(--text-secondary);
  white-space: pre-wrap;
}

.actions-section {
  display: flex;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--gray-200);
}
</style>
