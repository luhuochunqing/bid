<template>
  <div class="crm-opportunity-selector">
    <div class="crm-field-row">
      <el-text class="label">CRM商机关联</el-text>
      <template v-if="linkedOpportunity">
        <el-tag type="success" size="large">{{ linkedOpportunity.name }}</el-tag>
        <el-tag v-if="linkedOpportunity.code" type="info" size="small">{{ linkedOpportunity.code }}</el-tag>
        <el-button text type="primary" size="small" @click="openSearch">更换</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="!enabled" :loading="searching" @click="openSearch">
          {{ enabled ? '点击关联CRM商机' : '分配后由项目负责人关联' }}
        </el-button>
      </template>
    </div>

    <el-dialog v-model="showDialog" title="选择关联的CRM商机" width="860px"
      @close="showManualForm = false; manualConfirmed = false; resetSearch()">
      <div class="search-filters">
        <el-descriptions :column="4" size="small" border :labelStyle="{ fontWeight: 600 }">
          <el-descriptions-item label="招标主体">{{ tenderer }}</el-descriptions-item>
          <el-descriptions-item label="报名截止">{{ registrationDeadline }}</el-descriptions-item>
          <el-descriptions-item label="开标时间">{{ bidOpeningTime }}</el-descriptions-item>
        </el-descriptions>
        <div class="filter-row">
          <el-input v-model="searchForm.name" placeholder="商机名称" clearable size="small" class="filter-input" />
          <el-input v-model="searchForm.code" placeholder="商机编号" clearable size="small" class="filter-input" />
          <el-select v-model="searchForm.projectStatus" multiple placeholder="项目状态" clearable size="small" class="filter-input">
            <el-option label="跟踪中" :value="1" /><el-option label="已投标" :value="2" />
            <el-option label="已中标" :value="3" /><el-option label="已丢标" :value="4" /><el-option label="已流标" :value="5" />
          </el-select>
          <el-button size="small" type="primary" :loading="searching" @click="doSearch(1)">
            <el-icon><Search /></el-icon> 搜索
          </el-button>
        </div>
      </div>

      <CrmOpportunityTable :results="results" :total-count="totalCount" :current-page="currentPage"
        :page-size="pageSize" :selected-id="selectedId" @select="onSelect" @page-change="doSearch" />

      <!-- CRM不通时兜底：手动输入 -->
      <div v-if="searchPerformed && results.length === 0 && !showManualForm" class="manual-fallback">
        <el-divider />
        <el-alert type="info" :closable="false" show-icon>
          <template #title>未从CRM查到匹配商机，可手动输入关联</template>
        </el-alert>
        <el-button type="primary" plain size="small" class="mt-2" @click="showManualForm = true">手动输入商机信息</el-button>
      </div>

      <!-- 手动输入表单 -->
      <div v-if="showManualForm" class="manual-form">
        <el-divider /><h4 class="manual-title">手动输入商机信息</h4>
        <el-form :model="manualForm" label-width="90px" size="small">
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="商机名称"><el-input v-model="manualForm.name" placeholder="请输入" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="商机编号"><el-input v-model="manualForm.code" placeholder="选填" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="项目负责人"><el-input v-model="manualForm.projectLeaderName" placeholder="选填" /></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="评标时间"><el-date-picker v-model="manualForm.evaluationTime" type="date" placeholder="选填" class="full-width" /></el-form-item></el-col>
          </el-row>
          <el-row :gutter="12">
            <el-col :span="12"><el-form-item label="项目状态"><el-select v-model="manualForm.projectStatusText" placeholder="选填" class="full-width">
              <el-option label="跟踪中" value="跟踪中" /><el-option label="已投标" value="已投标" />
              <el-option label="已中标" value="已中标" /><el-option label="已丢标" value="已丢标" /><el-option label="已流标" value="已流标" />
            </el-select></el-form-item></el-col>
            <el-col :span="12"><el-form-item label="项目风险"><el-select v-model="manualForm.projectRiskText" placeholder="选填" class="full-width">
              <el-option label="低" value="低" /><el-option label="中" value="中" /><el-option label="高" value="高" />
            </el-select></el-form-item></el-col>
          </el-row>
          <el-form-item label="备注"><el-input v-model="manualForm.remark" type="textarea" :rows="2" placeholder="选填" /></el-form-item>
        </el-form>
      </div>

      <el-divider v-if="selectedChance || manualConfirmed" />

      <div v-if="selectedChance" class="selected-summary">
        <el-alert type="success" :closable="false">
          <template #title>已选择商机：<strong>{{ selectedChance.name }}</strong>（{{ selectedChance.code }}）</template>
          <template #default><p>项目负责人：{{ selectedChance.projectLeaderName || '-' }} | 状态：{{ selectedChance.projectStatusText || '-' }} | 评标时间：{{ selectedChance.evaluationTime || '-' }}</p></template>
        </el-alert>
      </div>
      <div v-if="manualConfirmed && !selectedChance" class="selected-summary">
        <el-alert type="warning" :closable="false">
          <template #title>已手动输入商机：<strong>{{ manualForm.name }}</strong></template>
          <template #default><p>编号：{{ manualForm.code || '-' }} | 负责人：{{ manualForm.projectLeaderName || '-' }} | 状态：{{ manualForm.projectStatusText || '-' }}</p></template>
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button v-if="showManualForm && !manualConfirmed" plain @click="confirmManual">确认手动输入</el-button>
        <el-button type="primary" :disabled="!selectedChance && !manualConfirmed" :loading="loading" @click="confirmLink">确认关联并回填评估表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { Search } from '@element-plus/icons-vue'
import { useCrmOpportunitySelector } from './useCrmOpportunitySelector.js'
import CrmOpportunityTable from './CrmOpportunityTable.vue'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  tenderer: { type: String, default: '' },
  registrationDeadline: { type: String, default: '' },
  bidOpeningTime: { type: String, default: '' },
  alreadyLinkedName: { type: String, default: '' },
})
const emit = defineEmits(['linked'])

const {
  showDialog, searching, loading, searchPerformed, results, selectedId,
  selectedChance, totalCount, currentPage, pageSize, showManualForm,
  manualConfirmed, searchForm, manualForm, linkedOpportunity,
  openSearch, doSearch, onSelect, confirmManual, confirmLink, resetSearch,
} = useCrmOpportunitySelector(props, emit)
</script>

<style scoped>
.crm-opportunity-selector { padding: 12px 0; }
.crm-field-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.label { font-weight: 600; min-width: 110px; }
.search-filters { margin-bottom: 16px; }
.filter-row { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 180px; }
.manual-fallback { text-align: center; padding: 8px 0; }
.manual-form { margin-top: 4px; }
.manual-title { font-size: 14px; font-weight: 600; margin: 0 0 8px 0; color: #374151; }
.mt-2 { margin-top: 8px; }
:deep(.full-width) { width: 100%; }
.selected-summary p { margin: 4px 0; font-size: 13px; color: #4b5563; }
</style>
