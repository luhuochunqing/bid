<template>
  <el-card shadow="never" class="org-card">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>部门树维护</h3>
          <p>统一维护任务分配、数据权限和用户归属使用的部门主数据。</p>
        </div>
        <div class="header-actions">
          <el-button @click="addRow">新增部门</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存部门树</el-button>
        </div>
      </div>
    </template>

    <el-alert
      v-if="rows.length === 0"
      title="尚未配置部门树，请先新增至少一个部门。"
      type="warning"
      show-icon
      :closable="false"
      class="empty-alert"
    />

    <el-table :data="rows" border stripe size="small" height="520">
      <el-table-column label="部门编码" min-width="150">
        <template #default="{ row }">
          <el-input v-model="row.deptCode" placeholder="如 SALES" />
        </template>
      </el-table-column>
      <el-table-column label="部门名称" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.deptName" placeholder="如 销售部" />
        </template>
      </el-table-column>
      <el-table-column label="上级部门" min-width="180">
        <template #default="{ row }">
          <el-tree-select
            v-model="row.parentDeptCode"
            :data="parentTreeOptions"
            node-key="deptCode"
            :props="{ label: 'deptName', value: 'deptCode' }"
            check-strictly
            clearable
            placeholder="根部门"
            filterable
            style="width: 100%"
          />
        </template>
      </el-table-column>
      <el-table-column label="成员数" width="100">
        <template #default="{ row }">
          <el-tag>{{ memberCountMap[row.deptCode] ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ $index, row }">
          <el-button link type="danger" @click="removeRow($index, row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { normalizeDeptTree } from './organization-normalizers'

const props = defineProps({
  deptTree: { type: Array, default: () => [] },
  users: { type: Array, default: () => [] },
  saveHandler: { type: Function, required: true }
})

const rows = ref([])
const saving = ref(false)

watch(
  () => props.deptTree,
  (value) => {
    rows.value = normalizeDeptTree(value).map((item) => ({ ...item }))
  },
  { immediate: true, deep: true }
)

const parentTreeOptions = computed(() => buildDeptTree(rows.value))

const memberCountMap = computed(() => {
  const map = {}
  for (const user of props.users || []) {
    const code = user.departmentCode
    if (code) {
      map[code] = (map[code] || 0) + 1
    }
  }
  return map
})

function buildDeptTree(depts) {
  const map = {}
  const roots = []
  for (const dept of depts) {
    if (!dept.deptCode) continue
    map[dept.deptCode] = { ...dept, children: [] }
  }
  for (const dept of depts) {
    if (!dept.deptCode) continue
    const parentCode = dept.parentDeptCode
    if (parentCode && parentCode !== dept.deptCode && map[parentCode]) {
      map[parentCode].children.push(map[dept.deptCode])
    } else {
      roots.push(map[dept.deptCode])
    }
  }
  return roots
}

const addRow = () => {
  rows.value.push({
    deptCode: '',
    deptName: '',
    parentDeptCode: '',
    sortOrder: rows.value.length
  })
}

const removeRow = (index, row) => {
  if ((memberCountMap.value[row.deptCode] || 0) > 0) {
    ElMessage.warning('该部门仍有关联用户，请先迁移用户归属')
    return
  }
  rows.value = rows.value.filter((_, i) => i !== index)
}

const validate = () => {
  const codes = new Set()
  for (const row of rows.value) {
    if (!row.deptCode || !row.deptName) throw new Error('部门编码和名称不能为空')
    if (codes.has(row.deptCode)) throw new Error(`部门编码重复：${row.deptCode}`)
    if (row.parentDeptCode === row.deptCode) throw new Error('上级部门不能指向自身')
    codes.add(row.deptCode)
  }
}

const submit = async () => {
  saving.value = true
  try {
    validate()
    await props.saveHandler(normalizeDeptTree(rows.value))
  } catch (error) {
    ElMessage.error(error?.message || '保存部门树失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.org-card {
  border-radius: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-start;
}

.panel-header h3 {
  margin: 0;
}

.panel-header p {
  margin: 6px 0 0;
  color: #667085;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.empty-alert {
  margin-bottom: 16px;
}
</style>
