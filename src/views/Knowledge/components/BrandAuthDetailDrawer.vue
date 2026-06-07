<template>
  <el-drawer v-model="visible" :title="detail.authorizationType === 'AGENT' ? '代理商授权详情' : '原厂授权详情'" size="650px">
    <template v-if="detail.id">
      <!-- Agent Mode: Authorization Chain Card -->
      <div v-if="detail.authorizationType === 'AGENT'" class="auth-chain-card">
        <div class="chain-step">
          <div class="step-role">品牌原厂</div>
          <div class="step-name">{{ detail.manufacturerName }}</div>
        </div>
        <div class="chain-arrow">➡</div>
        <div class="chain-step">
          <div class="step-role">代理商</div>
          <div class="step-name">{{ detail.agentName }}</div>
        </div>
        <div class="chain-arrow">➡</div>
        <div class="chain-step">
          <div class="step-role">转被授权</div>
          <div class="step-name">西域数智</div>
        </div>
      </div>

      <!-- OEM Mode Details -->
      <template v-if="detail.authorizationType !== 'AGENT'">
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
        <div v-if="detail.attachments?.filter(a => a.attachmentType === 'AUTH_DOC').length" style="margin-top:12px">
          <div v-for="a in detail.attachments?.filter(a => a.attachmentType === 'AUTH_DOC')" :key="a.id" class="attachment-item">
            <el-icon><Document /></el-icon><span>{{ a.fileName }}</span>
            <span class="file-size">{{ formatSize(a.fileSize) }}</span>
            <el-button link type="primary" size="small" @click="previewFile(a)">预览</el-button>
          </div>
        </div>
      </template>

      <!-- Agent Mode Details -->
      <template v-else>
        <el-divider content-position="left">基础信息</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="一级产线">{{ detail.productLine }}</el-descriptions-item>
          <el-descriptions-item label="品牌 ID">{{ detail.brandId }}</el-descriptions-item>
          <el-descriptions-item label="品牌">{{ detail.brandName }}</el-descriptions-item>
          <el-descriptions-item label="进口/国产">{{ detail.importDomestic }}</el-descriptions-item>
          <el-descriptions-item label="品牌原厂名称" :span="2">{{ detail.manufacturerName }}</el-descriptions-item>
          <el-descriptions-item label="代理商名称" :span="2">{{ detail.agentName }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">授权 1（原厂 → 代理商）</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="授权1开始">{{ detail.auth1StartDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="授权1结束">{{ detail.auth1EndDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detail.statusTagType">{{ detail.statusLabel }}</el-tag></el-descriptions-item>
          <el-descriptions-item label="有效期（取较早）">{{ detail.authEndDate || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.attachments?.filter(a => a.attachmentType === 'auth1').length" style="margin-top:12px">
          <div v-for="a in detail.attachments?.filter(a => a.attachmentType === 'auth1')" :key="a.id" class="attachment-item">
            <el-icon><Document /></el-icon><span>{{ a.fileName }}</span>
            <span class="file-size">{{ formatSize(a.fileSize) }}</span>
            <el-button link type="primary" size="small" @click="previewFile(a)">预览</el-button>
          </div>
        </div>
        <p class="remarks-box" v-if="detail.auth1Remarks"><strong>备注（授权1）：</strong>{{ detail.auth1Remarks }}</p>

        <el-divider content-position="left">授权 2（代理商 → 西域）</el-divider>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="授权2开始">{{ detail.auth2StartDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="授权2结束">{{ detail.auth2EndDate || '—' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.attachments?.filter(a => a.attachmentType === 'auth2').length" style="margin-top:12px">
          <div v-for="a in detail.attachments?.filter(a => a.attachmentType === 'auth2')" :key="a.id" class="attachment-item">
            <el-icon><Document /></el-icon><span>{{ a.fileName }}</span>
            <span class="file-size">{{ formatSize(a.fileSize) }}</span>
            <el-button link type="primary" size="small" @click="previewFile(a)">预览</el-button>
          </div>
        </div>
        <p class="remarks-box" v-if="detail.auth2Remarks"><strong>备注（授权2）：</strong>{{ detail.auth2Remarks }}</p>
      </template>

      <!-- Remarks and Supplementary Attachments (both modes) -->
      <el-divider content-position="left">备注与其他附件</el-divider>
      <p class="remarks-box"><strong>备注/限制说明：</strong>{{ detail.remarks || '无备注' }}</p>
      <div v-if="detail.attachments?.filter(a => a.attachmentType === 'SUPPLEMENTARY').length" style="margin-top:12px">
        <div v-for="a in detail.attachments?.filter(a => a.attachmentType === 'SUPPLEMENTARY')" :key="a.id" class="attachment-item">
          <el-icon><Document /></el-icon><span>{{ a.fileName }}</span>
          <span class="file-size">{{ formatSize(a.fileSize) }}</span>
          <el-button link type="primary" size="small" @click="previewFile(a)">预览</el-button>
        </div>
      </div>

      <!-- Action logs -->
      <el-divider content-position="left">操作日志</el-divider>
      <el-empty v-if="!logs.length" description="暂无操作日志" :image-size="40" />
      <el-timeline v-else>
        <el-timeline-item v-for="log in logs" :key="log.id" :timestamp="log.timestamp" placement="top">
          <p><strong>{{ log.username }}</strong> — {{ log.action }}</p>
          <p class="log-desc" v-if="log.description">{{ log.description }}</p>
        </el-timeline-item>
      </el-timeline>
      <div class="detail-actions" v-if="detail.status !== 'REVOKED' && canManage">
        <el-button v-if="detail.status !== 'EXPIRED'" type="primary" @click="$emit('edit', detail)">编辑</el-button>
        <el-button type="danger" @click="$emit('revoke', detail)">作废</el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({ modelValue: Boolean, detail: Object, logs: Array })
const emit = defineEmits(['update:modelValue', 'edit', 'revoke'])
const visible = ref(false)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const userStore = useUserStore()
const canManage = computed(() => userStore.hasPermission('knowledge-brand-auth'))

const formatSize = (b) => b ? (b < 1048576 ? (b / 1024).toFixed(1) + ' KB' : (b / 1048576).toFixed(1) + ' MB') : ''
const previewFile = (a) => { if (a.fileUrl) window.open(a.fileUrl, '_blank') }
</script>

<style scoped>
.auth-chain-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background-color: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.chain-step {
  text-align: center;
  flex: 1;
}
.step-role {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}
.step-name {
  font-weight: 600;
  font-size: 13px;
  color: #1e293b;
}
.chain-arrow {
  color: #94a3b8;
  font-weight: bold;
  padding: 0 8px;
}
.attachment-item { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid #f3f4f6; }
.file-size { color: #9ca3af; font-size: 12px; }
.detail-actions { display: flex; gap: 12px; margin-top: 24px; justify-content: flex-end; }
.remarks-box {
  background: #f9fafb;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  color: #4b5563;
  margin: 8px 0;
  line-height: 1.5;
}
.log-desc {
  font-size: 12px;
  color: #6b7280;
  margin-top: 4px;
  padding-left: 12px;
  border-left: 2px solid #e5e7eb;
}
</style>

