<template>
  <el-card class="table-card">
    <FeaturePlaceholder
      v-if="featurePlaceholder"
      :title="featurePlaceholder.title"
      :message="featurePlaceholder.message"
      :hint="featurePlaceholder.hint"
    />
    <el-table
      v-else
      v-loading="loading"
      :data="qualifications"
      stripe
      style="width: 100%"
    >
      <el-table-column prop="name" label="资质名称" min-width="200">
        <template #default="{ row }">
          <div class="name-cell">
            <el-icon class="type-icon" :color="qualificationTypeColors[row.type] || qualificationTypeColors.enterprise">
              <component :is="qualificationTypeIcons[row.type] || qualificationTypeIcons.enterprise" />
            </el-icon>
            <span>{{ row.name }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="type" label="类型" width="160">
        <template #default="{ row }">
          <el-tag :type="qualificationTypeTagTypes[row.type] || 'info'" size="small">
            {{ getTypeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="certificateNo" label="证书编号" min-width="180" />

      <el-table-column prop="issueDate" label="发证日期" width="120">
        <template #default="{ row }">
          {{ formatDate(row.issueDate) }}
        </template>
      </el-table-column>

      <el-table-column prop="expiryDate" label="有效期至" width="120">
        <template #default="{ row }">
          <span :class="getDateClass(row.status)">
            {{ formatDate(row.expiryDate) }}
          </span>
        </template>
      </el-table-column>

      <el-table-column prop="issuer" label="发证机关" min-width="150" />

      <el-table-column prop="status" label="状态" width="140">
        <template #default="{ row }">
          <el-tag
            :type="qualificationStatusTagTypes[row.status] || ''"
            :icon="qualificationStatusIcons[row.status] || undefined"
            size="small"
          >
            {{ qualificationStatusLabels[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :icon="View" size="small" @click="$emit('view', row)">
            查看
          </el-button>
          <el-button type="primary" link :icon="Share" size="small" @click="$emit('borrow', row)">
            借阅
          </el-button>
          <el-button type="primary" link :icon="Download" size="small" @click="$emit('download', row)">
            下载
          </el-button>
          <el-button
            v-if="isAdmin"
            type="danger"
            link
            :icon="Delete"
            size="small"
            @click="$emit('delete', row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="$emit('size-change', $event)"
        @current-change="$emit('page-change', $event)"
      />
    </div>
  </el-card>
</template>

<script setup>
import { Delete, Download, Share, View } from '@element-plus/icons-vue'
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'
import {
  formatDate,
  getDateClass,
  getTypeLabel,
  qualificationStatusIcons,
  qualificationStatusLabels,
  qualificationStatusTagTypes,
  qualificationTypeColors,
  qualificationTypeIcons,
  qualificationTypeTagTypes
} from './qualificationMeta.js'

defineProps({
  featurePlaceholder: {
    type: Object,
    default: null
  },
  isAdmin: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  },
  page: {
    type: Number,
    required: true
  },
  pageSize: {
    type: Number,
    required: true
  },
  qualifications: {
    type: Array,
    default: () => []
  },
  total: {
    type: Number,
    required: true
  }
})

defineEmits(['borrow', 'delete', 'download', 'page-change', 'size-change', 'view'])
</script>

<style scoped lang="scss">
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-icon {
  font-size: 18px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.date-warning {
  color: #e6a23c;
  font-weight: 500;
}

.date-expired {
  color: #f56c6c;
  font-weight: 500;
}
</style>
