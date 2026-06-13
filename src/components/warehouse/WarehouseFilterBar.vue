<template>
  <div class="filter-bar">
    <div class="filter-bar-main">
      <el-form :model="localFilters" inline @submit.prevent="handleSearch">
        <el-form-item label="仓库名称">
          <el-input v-model="localFilters.keyword" placeholder="仓库名称/地址/联系人" clearable style="width:150px" @keyup.enter="handleSearch" @input="onKeywordInput" />
        </el-form-item>
        <el-form-item label="仓库类型">
          <el-select v-model="localFilters.types" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable style="width:120px">
            <el-option label="自营" value="SELF_OPERATED" />
            <el-option label="云仓" value="CLOUD" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属区域">
          <el-select v-model="localFilters.regions" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable style="width:140px">
            <el-option v-for="r in REGION_OPTIONS" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库状态">
          <el-select v-model="localFilters.statuses" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable style="width:140px">
            <el-option label="使用中" value="IN_USE" />
            <el-option label="即将到期" value="EXPIRING" />
            <el-option label="已到期" value="EXPIRED" />
            <el-option label="已关仓" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="所在省份">
          <el-select v-model="localFilters.provinces" multiple collapse-tags collapse-tags-tooltip placeholder="全部" clearable filterable style="width:160px">
            <el-option v-for="p in PROVINCE_OPTIONS" :key="p" :label="p" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="localFilters.endDateRange" type="daterange" range-separator="至" start-placeholder="开始"
            end-placeholder="结束" value-format="YYYY-MM-DD" style="width:210px" />
        </el-form-item>
        <el-form-item label="附件">
          <el-checkbox v-model="localFilters.hasPropertyCert">产权证</el-checkbox>
          <el-checkbox v-model="localFilters.hasInvoice">发票</el-checkbox>
          <el-checkbox v-model="localFilters.hasPhotos">照片</el-checkbox>
        </el-form-item>
        <el-form-item label="区域联系人">
          <el-input v-model="localFilters.contactPersonKeyword" placeholder="区域联系人" clearable style="width:110px" @keyup.enter="handleSearch" @input="onContactInput" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon> 搜索</el-button>
          <el-button @click="handleReset"><el-icon><RefreshRight /></el-icon> 重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    <div class="filter-bar-actions">
      <span class="total-tip">共 <strong>{{ total }}</strong> 条</span>
      <el-button @click="$emit('download-template')"><el-icon><DocumentCopy /></el-icon> 下载模板</el-button>
      <el-button type="warning" @click="$emit('import')"><el-icon><Upload /></el-icon> 批量导入</el-button>
      <el-button v-if="selectedCount > 0" type="success" @click="$emit('batch-export')">
        <el-icon><Download /></el-icon> 批量导出 ({{ selectedCount }})
      </el-button>
      <el-button v-else type="success" @click="$emit('export')"><el-icon><Download /></el-icon> 导出台账</el-button>
      <el-button v-if="selectedCount > 0" type="primary" plain @click="$emit('ledger-export')">
        <el-icon><Document /></el-icon> 台账导出 ({{ selectedCount }})
      </el-button>
      <el-button v-else type="primary" plain @click="$emit('ledger-export')">
        <el-icon><Document /></el-icon> 台账导出
      </el-button>
      <el-button type="primary" @click="$emit('create')"><el-icon><Plus /></el-icon> 新增仓库</el-button>
    </div>
  </div>
</template>

<script setup>
import { reactive, watch, onBeforeUnmount } from 'vue'
import { Search, RefreshRight, Plus, Download, Upload, DocumentCopy } from '@element-plus/icons-vue'

const props = defineProps({
  filters: { type: Object, default: () => ({}) },
  total: { type: Number, default: 0 },
  selectedCount: { type: Number, default: 0 }
})
const emit = defineEmits(['update:filters', 'search', 'reset', 'create', 'export', 'import', 'download-template', 'batch-export', 'realtime-search', 'ledger-export'])

