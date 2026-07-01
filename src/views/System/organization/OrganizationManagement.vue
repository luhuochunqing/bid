<template>
  <div class="organization-page">
    <div class="page-header">
      <div>
        <p class="page-kicker">Organization</p>
        <h2>组织架构管理</h2>
      </div>
      <el-tag v-if="totalCount >= 0" type="info" effect="plain">
        共 {{ totalCount }} 名员工 | {{ departments.length }} 个部门
      </el-tag>
    </div>

    <el-card shadow="never" class="main-card">
      <!-- 左侧部门树 + 右侧用户表格 -->
      <div class="org-layout">
        <!-- 部门侧边栏 -->
        <div class="dept-sidebar">
          <div class="dept-header">
            <h4>部门列表</h4>
            <el-select v-model="selectedSourceApp" size="small" placeholder="全部来源" style="width: 110px" @change="loadDepartments">
              <el-option label="全部来源" value="" />
              <el-option label="ehsy" value="ehsy" />
              <el-option label="oss" value="oss" />
            </el-select>
          </div>
          <div class="dept-tree-wrapper">
            <el-tree
              ref="deptTreeRef"
              :data="deptTree"
              :props="{ label: 'departmentName', children: 'children' }"
              node-key="departmentCode"
              default-expand-all
              highlight-current
              @node-click="onDeptClick"
              :expand-on-click-node="true"
            >
              <template #default="{ node, data }">
                <span class="dept-node">
                  <span>{{ data.departmentName }}</span>
                  <el-tag v-if="node.level === 1" size="small" type="success" effect="plain">{{ data.departmentCode }}</el-tag>
                </span>
              </template>
            </el-tree>
          </div>
        </div>

        <!-- 用户列表主区域 -->
        <div class="user-main">
          <div class="toolbar">
            <div class="toolbar-left">
              <el-input
                v-model="searchForm.keyword"
                placeholder="搜索姓名/用户名/邮箱/手机号"
                clearable
                style="width: 280px"
                @input="debouncedSearch"
                @clear="debouncedSearch"
              >
                <template #prefix><el-icon><Search /></el-icon></template>
              </el-input>
              <el-select v-model="searchForm.enabled" placeholder="状态" clearable style="width: 100px" @change="loadUsers">
                <el-option label="全部" :value="null" />
                <el-option label="启用" :value="true" />
                <el-option label="停用" :value="false" />
              </el-select>
            </div>
          </div>

          <el-table
            v-loading="loading"
            :data="userList"
            stripe
            border
            style="width: 100%"
            max-height="560"
          >
            <el-table-column prop="fullName" label="姓名" width="120" fixed />
            <el-table-column prop="username" label="用户名" width="120" />
            <el-table-column prop="email" label="邮箱" min-width="200" />
            <el-table-column prop="phone" label="手机号" width="130" />
            <el-table-column label="部门" width="160">
              <template #default="{ row }">
                <el-select
                  v-if="editingDeptId === row.id"
                  v-model="editDeptCode"
                  @change="onDeptSelectChange"
                  size="small"
                  style="width: 130px"
                >
                  <el-option
                    v-for="d in departments"
                    :key="d.departmentCode"
                    :label="d.departmentName"
                    :value="d.departmentCode"
                  />
                </el-select>
                <span v-else>
                  {{ row.departmentName || '-' }}
                  <el-button v-if="showEdit" text type="primary" size="small" @click="startEditDept(row)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="roleName" label="角色" width="100" />
            <el-table-column label="CRM 工号" width="170">
              <template #default="{ row }">
                <div v-if="editingCrmId === row.id" class="crm-edit-cell">
                  <el-input
                    v-model="editCrmValue"
                    size="small"
                    placeholder="CRM 工号"
                    maxlength="64"
                    style="width: 110px"
                    @keyup.enter="saveCrmSalesNo(row)"
                    @keyup.esc="cancelEditCrm"
                  />
                  <el-button text type="primary" size="small" :loading="crmLoading === row.id" @click="saveCrmSalesNo(row)">保存</el-button>
                  <el-button text size="small" @click="cancelEditCrm">取消</el-button>
                </div>
                <span v-else>
                  {{ row.crmSalesNo || '-' }}
                  <el-button text type="primary" size="small" @click="startEditCrm(row)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  :loading="statusLoading === row.id"
                  @change="(val) => onStatusChange(row, val)"
                />
              </template>
            </el-table-column>
            <el-table-column label="同步来源" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.externalOrgUserId" size="small" type="info" effect="plain">组织架构</el-tag>
                <el-tag v-else size="small" type="warning" effect="plain">本地</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :total="totalCount"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              small
              @current-change="loadUsers"
              @size-change="loadUsers"
            />
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { Search, Edit } from '@element-plus/icons-vue'
import { organizationApi } from '@/api/modules/organization.js'
import { useUserCrmEdit } from './useUserCrmEdit.js'
import { useDeptEdit } from './useDeptEdit.js'

