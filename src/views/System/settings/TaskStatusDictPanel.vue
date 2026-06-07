<template>
  <div class="task-status-dict-panel">
    <div class="panel-header">
      <div class="panel-title-section">
        <h3 class="panel-title">任务状态字典</h3>
        <p class="panel-desc">管理全平台统一的任务状态列表（含已停用项）。停用不删除，历史任务保留。</p>
      </div>
      <el-button
        type="primary"
        :icon="Plus"
        data-test="new-status-btn"
        @click="openCreate"
      >新增状态</el-button>
    </div>

    <el-alert
      title="提示：使用上下箭头可调整顺序。停用不删除，历史任务状态仍保留该 Code。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="code"
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

      <el-table-column prop="code" label="Code" width="160" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column label="类别" width="160">
        <template #default="{ row }">{{ categoryLabel(row.category) }}</template>
      </el-table-column>

      <el-table-column label="颜色" width="140">
        <template #default="{ row }">
          <span class="color-chip" :style="{ background: row.color }" />
          <span class="color-hex">{{ row.color }}</span>
        </template>
      </el-table-column>

      <el-table-column label="初始" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.initial" type="success" size="small">是</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="终态" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.terminal" type="warning" size="small">是</el-tag>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
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
      :title="editingCode ? '编辑状态' : '新增状态'"
      width="540px"
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
// Input: taskStatusDictAdminApi for CRUD + reorder; projectStore for cache invalidation
// Output: admin panel for task status dictionary management
// Pos: src/views/System/settings/ - ADMIN-only settings panel
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, computed, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DynamicFormRenderer from '@/components/common/DynamicFormRenderer.vue'
import { taskStatusDictAdminApi } from '@/api/modules/taskStatusDictAdmin.js'
import { useProjectStore } from '@/stores/project'

const projectStore = useProjectStore()

const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const reordering = ref(false)

const dialogVisible = ref(false)
const editingCode = ref(null) // null = create; string = update
const editingForm = ref({})
const formRef = ref(null)

const CATEGORY_OPTIONS = [
  { label: '开放（OPEN）', value: 'OPEN' },
  { label: '进行中（IN_PROGRESS）', value: 'IN_PROGRESS' },
  { label: '审核（REVIEW）', value: 'REVIEW' },
  { label: '终态（CLOSED）', value: 'CLOSED' },
]

const formFields = computed(() => [
  {
    key: 'code',
    label: 'Code',
    type: 'text',
    required: true,
    placeholder: '大写字母+下划线+数字，例如 ARCHIVED',
    readonly: editingCode.value !== null,
  },
  { key: 'name', label: '显示名', type: 'text', required: true },
  { key: 'category', label: '类别', type: 'select', required: true, options: CATEGORY_OPTIONS },
  { key: 'color', label: '颜色', type: 'text', placeholder: '#67c23a（hex 格式）' },
  {
    key: 'isInitial',
    label: '设为初始状态',
    type: 'select',
    options: [
      { label: '否', value: false },
      { label: '是', value: true },
    ],
  },
  {
    key: 'isTerminal',
    label: '设为终态',
    type: 'select',
    options: [
      { label: '否', value: false },
      { label: '是', value: true },
    ],
  },
])

function rowClass({ row }) {
  return row.enabled ? '' : 'row-disabled'
}

function categoryLabel(cat) {
  return CATEGORY_OPTIONS.find((o) => o.value === cat)?.label ?? cat
}

async function load() {
  loading.value = true
  try {
    const res = await taskStatusDictAdminApi.list()
    rows.value = res?.data || []
  } catch (err) {
    ElMessage.error(`加载任务状态字典失败: ${err?.message || err}`)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingCode.value = null
  editingForm.value = {
    code: '',
    name: '',
    category: 'OPEN',
    color: 'var(--text-muted)',
    isInitial: false,
    isTerminal: false,
  }
  dialogVisible.value = true
}

function openEdit(row) {
  editingCode.value = row.code
  editingForm.value = {
    code: row.code,
    name: row.name,
    category: row.category,
    color: row.color,
    isInitial: row.initial,
    isTerminal: row.terminal,
    sortOrder: row.sortOrder,
  }
  dialogVisible.value = true
}

async function onSave() {
  const res = formRef.value?.submit?.()
  if (res && res.valid === false) return
  saving.value = true
  try {
    const dto = { ...editingForm.value }
    if (editingCode.value) {
      await taskStatusDictAdminApi.update(editingCode.value, dto)
      ElMessage.success('已更新')
    } else {
      await taskStatusDictAdminApi.create(dto)
      ElMessage.success('已新增')
    }
    projectStore.invalidateTaskStatuses()
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
      `确定要停用「${row.name}」吗？历史任务的状态不会变，只是字典里不再列出。`,
      '停用确认',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await taskStatusDictAdminApi.disable(row.code)
    projectStore.invalidateTaskStatuses()
    ElMessage.success('已停用')
    await load()
  } catch (err) {
    ElMessage.error(`停用失败: ${err?.response?.data?.msg || err?.message || err}`)
  }
}

async function onEnable(row) {
  try {
    await taskStatusDictAdminApi.enable(row.code)
    projectStore.invalidateTaskStatuses()
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
    { code: a.code, sortOrder: b.sortOrder },
    { code: b.code, sortOrder: a.sortOrder },
  ]
  reordering.value = true
  try {
    await taskStatusDictAdminApi.reorder(newItems)
    projectStore.invalidateTaskStatuses()
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
  editingCode,
  onSave,
  onDisable,
  onEnable,
  openEdit,
  openCreate,
  moveRow,
})
</script>

<style scoped>
.task-status-dict-panel {
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

.color-chip {
  display: inline-block;
  width: 20px;
  height: 14px;
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: middle;
  border: 1px solid #dcdfe6;
}

.color-hex {
  font-family: monospace;
  font-size: 12px;
}
</style>
