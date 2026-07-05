<template>
  <div class="file-list-view-container" v-loading="loading">
    <el-table :data="tableData" style="width: 100%" border stripe highlight-current-row max-height="calc(100vh - 220px)" scrollbar-always-on class="custom-table" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" />
      <el-table-column type="index" label="序号" width="70" align="center" />
      <el-table-column prop="fileName" label="文档名称" min-width="260" show-overflow-tooltip class-name="filename-col">
        <template #default="{ row }">
          <el-button type="primary" link class="filename-link" @click="emit('download', row)">
            {{ row.fileName }}
          </el-button>
        </template>
      </el-table-column>
      <el-table-column prop="documentCategory" label="文档分类" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="getDocumentCategoryTagType(row.documentCategory)">
            {{ getDocumentCategoryLabel(row.documentCategory) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="所属项目" min-width="220" show-overflow-tooltip />
      <el-table-column prop="projectType" label="项目类型" width="120" align="center">
        <template #default="{ row }">
          <el-tag>{{ getProjectTypeLabel(row.projectType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectStatus" label="项目状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="getStatusTagType(row.projectStatus)">{{ getStatusLabel(row.projectStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="projectManager" label="项目负责人" width="120" align="center">
        <template #default="{ row }">{{ row.projectManager || '-' }}</template>
      </el-table-column>
      <el-table-column prop="bidManager" label="投标负责人" width="120" align="center">
        <template #default="{ row }">{{ row.bidManager || '-' }}</template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传人" width="120" align="center" />
      <el-table-column prop="fileSize" label="文件大小" width="110" align="center">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="uploadedAt" label="上传时间" width="170" align="center">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="emit('download', row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-container">
      <el-pagination v-model:current-page="localPage" v-model:page-size="localPageSize" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="totalElements" @current-change="emit('page-change', localPage, localPageSize)" @size-change="handleSizeChange" />
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { getStatusLabel, getStatusTagType, getDocumentCategoryLabel, getDocumentCategoryTagType, formatDateTime, formatFileSize } from '../archiveLabels.js'
import { getProjectTypeLabel } from '../caseLabels.js'

const props = defineProps({
  loading: { type: Boolean, default: false },
  tableData: { type: Array, default: () => [] },
  totalElements: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 }
})

const emit = defineEmits(['download', 'page-change', 'selection-change'])

const localPage = ref(props.page)
const localPageSize = ref(props.pageSize)

watch(() => props.page, (v) => { localPage.value = v })
watch(() => props.pageSize, (v) => { localPageSize.value = v })

const handleSizeChange = () => {
  localPage.value = 1
  emit('page-change', localPage.value, localPageSize.value)
}

const handleSelectionChange = (selection) => {
  emit('selection-change', selection)
}

</script>

<style scoped lang="scss">
.file-list-view-container { display: flex; flex-direction: column; }
.filename-col .cell { overflow: hidden; }
.filename-link {
  font-weight: 500;
  width: 100%;
  text-align: left;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
.pagination-container { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
