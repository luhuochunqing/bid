<template>
  <el-dialog :model-value="modelValue" title="资质详情" width="700px" @close="$emit('update:modelValue', false)">
    <el-descriptions v-if="qualification" :column="2" border>
      <el-descriptions-item label="资质名称">
        {{ qualification.name }}
      </el-descriptions-item>
      <el-descriptions-item label="资质类型">
        <el-tag :type="qualificationTypeTagTypes[qualification.type] || 'info'" size="small">
          {{ getTypeLabel(qualification.type) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="归属主体">
        {{ qualification.subjectType === 'subsidiary' ? '子公司' : '公司' }}
      </el-descriptions-item>
      <el-descriptions-item label="主体名称">
        {{ qualification.subjectName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="证书编号">
        {{ qualification.certificateNo }}
      </el-descriptions-item>
      <el-descriptions-item label="发证机关">
        {{ qualification.issuer }}
      </el-descriptions-item>
      <el-descriptions-item label="持有人">
        {{ qualification.holderName || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="发证日期">
        {{ formatDate(qualification.issueDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="有效期至">
        <span :class="getDateClass(qualification.status)">
          {{ formatDate(qualification.expiryDate) }}
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="qualificationStatusTagTypes[qualification.status] || ''">
          {{ qualificationStatusLabels[qualification.status] || qualification.status }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="剩余天数">
        <span :class="getDaysClass(qualification.remainingDays)">
          {{ qualification.remainingDays }} 天
        </span>
      </el-descriptions-item>
      <el-descriptions-item label="借阅状态">
        {{ qualification.currentBorrowStatus === 'borrowed' ? '借出中' : '可借阅' }}
      </el-descriptions-item>
      <el-descriptions-item label="当前借阅人">
        {{ qualification.currentBorrower || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="附件" :span="2">
        <el-button type="primary" link :icon="Download" @click="$emit('download', qualification)">
          下载附件
        </el-button>
      </el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<script setup>
import { Download } from '@element-plus/icons-vue'
import {
  formatDate,
  getDateClass,
  getDaysClass,
  getTypeLabel,
  qualificationStatusLabels,
  qualificationStatusTagTypes,
  qualificationTypeTagTypes
} from './qualificationMeta.js'

defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  qualification: {
    type: Object,
    default: null
  }
})

defineEmits(['download', 'update:modelValue'])
</script>

<style scoped lang="scss">
.days-normal {
  color: #67c23a;
}

.days-notice {
  color: #e6a23c;
}

.days-warning {
  color: #e6a23c;
  font-weight: 600;
}

.days-expired {
  color: #f56c6c;
  font-weight: 600;
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
