<template>
  <el-form :model="form" label-width="110px">
    <el-form-item v-if="showProjectSelector" label="关联项目">
      <el-select v-model="form.projectId" placeholder="请选择项目" filterable clearable style="width: 100%">
        <el-option
          v-for="project in projects"
          :key="project.id"
          :label="project.name"
          :value="project.id"
        />
      </el-select>
    </el-form-item>

    <el-form-item label="投标结果">
      <el-radio-group v-model="form.result" @change="handleResultChange">
        <el-radio value="won">中标</el-radio>
        <el-radio value="lost">未中标</el-radio>
      </el-radio-group>
    </el-form-item>

    <el-form-item v-if="form.result === 'won'" label="中标金额">
      <el-input-number v-model="form.amount" :min="0" :precision="2" style="width: 220px" />
    </el-form-item>

    <el-form-item label="合同开始">
      <el-date-picker v-model="form.contractStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
    </el-form-item>

    <el-form-item label="合同结束">
      <el-date-picker v-model="form.contractEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
    </el-form-item>

    <el-form-item label="合作期限(月)">
      <el-input-number v-model="form.contractDurationMonths" :min="0" :controls="false" style="width: 220px" />
    </el-form-item>

    <el-form-item label="SKU数量">
      <el-input-number v-model="form.skuCount" :min="0" :controls="false" style="width: 220px" />
    </el-form-item>

    <el-form-item label="备注信息">
      <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="如中标 SKU 数量、复盘说明" />
    </el-form-item>

    <el-form-item :label="attachmentLabel">
      <el-upload
        :auto-upload="false"
        :limit="1"
        :file-list="form.attachmentFiles"
        accept=".pdf,.doc,.docx,.jpg,.jpeg,.png"
        @change="handleAttachmentChange"
        @remove="handleAttachmentRemove"
      >
        <el-button type="primary" plain>选择文件</el-button>
      </el-upload>
    </el-form-item>

    <el-form-item label="竞争对手">
      <BidResultCompetitorEditor
        :model-value="form.competitors"
        @add="emit('add-competitor')"
        @remove="emit('remove-competitor', $event)"
      />
    </el-form-item>
  </el-form>
</template>

<script setup>
import { computed } from 'vue'

import BidResultCompetitorEditor from './BidResultCompetitorEditor.vue'

const form = defineModel('form', { type: Object, required: true })
defineProps({
  projects: {
    type: Array,
    default: () => []
  },
  showProjectSelector: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['add-competitor', 'remove-competitor'])

const attachmentLabel = computed(() => (form.value.result === 'won' ? '中标通知书' : '分析报告'))

const handleResultChange = (value) => {
  form.value.attachmentType = value === 'won' ? 'WIN_NOTICE' : 'LOSS_REPORT'
}

const handleAttachmentChange = (file, fileList) => {
  form.value.attachmentFile = file.raw || file
  form.value.attachmentFiles = fileList.slice(-1)
}

const handleAttachmentRemove = () => {
  form.value.attachmentFile = null
  form.value.attachmentFiles = []
}
</script>