const loading = ref(false)
const userList = ref([])
const departments = ref([])
const totalCount = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

// CO-152 Review D2-2: 部门行内编辑 + 状态切换（抽到 composable 控制文件行数）
const { editingDeptId, editDeptCode, statusLoading, startEditDept, onDeptSelectChange, onStatusChange } = useDeptEdit(departments, userList)
// CO-152: CRM 工号行内编辑（抽到 composable 控制文件行数）
const { editingCrmId, editCrmValue, crmLoading, startEditCrm, cancelEditCrm, saveCrmSalesNo } = useUserCrmEdit()

const searchForm = ref({ keyword: '', enabled: null })
const selectedDeptCode = ref('')
const selectedSourceApp = ref('ehsy')

let debounceTimer = null
function debouncedSearch() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { currentPage.value = 1; loadUsers() }, 300)
}

const showEdit = computed(() => departments.value.length > 0)

const ROOT_CODE = 'rootorg'

function buildSubTree(parentCode, list, visited = new Set(), depth = 0) {
  if (depth > 20) return []
  const children = list.filter(d => d.parentDepartmentCode === parentCode && !visited.has(d.departmentCode))
  return children.map(c => {
    const nextVisited = new Set(visited)
    nextVisited.add(c.departmentCode)
    return {
      ...c,
      children: buildSubTree(c.departmentCode, list, nextVisited, depth + 1)
    }
  })
}

const deptTree = computed(() => {
  const list = departments.value
  const root = list.find(d => d.departmentCode === ROOT_CODE)
  if (root) {
    return [{
      ...root,
      children: buildSubTree(ROOT_CODE, list, new Set([ROOT_CODE]))
    }]
  }
  // fallback: 无 rootorg 时以 parent 为空或指向自身的节点为根
  const roots = list.filter(d => !d.parentDepartmentCode || d.parentDepartmentCode === d.departmentCode)
  return roots.map(r => ({
    ...r,
    children: buildSubTree(r.departmentCode, list, new Set([r.departmentCode]))
  }))
})

function onDeptClick(data) {
  selectedDeptCode.value = data.departmentCode === selectedDeptCode.value ? '' : data.departmentCode
  currentPage.value = 1
  loadUsers()
}

async function loadUsers() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchForm.value.keyword || undefined,
      enabled: searchForm.value.enabled,
      departmentCode: selectedDeptCode.value || undefined,
      sourceApp: selectedDeptCode.value ? (selectedSourceApp.value || undefined) : undefined,
    }
    const res = await organizationApi.listUsersPage(params)
    userList.value = res.list || []
    totalCount.value = res.totalCount || 0
  } catch {
    userList.value = []
    totalCount.value = 0
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await organizationApi.listDepartments(selectedSourceApp.value || undefined)
    departments.value = Array.isArray(res) ? res : []
  } catch {
    departments.value = []
  }
}

onMounted(async () => {
  await loadDepartments()
  await loadUsers()
})
</script>

<style scoped>
.organization-page { padding: 0; }
.page-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 0; }
.page-kicker { margin: 0 0 4px; color: #6d7d5d; font-size: 12px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; }
.page-header h2 { margin: 0; color: #1f2d1d; font-size: 20px; }
.main-card { border-radius: 8px; }
.org-layout { display: flex; gap: 0; min-height: 500px; }
.dept-sidebar { width: 260px; min-width: 260px; border-right: 1px solid var(--el-border-color-light); padding: 0 12px 12px 0; }
.dept-header { display: flex; align-items: center; justify-content: space-between; padding: 8px 4px 12px; border-bottom: 1px solid var(--el-border-color-lighter); margin-bottom: 8px; }
.dept-header h4 { margin: 0; font-size: 14px; color: #374151; }
.dept-tree-wrapper { overflow-y: auto; max-height: 520px; }
.dept-node { display: flex; align-items: center; gap: 6px; font-size: 13px; }
:deep(.el-tree-node__content) { height: 36px; }
.user-main { flex: 1; padding: 0 0 0 16px; min-width: 0; }
.toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.toolbar-left { display: flex; gap: 8px; align-items: center; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 16px; }
:deep(.el-switch) { --el-switch-on-color: var(--el-color-success); }
.crm-edit-cell { display: flex; align-items: center; gap: 4px; }
</style>
