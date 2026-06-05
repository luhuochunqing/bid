<template>
  <div class="org-management-panel">
    <el-row :gutter="24">
      <!-- Left: Org Tree -->
      <el-col :span="8">
        <el-card shadow="never" class="tree-card">
          <template #header>
            <div class="card-header">
              <span class="title">组织架构树</span>
              <el-button type="primary" link @click="handleAddDept(null)">
                <el-icon><Plus /></el-icon> 新增根部门
              </el-button>
            </div>
          </template>
          
          <el-tree
            ref="treeRef"
            :data="deptTree"
            node-key="id"
            default-expand-all
            :expand-on-click-node="false"
            highlight-current
            @node-click="handleNodeClick"
          >
            <template #default="{ data }">
              <span class="custom-tree-node">
                <span class="node-label">
                  <el-icon v-if="data.children && data.children.length"><OfficeBuilding /></el-icon>
                  <el-icon v-else><Folder /></el-icon>
                  {{ data.name }}
                </span>
                <span class="node-ops">
                  <el-button link type="primary" icon="Plus" @click.stop="handleAddDept(data)" />
                  <el-button 
                    link 
                    type="danger" 
                    icon="Delete" 
                    v-if="data.id !== 'D001'"
                    @click.stop="handleDeleteDept(data)" 
                  />
                </span>
              </span>
            </template>
          </el-tree>
        </el-card>
      </el-col>
      
      <!-- Right: Detail & Members -->
      <el-col :span="16">
        <el-card shadow="never" class="detail-card">
          <template #header>
            <div class="card-header">
              <span class="title">部门详情 {{ selectedDept ? ` - ${selectedDept.name}` : '' }}</span>
            </div>
          </template>
          
          <div v-if="selectedDept">
            <el-form :model="editForm" label-width="120px" label-position="left">
              <el-form-item label="部门名称" required>
                <el-input v-model="editForm.name" placeholder="请输入部门名称" />
              </el-form-item>
              <el-form-item label="部门编码">
                <el-input v-model="editForm.id" placeholder="请输入部门编码 (如 D001)" :disabled="!!selectedDept && selectedDept.id" />
              </el-form-item>
              <el-form-item label="上级部门">
                <el-tree-select
                  v-model="editForm.parentId"
                  :data="deptTree"
                  node-key="id"
                  :props="{ label: 'name', value: 'id' }"
                  check-strictly
                  placeholder="项目部级"
                  style="width: 100%"
                  :disabled="editForm.id === 'D001'"
                />
              </el-form-item>
              <el-form-item label="部门负责人">
                <el-select v-model="editForm.managerId" placeholder="请选择负责人" style="width: 100%" clearable>
                  <el-option 
                    v-for="user in userOptions" 
                    :key="user.id" 
                    :label="user.name" 
                    :value="user.id" 
                  />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="handleSave">更新详情</el-button>
              </el-form-item>
            </el-form>
            
            <el-divider />
            
            <div class="member-list">
              <div class="list-header">
                <span class="sub-title">部门成员 ({{ deptUsers.length }})</span>
              </div>
              <el-table :data="deptUsers" border stripe size="small" style="margin-top: 12px">
                <el-table-column prop="name" label="姓名" width="120" />
                <el-table-column prop="role" label="角色">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.role === 'admin' ? 'danger' : 'info'">{{ row.role }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100">
                  <template #default>
                    <el-button link type="primary" size="small">更换部门</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
          
          <el-empty v-else description="请从左侧选择部门以查看详情" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, OfficeBuilding, Folder } from '@element-plus/icons-vue'

const props = defineProps({
  depts: { type: Array, required: true },
  users: { type: Array, required: true }
})

const emit = defineEmits(['update:depts'])

const treeRef = ref(null)
const selectedDept = ref(null)
const editForm = ref({
  id: '',
  name: '',
  parentId: null,
  managerId: null,
  isNew: false
})

// Build tree structure
const deptTree = computed(() => {
  const map = {}
  props.depts.forEach(d => {
    map[d.id] = { ...d, children: [] }
  })
  const roots = []
  props.depts.forEach(d => {
    if (d.parentId && map[d.parentId]) {
      map[d.parentId].children.push(map[d.id])
    } else {
      roots.push(map[d.id])
    }
  })
  return roots
})

const userOptions = computed(() => props.users)

const deptUsers = computed(() => {
  if (!selectedDept.value) return []
  return props.users.filter(u => u.deptId === selectedDept.value.id)
})

watch(selectedDept, (newVal) => {
  if (newVal) {
    editForm.value = { ...newVal }
  }
})

function handleNodeClick(data) {
  selectedDept.value = data
}

function handleAddDept(parent) {
  const newDept = {
    id: '', // Leave empty to let user fill or generate
    name: '新部门',
    parentId: parent ? parent.id : null,
    managerId: null,
    isNew: true
  }
  
  selectedDept.value = newDept
  editForm.value = { ...newDept }
  ElMessage.info('请填写部门名称和编码，然后保存')
}

function handleDeleteDept(data) {
  ElMessageBox.confirm(`确定要删除部门 "${data.name}" 吗？其子部门也将受影响。`, '警告', {
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(() => {
    const nextDepts = props.depts.filter(d => d.id !== data.id && d.parentId !== data.id)
    emit('update:depts', nextDepts)
    selectedDept.value = null
    ElMessage.success('部门已删除')
  }).catch(() => {})
}

function handleSave() {
  if (!editForm.value.name) return ElMessage.error('名称必填')
  if (!editForm.value.id) return ElMessage.error('编码必填')
  
  const nextDepts = [...props.depts]
  const idx = nextDepts.findIndex(d => d.id === editForm.value.id)
  
  if (idx > -1) {
    nextDepts[idx] = { ...editForm.value, isNew: false }
  } else {
    nextDepts.push({ ...editForm.value, isNew: false })
  }
  
  emit('update:depts', nextDepts)
  selectedDept.value = { ...editForm.value, isNew: false }
  ElMessage.success('详情已更新，请点击页面底部“保存配置”同步到系统')
}
</script>

<style scoped>
.org-management-panel {
  padding: 12px 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-weight: 600;
  color: var(--gray-750);
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-secondary-ui);
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-right: 8px;
}

.node-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-ops {
  opacity: 0;
  transition: opacity 0.2s;
}

.el-tree-node__content:hover .node-ops {
  opacity: 1;
}

.tree-card {
  min-height: 500px;
}

.detail-card {
  min-height: 500px;
}

.member-list {
  background: #f8f9fb;
  padding: 16px;
  border-radius: 8px;
}
</style>
