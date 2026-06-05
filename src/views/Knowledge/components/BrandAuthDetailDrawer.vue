<template>
  <el-drawer v-model="visible" title="原厂授权详情" size="620px">
    <template v-if="detail.id">
      <el-divider content-position="left">基础信息</el-divider>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="一级产线">{{ detail.productLine }}</el-descriptions-item>
        <el-descriptions-item label="品牌 ID">{{ detail.brandId }}</el-descriptions-item>
        <el-descriptions-item label="品牌">{{ detail.brandName }}</el-descriptions-item>
        <el-descriptions-item label="进口/国产">{{ detail.importDomestic }}</el-descriptions-item>
        <el-descriptions-item label="品牌原厂名称" :span="2">{{ detail.manufacturerName }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">授权信息</el-divider>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="授权开始">{{ detail.authStartDate }}</el-descriptions-item>
        <el-descriptions-item label="授权结束">{{ detail.authEndDate }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="detail.statusTagType">{{ detail.statusLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="有效期剩余">{{ detail.remainingDays != null ? detail.remainingDays + ' 天' : '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="detail.attachments?.length" style="margin-top:12px">
        <div v-for="a in detail.attachments" :key="a.id" class="attachment-item">
          <el-icon><Document /></el-icon><span>{{ a.fileName }}</span>
          <span class="file-size">{{ formatSize(a.fileSize) }}</span>
          <el-button link type="primary" size="small" @click="previewFile(a)">预览</el-button>
        </div>
      </div>
      <el-divider content-position="left">备注</el-divider>
      <p>{{ detail.remarks || '无备注' }}</p>
      <el-divider content-position="left">操作日志</el-divider>
      <el-empty v-if="!logs.length" description="暂无操作日志" :image-size="40" />
      <el-timeline v-else>
        <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.timestamp" placement="top">
          {{ log.username }} — {{ log.action }} {{ log.description || '' }}
        </el-timeline-item>
      </el-timeline>
      <div class="detail-actions" v-if="detail.status !== 'REVOKED'">
        <el-button v-if="detail.status !== 'EXPIRED'" type="primary" @click="$emit('edit', detail)">编辑</el-button>
        <el-button type="danger" @click="$emit('revoke', detail)">作废</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'

const props = defineProps({ modelValue: Boolean, detail: Object, logs: Array })
const emit = defineEmits(['update:modelValue', 'edit', 'revoke'])
const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const formatSize = (b) => b ? (b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB') : ''
const previewFile = (a) => { if (a.fileUrl) window.open(a.fileUrl, '_blank') }
</script>

<style scoped>
.attachment-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #f3f4f6; }
.file-size { color: #9ca3af; font-size: 12px; }
.detail-actions { display: flex; gap: 12px; margin-top: 24px; justify-content: flex-end; }
</style>
