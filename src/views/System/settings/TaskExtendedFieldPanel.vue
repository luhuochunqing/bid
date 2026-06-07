<template>
  <div class="task-extended-field-panel">
    <div class="panel-header">
      <div class="panel-title-section">
        <h3 class="panel-title">任务扩展字段</h3>
        <p class="panel-desc">管理全平台统一的任务扩展字段（含已停用项）。停用不删除，历史任务保留 JSON 值。</p>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        data-test="new-field-btn"
        @click="openCreate"
      >新增字段</el-button>
    </div>

    <el-alert
      title="提示：使用上下箭头可调整顺序。停用不删除，历史任务扩展字段值仍会保留。select 类型必须提供 options JSON 数组。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="key"
      class="dict-table"
      :row-class-name="rowClass"
    >
      <el-table-column label="排序" width="90">
        <template #default="{ $index }">
          <div class="order-cell">
            <span class="order-index">{{ $index + 1 }}</span>
            <div class="order-arrows">
              <el-button
                link
                size="small"
                :disabled="$index === 0 || reordering"
                @click="moveRow($index, -1)"
              >↑</el-button>
              <el-button
                link
                size="small"
                :disabled="$index === rows.length - 1 || reordering"
                @click="moveRow($index, 1)"
              >↓</el-button>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="key" label="Key" width="180" />
      <el-table-column prop="label" label="显示名" width="160" />

      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <span class="type-cell">{{ row.fieldType }}</span>
        </template>
      </el-table-column>

      <el-table-column label="必填" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.required" type="danger" size="small">是</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="Options" width="110" align="center">
        <template #default="{ row }">
          <span class="options-cell">{{ optionsLabel(row.options) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="启用" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.enabled"
            link
            type="danger"
            size="small"
            @click="onDisable(row)"
          >停用</el-button>
          <el-button
            v-else
            link
            type="success"
            size="small"
            @click="onEnable(row)"
          >启用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editingKey ? '编辑扩展字段' : '新增扩展字段'"
      width="600px"
      :close-on-click-modal="false"
    >
      <DynamicFormRenderer
        ref="formRef"
        v-model="editingForm"
        :fields="formFields"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// Input: taskExtendedFieldAdminApi for CRUD + reorder; projectStore for cache invalidation
// Output: admin panel for task extended field schema management
// Pos: src/views/System/settings/ - ADMIN-only settings panel
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DynamicFormRenderer from '@/components/common/DynamicFormRenderer.vue'
import { taskExtendedFieldAdminApi } from '@/api/modules/taskExtendedFieldAdmin.js'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const reordering = ref(false)

const dialogVisible = ref(false)
const editingKey = ref(null) // null = create; string = update
const editingForm = ref({})
const formRef = ref(null)

const FIELD_TYPE_OPTIONS = [
  { label: '单行文本（text）', value: 'text' },
  { label: '多行文本（textarea）', value: 'textarea' },
  { label: '数字（number）', value: 'number' },
  { label: '日期（date）', value: 'date' },
  { label: '下拉选择（select）', value: 'select' },
]

const formFields = computed(() => [
  {
    key: 'key',
    label: 'Key',
    type: 'text',
    required: true,
    placeholder: 'snake_case，例如 tender_chapter',
    readonly: editingKey.value !== null,
  },
  { key: 'label', label: '显示名', type: 'text', required: true },
  {
    key: 'fieldType',
    label: '字段类型',
    type: 'select',
    required: true,
    options: FIELD_TYPE_OPTIONS,
  },
  {
    key: 'required',
    label: '必填',
    type: 'select',
    options: [
      { label: '否', value: false },
      { label: '是', value: true },
    ],
  },
  { key: 'placeholder', label: '占位提示', type: 'text' },
  {
    key: 'optionsJson',
    label: 'Options JSON（仅 select 类型）',
    type: 'textarea',
    placeholder: '[{"label":"高","value":"high"},{"label":"低","value":"low"}]',
  },
])

function rowClass({ row }) {
  return row.enabled ? '' : 'row-disabled'
}

function optionsLabel(options) {
  if (!Array.isArray(options) || options.length === 0) return '—'
  return `${options.length} 项`
}

async function load() {
  loading.value = true
  try {
    const res = await taskExtendedFieldAdminApi.list()
    rows.value = res?.data || []
  } catch (err) {
    ElMessage.error(`加载任务扩展字段失败: ${err?.message || err}`)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingKey.value = null
  editingForm.value = {
    key: '',
    label: '',
    fieldType: 'text',
    required: false,
    placeholder: '',
    optionsJson: '',
  }
  dialogVisible.value = true
}

function openEdit(row) {
  editingKey.value = row.key
  editingForm.value = {
    key: row.key,
    label: row.label,
    fieldType: row.fieldType,
    required: !!row.required,
    placeholder: row.placeholder || '',
    optionsJson: Array.isArray(row.options) && row.options.length
      ? JSON.stringify(row.options, null, 2)
      : '',
  }
  dialogVisible.value = true
}

async function onSave() {
  const res = formRef.value?.submit?.()
  if (res && res.valid === false) return

  // Parse optionsJson for select type
  let options = null
  if (editingForm.value.fieldType === 'select') {
    if (!editingForm.value.optionsJson?.trim()) {
      ElMessage.error('select 类型必须提供 options')
      return
    }
    try {
      options = JSON.parse(editingForm.value.optionsJson)
      if (!Array.isArray(options)) throw new Error('options 必须是数组')
    } catch (err) {
      ElMessage.error(`options JSON 解析失败: ${err.message}`)
      return
    }
  }

  saving.value = true
  try {
    const dto = {
      key: editingForm.value.key,
      label: editingForm.value.label,
      fieldType: editingForm.value.fieldType,
      required: !!editingForm.value.required,
      placeholder: editingForm.value.placeholder,
      options,
    }
    if (editingKey.value) {
      await taskExtendedFieldAdminApi.update(editingKey.value, dto)
      ElMessage.success('已更新')
    } else {
      await taskExtendedFieldAdminApi.create(dto)
      ElMessage.success('已新增')
    }
    projectStore.invalidateTaskExtendedFields()
    dialogVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(`保存失败: ${err?.response?.data?.msg || err?.message || err}`)
  } finally {
    saving.value = false
  }
}

async function onDisable(row) {
  try {
    await ElMessageBox.confirm(
      `确定要停用「${row.label}」吗？历史任务的扩展字段值不会删除，只是字典里不再列出。`,
      '停用确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await taskExtendedFieldAdminApi.disable(row.key)
    projectStore.invalidateTaskExtendedFields()
    ElMessage.success('已停用')
    await load()
  } catch (err) {
    ElMessage.error(`停用失败: ${err?.response?.data?.msg || err?.message || err}`)
  }
}

async function onEnable(row) {
  try {
    await taskExtendedFieldAdminApi.enable(row.key)
    projectStore.invalidateTaskExtendedFields()
    ElMessage.success('已启用')
    await load()
  } catch (err) {
    ElMessage.error(`启用失败: ${err?.response?.data?.msg || err?.message || err}`)
  }
}

async function moveRow(index, dir) {
  const target = index + dir
  if (target < 0 || target >= rows.value.length) return
  const a = rows.value[index]
  const b = rows.value[target]
  const newItems = [
    { key: a.key, sortOrder: b.sortOrder },
    { key: b.key, sortOrder: a.sortOrder },
  ]
  reordering.value = true
  try {
    await taskExtendedFieldAdminApi.reorder(newItems)
    projectStore.invalidateTaskExtendedFields()
    await load()
  } catch (err) {
    ElMessage.error(`重排失败: ${err?.response?.data?.msg || err?.message || err}`)
  } finally {
    reordering.value = false
  }
}

onMounted(load)

defineExpose({
  rows,
  editingForm,
  editingKey,
  onSave,
  onDisable,
  onEnable,
  openEdit,
  openCreate,
  moveRow,
})
</script>

<style scoped>
.task-extended-field-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.panel-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--gray-750);
}

.panel-desc {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.dict-table {
  width: 100%;
}

.dict-table :deep(.row-disabled) {
  color: #c0c4cc;
  background: #fafafa;
}

.order-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}

.order-arrows {
  display: flex;
  flex-direction: column;
}

.type-cell {
  font-family: monospace;
  font-size: 12px;
  color: var(--text-secondary-ui);
}

.options-cell {
  color: var(--text-secondary-ui);
  font-size: 12px;
}
</style>
