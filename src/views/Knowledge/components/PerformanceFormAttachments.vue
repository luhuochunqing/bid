<template>
  <div class="attachments-form">
    <el-form-item label="商城网址">
      <el-input v-model="form.mallWebsiteUrl" placeholder="如客户采购商城的访问链接" />
    </el-form-item>
    <el-form-item label="中标通知书">
      <el-switch
        v-model="form.hasBidNotice"
        active-text="包含中标通知书"
        inactive-text="无"
      />
    </el-form-item>

    <div class="detail-subtitle">附件清单（每个字段支持上传多份文件）</div>

    <div class="attachment-input-grid">
      <!-- 核心必填：合同协议 -->
      <div class="attachment-row required-row">
        <div class="attachment-header">
          <span class="attachment-label">合同协议 <span class="req">*</span></span>
          <el-upload
            :http-request="(opt) => handleUpload({ ...opt, fileType: 'CONTRACT_AGREEMENT' })"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
            multiple
          >
            <el-button type="primary" size="small">上传文件</el-button>
          </el-upload>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in form.attachmentMap.CONTRACT_AGREEMENT" :key="f.fileUrl + idx" class="file-item">
            <span class="file-name">{{ f.fileName }}</span>
            <el-button type="danger" size="small" link @click="removeFile('CONTRACT_AGREEMENT', idx)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 商城截图 -->
      <div class="attachment-row">
        <div class="attachment-header">
          <span class="attachment-label">商城截图</span>
          <el-upload
            :http-request="(opt) => handleUpload({ ...opt, fileType: 'MALL_SCREENSHOT' })"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
            multiple
          >
            <el-button type="primary" size="small">上传文件</el-button>
          </el-upload>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in form.attachmentMap.MALL_SCREENSHOT" :key="f.fileUrl + idx" class="file-item">
            <span class="file-name">{{ f.fileName }}</span>
            <el-button type="danger" size="small" link @click="removeFile('MALL_SCREENSHOT', idx)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 联动显示：央企附件 -->
      <template v-if="form.customerType === 'CENTRAL_SOE'">
        <div class="attachment-row required-row">
          <div class="attachment-header">
            <span class="attachment-label">央企名录 <span class="req">*</span></span>
            <el-upload
              :http-request="(opt) => handleUpload({ ...opt, fileType: 'SOE_DIRECTORY' })"
              :show-file-list="false"
              accept=".pdf,.jpg,.jpeg,.png"
              multiple
            >
              <el-button type="primary" size="small">上传文件</el-button>
            </el-upload>
          </div>
          <div class="file-list">
            <div v-for="(f, idx) in form.attachmentMap.SOE_DIRECTORY" :key="f.fileUrl + idx" class="file-item">
              <span class="file-name">{{ f.fileName }}</span>
              <el-button type="danger" size="small" link @click="removeFile('SOE_DIRECTORY', idx)">删除</el-button>
            </div>
          </div>
        </div>
        <div class="attachment-row required-row">
          <div class="attachment-header">
            <span class="attachment-label">关系证明 <span class="req">*</span></span>
            <el-upload
              :http-request="(opt) => handleUpload({ ...opt, fileType: 'RELATIONSHIP_PROOF' })"
              :show-file-list="false"
              accept=".pdf,.jpg,.jpeg,.png"
              multiple
            >
              <el-button type="primary" size="small">上传文件</el-button>
            </el-upload>
          </div>
          <div class="file-list">
            <div v-for="(f, idx) in form.attachmentMap.RELATIONSHIP_PROOF" :key="f.fileUrl + idx" class="file-item">
              <span class="file-name">{{ f.fileName }}</span>
              <el-button type="danger" size="small" link @click="removeFile('RELATIONSHIP_PROOF', idx)">删除</el-button>
            </div>
          </div>
        </div>
        <div style="margin-left: 120px; color: var(--el-color-warning); font-size: 12px; margin-bottom: 12px;">
          * 客户类型为央企，【央企名录】与【关系证明】必须至少上传一个文件。
        </div>
      </template>

      <!-- 品类页 -->
      <div class="attachment-row">
        <div class="attachment-header">
          <span class="attachment-label">品类页</span>
          <el-upload
            :http-request="(opt) => handleUpload({ ...opt, fileType: 'CATEGORY_PAGE' })"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
            multiple
          >
            <el-button type="primary" size="small">上传文件</el-button>
          </el-upload>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in form.attachmentMap.CATEGORY_PAGE" :key="f.fileUrl + idx" class="file-item">
            <span class="file-name">{{ f.fileName }}</span>
            <el-button type="danger" size="small" link @click="removeFile('CATEGORY_PAGE', idx)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 联动显示：中标通知书 -->
      <div class="attachment-row required-row" v-if="form.hasBidNotice">
        <div class="attachment-header">
          <span class="attachment-label">中标通知书 <span class="req">*</span></span>
          <el-upload
            :http-request="(opt) => handleUpload({ ...opt, fileType: 'BID_NOTICE' })"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
            multiple
          >
            <el-button type="primary" size="small">上传文件</el-button>
          </el-upload>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in form.attachmentMap.BID_NOTICE" :key="f.fileUrl + idx" class="file-item">
            <span class="file-name">{{ f.fileName }}</span>
            <el-button type="danger" size="small" link @click="removeFile('BID_NOTICE', idx)">删除</el-button>
          </div>
        </div>
      </div>

      <!-- 其他附件 -->
      <div class="attachment-row">
        <div class="attachment-header">
          <span class="attachment-label">其他附件</span>
          <el-upload
            :http-request="(opt) => handleUpload({ ...opt, fileType: 'OTHER' })"
            :show-file-list="false"
            accept=".pdf,.jpg,.jpeg,.png"
            multiple
          >
            <el-button type="primary" size="small">上传文件</el-button>
          </el-upload>
        </div>
        <div class="file-list">
          <div v-for="(f, idx) in form.attachmentMap.OTHER" :key="f.fileUrl + idx" class="file-item">
            <span class="file-name">{{ f.fileName }}</span>
            <el-button type="danger" size="small" link @click="removeFile('OTHER', idx)">删除</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-form-item label="备注" style="margin-top: 16px;">
      <el-input v-model="form.remarks" type="textarea" :rows="3" placeholder="填写合规说明、特许采购等补充条款" />
    </el-form-item>
  </div>
