<template>
  <div class="project-group-settings-panel">
    <el-table :data="projectGroups" size="small" border>
      <el-table-column prop="groupName" label="项目组" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.groupName" size="small" placeholder="请输入项目组名称" />
        </template>
      </el-table-column>
      <el-table-column prop="managerUserId" label="负责人" width="160">
        <template #default="{ row }">
          <el-select v-model="row.managerUserId" size="small" style="width: 100%" @change="syncMeta(row)">
            <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="memberCount" label="成员数" width="80" align="center" />
      <el-table-column prop="visibility" label="可见范围" width="140">
        <template #default="{ row }">
          <el-select v-model="row.visibility" size="small" @change="handleVisibilityChange(row)">
            <el-option v-for="option in visibilityOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="allowedRoles" label="可访问角色" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.allowedRoles" multiple size="small" style="width: 100%" :disabled="row.visibility !== 'custom'">
            <el-option v-for="option in roleOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="memberUserIds" label="组成员" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.memberUserIds" multiple size="small" style="width: 100%" @change="syncMeta(row)">
            <el-option v-for="user in userOptions" :key="user.id" :label="user.name" :value="user.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column prop="projectIds" label="绑定项目" min-width="260">
        <template #default="{ row }">
          <el-select v-model="row.projectIds" multiple size="small" style="width: 100%">
            <el-option v-for="option in projectScopeOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row, $index }">
          <el-button type="primary" size="small" @click="$emit('save-group', row, $index)">保存</el-button>
          <el-button type="danger" link size="small" @click="$emit('delete-group', row, $index)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="margin-top: 12px;">
      <el-button type="primary" plain @click="$emit('add-group')">新增项目组</el-button>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  projectGroups: { type: Array, required: true },
  userOptions: { type: Array, required: true },
  projectScopeOptions: { type: Array, required: true }
})

defineEmits(['add-group', 'save-group', 'delete-group'])

const visibilityOptions = [
  { label: '全员可见', value: 'all' },
  { label: '项目组成员', value: 'members' },
  { label: '仅负责人', value: 'manager' },
  { label: '自定义角色', value: 'custom' }
]

const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '经理', value: 'manager' },
  { label: '普通员工', value: 'staff' }
]

const resolveUserName = (userId) => {
  const match = props.userOptions.find((user) => String(user.id) === String(userId))
  return match?.name || ''
}

const syncMeta = (row) => {
  row.manager = resolveUserName(row.managerUserId)
  row.memberCount = Array.isArray(row.memberUserIds) ? row.memberUserIds.length : 0
}

const handleVisibilityChange = (row) => {
  if (row.visibility === 'manager') {
    row.allowedRoles = []
    row.memberUserIds = []
  }
  if (row.visibility !== 'custom') {
    row.allowedRoles = []
  }
  syncMeta(row)
}
</script>
