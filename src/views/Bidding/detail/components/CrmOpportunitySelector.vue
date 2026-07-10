<template>
  <div class="crm-opportunity-selector">
    <div class="crm-field-row">
      <el-text class="label">CRM商机关联</el-text>
      <template v-if="linkedOpportunity">
        <el-tag type="success" size="large">{{ linkedOpportunity.name }}</el-tag>
        <el-tag v-if="linkedOpportunity.code" type="info" size="small">{{ linkedOpportunity.code }}</el-tag>
        <el-button v-if="enabled" text type="primary" size="small" @click="openSearch">更换</el-button>
      </template>
      <template v-else>
        <el-button type="primary" :disabled="!enabled" :loading="searching" @click="openSearch">
          {{ enabled ? '点击关联CRM商机' : '分配后由项目负责人关联' }}
        </el-button>
      </template>
    </div>

    <el-dialog v-model="showDialog" title="选择关联的CRM商机" width="860px" class="crm-opportunity-dialog"
      @close="resetSearch()">
      <div class="search-filters">
        <div class="blueprint-row">
          <div class="blueprint-item"><span class="blueprint-label">招标主体</span><span class="blueprint-value" :title="tenderer">{{ tenderer || '-' }}</span></div>
          <div class="blueprint-item"><span class="blueprint-label">报名截止</span><span class="blueprint-value">{{ registrationDeadline || '-' }}</span></div>
          <div class="blueprint-item"><span class="blueprint-label">开标时间</span><span class="blueprint-value">{{ bidOpeningTime || '-' }}</span></div>
        </div>
        <div class="filter-row">
          <el-input v-model="searchForm.name" placeholder="商机名称（模糊查询）" clearable size="small" class="filter-input" />
          <el-input v-model="searchForm.code" placeholder="商机编号（CRM暂不支持按编号搜索）" clearable size="small" class="filter-input" />
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

      <el-divider v-if="selectedChance" />

      <div v-if="selectedChance" class="selected-summary">
        <el-alert type="success" :closable="false">
          <template #title>已选择商机：<strong>{{ selectedChance.name }}</strong>（{{ selectedChance.code }}）</template>
          <template #default><p>项目负责人：{{ selectedChance.projectLeaderName || '-' }} | 状态：{{ selectedChance.projectStatusText || '-' }} | 评标时间：{{ selectedChance.evaluationTime || '-' }}</p></template>
        </el-alert>
      </div>

      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedChance" :loading="loading" @click="confirmLink">确认关联并回填评估表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { useCrmOpportunitySelector } from './useCrmOpportunitySelector.js'
import CrmOpportunityTable from './CrmOpportunityTable.vue'

const props = defineProps({
  enabled: { type: Boolean, default: false },
  tenderer: { type: String, default: '' },
  registrationDeadline: { type: String, default: '' },
  bidOpeningTime: { type: String, default: '' },
  alreadyLinkedName: { type: String, default: '' },
  // CO-308: 父组件递增此信号通知关联失败,触发子组件重置 UI 状态
  linkFailed: { type: Number, default: 0 },
})
const emit = defineEmits(['linked'])

const {
  showDialog, searching, loading, results, selectedId,
  selectedChance, totalCount, currentPage, pageSize, searchForm, linkedOpportunity,
  openSearch, doSearch, onSelect, confirmLink, resetSearch,
} = useCrmOpportunitySelector(props, emit)

// CO-308: 父组件告知关联失败(后端业务冲突),重置乐观写入的 linkedOpportunity,
// 让字段回到"未关联"展示,引导用户重新选择商机
watch(() => props.linkFailed, (val, oldVal) => {
  if (val > oldVal) linkedOpportunity.value = null
})
</script>

<style scoped>
.crm-opportunity-selector { padding: 12px 0; }
.crm-field-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.label { font-weight: 600; min-width: 110px; }
.crm-field-row .el-tag { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.search-filters { margin-bottom: 16px; }
.blueprint-row { display: flex; flex-wrap: nowrap; align-items: stretch; padding: 10px 0; background: var(--bg-muted-2); border: 1px solid var(--border-light); border-radius: 6px; }
.blueprint-item { flex: 1; display: flex; align-items: center; gap: 8px; min-width: 0; padding: 0 16px; border-right: 1px solid var(--border-light); }
.blueprint-item:last-child { border-right: none; }
.blueprint-label { font-weight: 600; color: var(--text-badge); font-size: 13px; white-space: nowrap; }
.blueprint-value { color: var(--text-primary); font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.filter-row { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; align-items: center; }
.filter-input { width: 180px; }
.selected-summary p { margin: 4px 0; font-size: 13px; color: var(--text-badge-2); }
:deep(.crm-opportunity-dialog .el-dialog__body) { max-height: 60vh; overflow-y: auto; padding: 16px 20px; }
:deep(.crm-opportunity-dialog .el-dialog__footer) { padding: 12px 20px; border-top: 1px solid var(--border-light); }
</style>
