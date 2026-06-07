<template>
  <el-card shadow="never" class="org-card">
    <template #header>
      <div class="panel-header">
        <div>
          <h3>用户组织归属</h3>
          <p>将用户绑定到真实部门和启用角色，任务候选人和 Dashboard 会同步生效。</p>
        </div>
      </div>
    </template>

    <el-empty v-if="users.length === 0" description="暂无用户，请先创建用户" />
    <el-table v-else :data="users" border stripe>
      <el-table-column prop="fullName" label="姓名" min-width="120" />
      <el-table-column prop="username" label="登录名" min-width="120" />
      <el-table-column prop="departmentName" label="部门" min-width="150" />
      <el-table-column prop="roleName" label="角色" min-width="130" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDialog(row)">调整归属</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="调整用户组织归属" width="520px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="部门" required>
          <el-select v-model="form.departmentCode" filterable placeholder="请选择部门" style="width: 100%">
            <el-option v-for="dept in deptOptions" :key="dept.value" :label="dept.label" :value="dept.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="form.roleId" filterable placeholder="请选择角色" style="width: 100%">
            <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  users: { type: Array, default: () => [] },
  deptOptions: { type: Array, default: () => [] },
  roles: { type: Array, default: () => [] },
  saveHandler: { type: Function, required: true }
})

const dialogVisible = ref(false)
const saving = ref(false)
const editingUserId = ref(null)
const form = ref({ departmentCode: '', roleId: null, enabled: true })

const openDialog = (user) => {
  editingUserId.value = user.id
  form.value = {
    departmentCode: user.departmentCode || '',
    roleId: user.roleId ?? null,
    enabled: Boolean(user.enabled)
  }
  dialogVisible.value = true
}

const submit = async () => {
  if (!form.value.departmentCode) return ElMessage.warning('请选择部门')
  if (!form.value.roleId) return ElMessage.warning('请选择角色')
  saving.value = true
  try {
    await props.saveHandler(editingUserId.value, form.value)
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '保存用户组织归属失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.org-card {
  border-radius: 16px;
}

.panel-header h3 {
  margin: 0;
}

.panel-header p {
  margin: 6px 0 0;
  color: #667085;
}
</style>
