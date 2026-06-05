<template>
  <div class="performance-container">
    <div class="page-header">
      <div class="header-left">
        <h2>业绩管理</h2>
        <span class="sub-title">合同台账与到期提醒中心</span>
      </div>
      <div class="header-right">
        <el-button class="ghost-btn" @click="openAlertConfig">
          <el-icon class="btn-icon"><Bell /></el-icon> 提醒配置
        </el-button>
        <el-button type="primary" class="gradient-btn" @click="openForm(null)">
          <el-icon class="btn-icon"><Plus /></el-icon> 新增业绩
        </el-button>
        <el-button disabled class="ghost-btn">
          <el-icon class="btn-icon"><Upload /></el-icon> 批量导入
        </el-button>
        <el-button disabled class="ghost-btn">
          <el-icon class="btn-icon"><Download /></el-icon> 导出
        </el-button>
      </div>
    </div>

    <!-- 过滤器 -->
    <el-card class="filter-card border-glow">
      <el-form :inline="true" :model="filters" class="demo-form-inline">
        <el-form-item label="模糊搜索">
          <el-input v-model="filters.keyword" placeholder="合同名称/签约单位/集团名称" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select v-model="filters.customerType" placeholder="全部" clearable style="width: 160px">
            <el-option label="政府机关/事业单位" value="GOVERNMENT_INSTITUTION" /><el-option label="央企" value="CENTRAL_SOE" />
            <el-option label="地方国企" value="LOCAL_SOE" /><el-option label="民企" value="PRIVATE_ENTERPRISE" />
            <el-option label="港澳台/外企" value="FOREIGN_HK_MACAO_TW" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-select v-model="filters.projectType" placeholder="全部" clearable style="width: 120px">
            <el-option label="办公" value="OFFICE" /><el-option label="综合" value="COMPREHENSIVE" />
            <el-option label="集采" value="CENTRALIZED" /><el-option label="工业品" value="INDUSTRIAL" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="履约中" value="IN_PERFORMANCE" /><el-option label="即将到期" value="EXPIRING" /><el-option label="已到期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="属地">
          <el-input v-model="filters.territory" placeholder="省/市关键词" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="签约日期">
          <el-date-picker v-model="filters.signingDateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="filters.expiryDateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="中标通知书">
          <el-select v-model="filters.hasBidNotice" placeholder="全部" clearable style="width: 100px">
            <el-option label="有" :value="true" /><el-option label="无" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目负责人">
          <el-input v-model="filters.projectManagerKeyword" placeholder="负责人姓名" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card border-glow" v-loading="loading">
      <el-table 
        :data="records" 
        stripe 
        style="width: 100%" 
        @row-click="openDetail"
        class="custom-table"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="signingEntity" label="签约单位" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerType" label="客户类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getCustomerTypeTagType(row.customerType)" effect="light">
              {{ row.customerTypeLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="groupCompany" label="集团公司" min-width="150" show-overflow-tooltip />
        <el-table-column prop="projectType" label="项目类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="info" size="small">{{ row.projectTypeLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerLevel" label="客户级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.customerLevelLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signingDate" label="签约日期" width="105" align="center" />
        <el-table-column prop="expiryDate" label="截止日期" width="105" align="center">
          <template #default="{ row }">
            <span :class="getExpiryDateClass(row)">{{ row.expiryDate }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="daysRemaining" label="到期天数" width="100" align="center">
          <template #default="{ row }">
            <span :class="getDaysRemainingClass(row)" style="font-weight: 600">
              {{ formatDaysRemaining(row.daysRemaining) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" effect="dark">
              {{ row.statusLabel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click.stop="openForm(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情抽屉 -->
    <PerformanceDetailDrawer v-model:visible="detailVisible" :data="current" />

    <!-- 新增/编辑弹窗 -->
    <PerformanceFormDialog 
      v-model:visible="formVisible" 
      :data="editingRow" 
      :submitting="submitting"
      @submit="handleSubmit"
    />

    <!-- 提醒配置弹窗 -->
    <PerformanceAlertConfigDialog v-model="alertConfigVisible" />
  </div>
</template>

<script setup>
// Input: httpClient and performanceApi
// Output: performance management main page UI
// Pos: src/views/Knowledge/ - Frontend views layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Bell } from '@element-plus/icons-vue'
import performanceApi from '@/api/modules/performance.js'
import PerformanceDetailDrawer from './components/PerformanceDetailDrawer.vue'
import PerformanceFormDialog from './components/PerformanceFormDialog.vue'
import PerformanceAlertConfigDialog from './components/performance/PerformanceAlertConfigDialog.vue'

const records = ref([])
const loading = ref(false)
const submitting = ref(false)
const filters = reactive({ 
  keyword: '', 
  customerType: '', 
  projectType: '', 
  status: '',
  territory: '',
  signingDateRange: null,
  expiryDateRange: null,
  hasBidNotice: null,
  projectManagerKeyword: ''
})

const detailVisible = ref(false)
const formVisible = ref(false)
const alertConfigVisible = ref(false)
const current = ref({ attachments: [] })
const editingRow = ref(null)

const getCustomerTypeTagType = (t) => t === 'CENTRAL_SOE' ? 'danger' : t === 'LOCAL_SOE' ? 'warning' : t === 'GOVERNMENT_INSTITUTION' ? 'success' : 'primary'
const getStatusTagType = (s) => s === 'EXPIRED' ? 'danger' : s === 'EXPIRING' ? 'warning' : 'success'
const getExpiryDateClass = (row) => row.status === 'EXPIRED' ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-normal'
const getDaysRemainingClass = (row) => (row.daysRemaining != null && row.daysRemaining < 0) ? 'text-danger' : row.status === 'EXPIRING' ? 'text-warning' : 'text-success'
const formatDaysRemaining = (days) => (days == null || days > 999999999 || days === 2147483647) ? '-' : days < 0 ? `已逾期 ${Math.abs(days)} 天` : `${days} 天`

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await performanceApi.getList(filters)
    records.value = data || []
  } catch {
    ElMessage.error('台账加载失败，请检查服务状态')
  } finally {
    loading.value = false
  }
}

const resetFilters = () => {
  Object.assign(filters, { 
    keyword: '', customerType: '', projectType: '', status: '',
    territory: '', signingDateRange: null, expiryDateRange: null,
    hasBidNotice: null, projectManagerKeyword: ''
  })
  loadData()
}

const openDetail = (row) => {
  current.value = row
  detailVisible.value = true
}

const openForm = (row) => {
  editingRow.value = row
  formVisible.value = true
}

const openAlertConfig = () => {
  alertConfigVisible.value = true
}

const handleSubmit = async (formData) => {
  submitting.value = true
  
  const ftNames = {CONTRACT_AGREEMENT:'合同协议件.pdf',MALL_SCREENSHOT:'商城上架截图.png',SOE_DIRECTORY:'央企名录页页截图.png',CATEGORY_PAGE:'品类授权证明.pdf',RELATIONSHIP_PROOF:'层级关系图.pdf',BID_NOTICE:'中标通知书.pdf',OTHER:'附加关联文件.zip'}
  const payload = {
    ...formData,
    attachments: Object.keys(formData.attachmentMap)
      .filter(type => formData.attachmentMap[type].fileUrl)
      .map(type => ({
        fileName: formData.attachmentMap[type].fileName || ftNames[type] || '证明文件',
        fileUrl: formData.attachmentMap[type].fileUrl,
        fileType: type
      }))
  }

  try {
    let res
    if (editingRow.value) {
      res = await performanceApi.update(formData.id, payload)
      ElMessage.success('业绩档案更新成功')
    } else {
      res = await performanceApi.create(payload)
      ElMessage.success('业绩档案创建成功')
    }
    
    formVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`您确定要删除合同「${row.contractName}」的业绩档案吗？`, '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await performanceApi.delete(row.id)
    ElMessage.success('业绩档案删除成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败，请重试')
    }
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss" src="./components/Performance.scss"></style>