</template>

<script setup>
import { ElMessage } from 'element-plus'
import { performanceApi } from '@/api/modules/performance.js'

const props = defineProps({
  form: {
    type: Object,
    required: true,
  },
})

// CO-442: 自定义上传逻辑，调用后端 /api/knowledge/performance/attachments/upload
async function handleUpload({ file, fileType }) {
  try {
    const res = await performanceApi.uploadAttachment(file, fileType)
    const data = res?.data || res
    if (!data || !data.fileUrl) {
      ElMessage.error('上传失败：未返回文件地址')
      return
    }
    props.form.attachmentMap[fileType].push({
      fileName: data.fileName || file.name,
      fileUrl: data.fileUrl,
      fileType,
    })
    ElMessage.success('上传成功')
  } catch (e) {
    ElMessage.error(e?.message || '上传失败')
  }
}

// CO-442: 删除已上传的文件（仅从列表移除，不调用后端删除接口）
function removeFile(fileType, index) {
  props.form.attachmentMap[fileType].splice(index, 1)
}
</script>

<style scoped lang="scss">
.detail-subtitle {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 20px 0 12px 0;
  border-left: 4px solid var(--el-color-primary);
  padding-left: 8px;
}

.attachment-input-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.attachment-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px;
  border-radius: 6px;
  background-color: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);

  .attachment-header {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .attachment-label {
    width: 120px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    text-align: right;
    .req {
      color: var(--el-color-danger);
    }
  }

  .file-list {
    margin-left: 132px;
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .file-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 4px 8px;
    background-color: var(--el-fill-color-light);
    border-radius: 4px;

    .file-name {
      color: var(--el-text-color-regular);
      font-size: 13px;
      word-break: break-all;
    }
  }
}

.required-row {
  border-color: var(--el-border-color);
  background-color: var(--el-fill-color-light);
}
</style>