// 7 大区域
const REGION_OPTIONS = ['华北', '东北', '华东', '华中', '华南', '西北', '西南']
// 34 个省级行政区
const PROVINCE_OPTIONS = [
  '北京市', '天津市', '上海市', '重庆市',
  '河北省', '山西省', '辽宁省', '吉林省', '黑龙江省',
  '江苏省', '浙江省', '安徽省', '福建省', '江西省', '山东省',
  '河南省', '湖北省', '湖南省', '广东省', '海南省',
  '四川省', '贵州省', '云南省', '陕西省', '甘肃省', '青海省',
  '台湾省',
  '内蒙古自治区', '广西壮族自治区', '西藏自治区', '宁夏回族自治区', '新疆维吾尔自治区',
  '香港特别行政区', '澳门特别行政区'
]

const localFilters = reactive({
  keyword: props.filters.keyword || '',
  types: props.filters.types || [],
  statuses: props.filters.statuses || [],
  regions: props.filters.regions || [],
  provinces: props.filters.provinces || [],
  endDateRange: props.filters.endDateRange || null,
  hasPropertyCert: props.filters.hasPropertyCert || false,
  hasInvoice: props.filters.hasInvoice || false,
  hasPhotos: props.filters.hasPhotos || false,
  contactPersonKeyword: props.filters.contactPersonKeyword || ''
})

watch(() => props.filters, (f) => {
  localFilters.keyword = f.keyword || ''
  localFilters.types = f.types || []
  localFilters.statuses = f.statuses || []
  localFilters.regions = f.regions || []
  localFilters.provinces = f.provinces || []
  localFilters.endDateRange = f.endDateRange || null
  localFilters.hasPropertyCert = f.hasPropertyCert || false
  localFilters.hasInvoice = f.hasInvoice || false
  localFilters.hasPhotos = f.hasPhotos || false
  localFilters.contactPersonKeyword = f.contactPersonKeyword || ''
}, { deep: true })

const buildFilters = () => ({
  keyword: localFilters.keyword || undefined,
  types: localFilters.types.length ? localFilters.types : undefined,
  statuses: localFilters.statuses.length ? localFilters.statuses : undefined,
  regions: localFilters.regions.length ? localFilters.regions : undefined,
  provinces: localFilters.provinces.length ? localFilters.provinces : undefined,
  endDateFrom: localFilters.endDateRange?.[0] || undefined,
  endDateTo: localFilters.endDateRange?.[1] || undefined,
  hasPropertyCert: localFilters.hasPropertyCert || undefined,
  hasInvoice: localFilters.hasInvoice || undefined,
  hasPhotos: localFilters.hasPhotos || undefined,
  contactPersonKeyword: localFilters.contactPersonKeyword || undefined
})

// 实时筛选（防抖 300ms）：仅 keyword / 联系人文本框触发，其他变化仍走「搜索」按钮
let debounceTimer = null
const emitRealtime = () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    emit('update:filters', buildFilters())
    emit('realtime-search')
  }, 300)
}
const onKeywordInput = () => emitRealtime()
const onContactInput = () => emitRealtime()

const handleSearch = () => {
  if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null }
  emit('update:filters', buildFilters())
  emit('search')
}
const handleReset = () => {
  Object.assign(localFilters, { keyword:'', types:[], statuses:[], regions:[], provinces:[],
    endDateRange:null, hasPropertyCert:false, hasInvoice:false, hasPhotos:false, contactPersonKeyword:'' })
  if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null }
  emit('update:filters', buildFilters())
  emit('reset')
}

onBeforeUnmount(() => { if (debounceTimer) clearTimeout(debounceTimer) })
</script>

<style scoped lang="scss">
.filter-bar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  margin-bottom: 16px;
}

.filter-bar-main {
  width: 100%;

  .el-form {
    display: flex;
    flex-wrap: wrap;
    gap: 10px 12px;
    align-items: center;
  }

  .el-form-item {
    display: flex;
    align-items: center;
    margin-bottom: 0;
    margin-right: 0;
    white-space: nowrap;
  }

  :deep(.el-form-item__label) {
    padding-right: 6px;
    white-space: nowrap;
    font-weight: 500;
    color: var(--el-text-color-regular);
    font-size: 13px;
  }

  :deep(.el-form-item__content) {
    display: flex;
    align-items: center;
  }
}

.filter-bar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.total-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 500;
  margin-right: auto;
}
</style>
