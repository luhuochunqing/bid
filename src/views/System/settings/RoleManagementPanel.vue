<!-- Input: user data, system roles, department options, and action handlers
Output: presentational Role Management panel with grouped menu permission editing
Pos: src/views/System/settings/ - System settings panels
一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。 -->
<template>
  <div class="role-management-panel">
    <div class="panel-header">
      <div class="header-content">
        <h3 class="panel-title">角色权限管理</h3>
        <p class="panel-desc">定义系统角色及其对应的功能菜单访问权限</p>
      </div>
      <el-button type="primary" plain @click="handleAddRole">
        <el-icon><Plus /></el-icon>
        新增角色
      </el-button>
    </div>

    <el-table :data="roles" style="width: 100%" border>
      <el-table-column prop="name" label="角色名称" width="180" />
      <el-table-column prop="code" label="权限编码" width="150">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.code }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="角色描述" show-overflow-tooltip />
      <el-table-column label="菜单权限" min-width="300">
        <template #default="{ row }">
          <div class="permission-tags">
            <el-tag
              v-for="perm in row.menuPermissions"
              :key="perm"
              size="small"
              class="perm-tag"
            >
              {{ getMenuLabel(perm) }}
            </el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button-group>
            <el-button link type="primary" @click="handleEditRole(row)">编辑</el-button>
            <el-button link type="danger" @click="toggleHandler(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="warning" @click="resetHandler(row)">重置</el-button>
          </el-button-group>
        </template>
      </el-table-column>
    </el-table>

    <!-- Role Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑角色' : '新增角色'"
      width="600px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" class="role-form">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" placeholder="如：销售经理" />
        </el-form-item>
        <el-form-item label="权限编码" required>
          <el-input v-model="form.code" placeholder="如：ROLE_SALES_MGR" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="角色描述">
          <el-input v-model="form.description" type="textarea" rows="2" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="form.dataScope" placeholder="请选择数据范围">
            <el-option label="全部数据" value="all" />
            <el-option label="本部门数据" value="dept" />
            <el-option label="个人数据" value="self" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单权限">
          <div class="permission-tree">
            <div
              v-for="group in menuGroups"
              :key="group.value"
              class="permission-group"
            >
              <el-checkbox
                :model-value="isGroupChecked(group)"
                :indeterminate="isGroupIndeterminate(group)"
                @change="(checked) => handleGroupChange(group, checked)"
              >
                {{ group.label }}
              </el-checkbox>
              <div v-if="group.children.length" class="permission-children">
                <el-checkbox
                  v-for="child in group.children"
                  :key="child.value"
                  :model-value="hasPermission(child.value)"
                  @change="(checked) => handleChildChange(group, child, checked)"
                >
                  {{ child.label }}
                </el-checkbox>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRole">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { roleMenuGroups, roleMenuOptions } from '@/config/sidebar-menu'
import {
  isRolePermissionGroupChecked,
  isRolePermissionGroupIndeterminate,
  normalizeRoleMenuPermissions,
  setRolePermissionChild,
  setRolePermissionGroup
} from './role-menu-permission-tree'

const props = defineProps({
  roles: { type: Array, default: () => [] },
  deptOptions: { type: Array, default: () => [] },
  saveHandler: { type: Function, required: true },
  toggleHandler: { type: Function, required: true },
  resetHandler: { type: Function, required: true }
})

const dialogVisible = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

const menuGroups = roleMenuGroups
const menuOptions = roleMenuOptions

function emptyForm() {
  return {
    id: null,
    code: '',
    name: '',
    description: '',
    dataScope: 'self',
    menuPermissions: []
  }
}

const getMenuLabel = (value) => {
  const opt = menuOptions.find(o => o.value === value)
  return opt ? opt.label : value
}

const handleAddRole = () => {
  form.value = emptyForm()
  dialogVisible.value = true
}

const handleEditRole = (role) => {
  form.value = {
    ...role,
    menuPermissions: normalizeRoleMenuPermissions(Array.isArray(role.menuPermissions) ? [...role.menuPermissions] : [])
  }
  dialogVisible.value = true
}

const hasPermission = (value) => form.value.menuPermissions.includes(value)

const isGroupChecked = (group) => isRolePermissionGroupChecked(form.value.menuPermissions, group)

const isGroupIndeterminate = (group) => isRolePermissionGroupIndeterminate(form.value.menuPermissions, group)

const handleGroupChange = (group, checked) => {
  form.value.menuPermissions = setRolePermissionGroup(form.value.menuPermissions, group, Boolean(checked))
}

const handleChildChange = (group, child, checked) => {
  form.value.menuPermissions = setRolePermissionChild(form.value.menuPermissions, group, child, Boolean(checked))
}

const saveRole = async () => {
  if (!form.value.name.trim()) return ElMessage.warning('请填写角色名称')
  saving.value = true
  try {
    await props.saveHandler({
      ...form.value,
      menuPermissions: normalizeRoleMenuPermissions(form.value.menuPermissions)
    })
    dialogVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '保存角色失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.role-management-panel {
  padding: 20px;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}
.panel-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
}
.panel-desc {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.perm-tag {
  margin: 2px;
}
.role-form {
  padding-right: 20px;
}
.permission-tree {
  width: 100%;
  display: grid;
  gap: 12px;
}
.permission-group {
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.permission-group:last-child {
  border-bottom: 0;
}
.permission-children {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin-top: 8px;
  padding-left: 24px;
}
</style>
