<template>
  <el-dialog
    v-model="visible"
    title="使用模板"
    width="620px"
  >
    <div v-if="template" class="use-template-content">
      <el-alert
        :title="`正在使用模板: ${template.name}`"
        type="success"
        :closable="false"
        show-icon
        style="margin-bottom: 20px"
      />

      <el-form :model="form" label-width="120px">
        <el-form-item label="创建文档类型">
          <el-radio-group v-model="form.docType">
            <el-radio v-for="option in docTypeOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.docType !== 'standalone'" label="关联项目">
          <el-select
            v-model="form.projectId"
            aria-label="关联项目"
            placeholder="选择关联项目（可选）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="project in projects"
              :key="project.id"
              :label="`${project.name} (${project.customer})`"
              :value="project.id"
            >
              <div class="project-option">
                <span class="project-name">{{ project.name }}</span>
                <span class="project-customer">{{ project.customer }}</span>
                <el-tag size="small" :type="getProjectStatusType(project.status)">
                  {{ getProjectStatusLabel(project.status) }}
                </el-tag>
              </div>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="文档名称" required>
          <el-input v-model="form.docName" aria-label="文档名称" placeholder="请输入文档名称" />
        </el-form-item>

        <el-form-item label="应用方式">
          <el-checkbox-group v-model="form.applyOptions">
            <el-checkbox value="content">应用模板内容</el-checkbox>
            <el-checkbox value="format">保留格式设置</el-checkbox>
            <el-checkbox value="styles">应用样式风格</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :icon="Check" @click="$emit('confirm')">确认使用</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { Check } from '@element-plus/icons-vue'
import { getProjectStatusLabel, getProjectStatusType, USE_TEMPLATE_DOC_TYPE_OPTIONS } from './templateLibraryHelpers.js'

const visible = defineModel('visible', { type: Boolean, default: false })
defineModel('form', { type: Object, required: true })
defineProps({
  template: { type: Object, default: null },
  projects: { type: Array, default: () => [] }
})

defineEmits(['confirm'])

const docTypeOptions = USE_TEMPLATE_DOC_TYPE_OPTIONS
</script>

<style scoped>
.project-option {
  display: flex;
  align-items: center;
  gap: 8px;
}

.project-name {
  font-weight: 600;
}

.project-customer {
  color: var(--text-muted);
  font-size: 12px;
}
</style>
