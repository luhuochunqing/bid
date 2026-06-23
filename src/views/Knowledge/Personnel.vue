<template>
  <div class="personnel-container">
    <div class="page-header">
      <h2>人员库 — 投标团队成员管理</h2>
      <div class="header-actions">
        <div class="primary-actions">
          <el-button v-if="canAdd" type="primary" @click="openForm(null)">
            <el-icon><Plus /></el-icon> 新增人员
          </el-button>
        </div>
        <div class="batch-actions" v-if="canBatch">
          <el-button v-if="canImportExport" @click="importDialogVisible = true">
            <el-icon><Upload /></el-icon> 批量导入
          </el-button>
          <el-button v-if="canImportExport" @click="exportDialogVisible = true">
            <el-icon><Download /></el-icon> 批量导出
          </el-button>
          <el-button @click="attachDialogVisible = true">
            <el-icon><Link /></el-icon> 批量关联附件
          </el-button>
        </div>
      </div>
    </div>

    <el-card class="filter-card">
      <div class="filter-title">筛选条件</div>
      <el-form :inline="true" :model="filters" class="filter-form">
        <el-form-item label="姓名/工号">
          <el-input v-model="filters.keyword" placeholder="实时搜索姓名或工号" clearable style="width:180px" @input="debouncedLoad(280)" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="filters.gender" placeholder="全部" clearable style="width:90px" @change="loadData">
            <el-option v-for="g in GENDER_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:110px" @change="loadData">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="持有证书">
          <el-input v-model="filters.certificateKeyword" placeholder="如：建造师" clearable style="width:160px" @input="debouncedLoad(320)" />
        </el-form-item>
        <el-form-item label="最高学历">
          <el-select v-model="filters.highestEducations" multiple collapse-tags placeholder="多选" style="width:210px" @change="loadData">
            <el-option v-for="e in EDUCATION_OPTIONS" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item label="学习形式">
          <el-select v-model="filters.studyForms" multiple collapse-tags placeholder="多选" style="width:180px" @change="loadData">
            <el-option v-for="s in STUDY_FORM_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="filters.majorKeyword" placeholder="专业模糊" clearable style="width:140px" @input="debouncedLoad(320)" />
        </el-form-item>
        <el-form-item label="入职时间">
          <el-date-picker
            v-model="entryDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width:240px"
            @change="onEntryDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="证书状态">
          <el-select v-model="filters.certificateStatuses" multiple collapse-tags placeholder="多选" style="width:210px" @change="loadData">
            <el-option v-for="c in CERT_STATUS_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">刷新</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" v-loading="loading">
      <el-table :data="records" stripe style="width:100%" @row-click="openDetail">
        <el-table-column type="selection" width="55" />
        <el-table-column type="index" label="序号" width="110" align="center" />
        <el-table-column prop="employeeNumber" label="工号" width="90" align="center">
          <template #default="{row}"><span class="emp-no">{{ row.employeeNumber }}</span></template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="性别" width="70" align="center">
          <template #default="{row}">
            <el-tag v-if="row.gender" :type="row.gender==='男'?'primary':'danger'" size="small">{{ row.gender }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="入职时间" width="120" align="center">
          <template #default="{row}">{{ row.entryDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="入职年限" width="120" align="center">
          <template #default="{row}">
            <span v-if="row.yearsOfService != null">{{ row.yearsOfService }} 年</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号码" width="120" />
        <el-table-column label="最高学历" width="120" align="center">
          <template #default="{row}">
            <el-tag v-if="row.highestEducation && row.highestEducation !== '-'" size="small" type="info">{{ row.highestEducation }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="证书数量" width="120" align="center">
          <template #default="{row}">
            <el-tag :type="row.certificateCount > 0 ? 'success' : 'info'" class="cert-count-clickable" @click.stop="openDetail(row, 'certificate')">
              {{ row.certificateCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="即将到期" width="120" align="center">
          <template #default="{row}">
            <span v-if="row.expiringCertificatesCount > 0" class="expiry-warn" @click.stop="openDetail(row, 'certificate')">
              <el-icon><Warning /></el-icon> {{ row.expiringCertificatesCount }}
            </span>
            <span v-else class="expiry-ok">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.status==='ACTIVE'?'success':row.status==='TERMINATED'?'danger':'info'">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click.stop="openForm(row)">编辑</el-button>
            <template v-if="row.status === 'INACTIVE'">
              <el-button type="success" link size="small" @click.stop="handleRestore(row)">恢复</el-button>
            </template>
            <template v-else>
              <el-button type="danger" link size="small" @click.stop="openDeleteDialog(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <PersonnelDetailDrawer
      v-model="detailVisible"
      :personnel="current"
      :can-edit="canEdit"
      @edit="openFormFromDetail"
    />

    <PersonnelFormDialog
      v-model="formVisible"
      :personnel="editingPersonnel"
      @saved="onFormSaved"
    />

    <PersonnelDeleteDialog
      v-model="deleteDialogVisible"
      :personnel="deleteTarget"
      @deleted="loadData"
    />

    <PersonnelImportDialog
      v-model="importDialogVisible"
      @imported="loadData"
    />

    <PersonnelExportDialog v-model="exportDialogVisible" />

    <PersonnelBatchAttachDialog
      v-model="attachDialogVisible"
      @attached="loadData"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { isBidManager } from '@/utils/permission'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Warning, Download, Upload, Link } from '@element-plus/icons-vue'
import personnelApi from '@/api/modules/personnel.js'


import {
  GENDER_OPTIONS, EDUCATION_OPTIONS, STUDY_FORM_OPTIONS,
  CERT_STATUS_OPTIONS, STATUS_OPTIONS
} from './components/personnel/personnelConstants.js'
import { usePersonnelFilters } from './components/personnel/usePersonnelFilters.js'
import PersonnelDetailDrawer from './components/personnel/PersonnelDetailDrawer.vue'
import PersonnelFormDialog from './components/personnel/PersonnelFormDialog.vue'
import PersonnelDeleteDialog from './components/personnel/PersonnelDeleteDialog.vue'
import PersonnelImportDialog from './components/personnel/PersonnelImportDialog.vue'
import PersonnelExportDialog from './components/personnel/PersonnelExportDialog.vue'
import PersonnelBatchAttachDialog from './components/personnel/PersonnelBatchAttachDialog.vue'

const userStore = useUserStore()

const {
  records, loading, filters, entryDateRange,
  loadData, debouncedLoad, resetFilters, onEntryDateRangeChange
} = usePersonnelFilters(personnelApi)

const detailVisible = ref(false)
const current = ref({})
const formVisible = ref(false)
const editingPersonnel = ref(null)
const deleteDialogVisible = ref(false)
const deleteTarget = ref(null)
const importDialogVisible = ref(false)
const exportDialogVisible = ref(false)
const attachDialogVisible = ref(false)

const userRole = computed(() => userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || '')
const canAdd = computed(() => isBidManager(userRole.value) || userRole.value === 'bid-Team')
const canImportExport = computed(() => isBidManager(userRole.value))
const canBatch = computed(() => isBidManager(userRole.value) || userRole.value === 'bid-Team')
const canEdit = computed(() => isBidManager(userRole.value) || userRole.value === 'bid-Team')

function openDetail(row, targetTab = 'basic') {
  if (!row?.id) return
  current.value = row
  detailVisible.value = true
}

function openForm(row) {
  editingPersonnel.value = row
  formVisible.value = true
}

function openFormFromDetail() {
  detailVisible.value = false
  setTimeout(() => openForm(current.value), 300)
}

function onFormSaved() {
  loadData()
}

function openDeleteDialog(row) {
  deleteTarget.value = row
  deleteDialogVisible.value = true
}

async function handleRestore(row) {
  try {
    await ElMessageBox.confirm(`确认恢复人员「${row.name}」？`, '恢复确认')
    await personnelApi.restore(row.id)
    ElMessage.success('恢复成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('恢复失败')
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; h2{font-weight:600;color:var(--el-text-color-primary);margin:0} }
.header-actions { display: flex; gap: 12px; align-items: center; }
.primary-actions, .batch-actions { display: flex; gap: 8px; }
.filter-card,.table-card{ border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05); }
.expiry-warn{color:var(--el-color-warning);display:flex;align-items:center;gap:4px;font-size:13px}
.expiry-ok{color:var(--el-color-success);font-size:13px}
.emp-no { font-weight: 600; color: var(--el-text-color-primary); }
.cert-count-clickable { cursor: pointer; user-select: none; }
.cert-count-clickable:hover { opacity: 0.85; }
.new-person-highlight {
  background-color: var(--el-color-primary-light-9) !important;
  transition: background-color 0.3s;
}
</style>
