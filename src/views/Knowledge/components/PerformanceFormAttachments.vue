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
    
    <div class="detail-subtitle">附件清单（直接填入文件名与链接录入档案）</div>
    
    <div class="attachment-input-grid">
      <!-- 核心必填：合同协议 -->
      <div class="attachment-row required-row">
        <span class="attachment-label">合同协议 <span class="req">*</span></span>
        <el-input v-model="form.attachmentMap.CONTRACT_AGREEMENT.fileName" placeholder="合同协议文件名" style="width: 200px" />
        <el-input v-model="form.attachmentMap.CONTRACT_AGREEMENT.fileUrl" placeholder="下载链接/URL" class="flex-grow" />
      </div>

      <!-- 商城截图 -->
      <div class="attachment-row">
        <span class="attachment-label">商城截图</span>
        <el-input v-model="form.attachmentMap.MALL_SCREENSHOT.fileName" placeholder="商城截图文件名" style="width: 200px" />
        <el-input v-model="form.attachmentMap.MALL_SCREENSHOT.fileUrl" placeholder="下载链接/URL" class="flex-grow" />
      </div>

      <!-- 联动显示：央企附件 -->
      <template v-if="form.customerType === 'CENTRAL_SOE'">
        <div class="attachment-row required-row">
          <span class="attachment-label">央企名录 <span class="req">*</span></span>
          <el-input v-model="form.attachmentMap.SOE_DIRECTORY.fileName" placeholder="央企名录文件名" style="width: 200px" />
          <el-input v-model="form.attachmentMap.SOE_DIRECTORY.fileUrl" placeholder="名录或清单链接" class="flex-grow" />
        </div>
        <div class="attachment-row required-row">
          <span class="attachment-label">关系证明 <span class="req">*</span></span>
          <el-input v-model="form.attachmentMap.RELATIONSHIP_PROOF.fileName" placeholder="关系证明文件名" style="width: 200px" />
          <el-input v-model="form.attachmentMap.RELATIONSHIP_PROOF.fileUrl" placeholder="层级与归属证明链接" class="flex-grow" />
        </div>
        <div style="margin-left: 120px; color: var(--el-color-warning); font-size: 12px; margin-bottom: 12px;">
          * 客户类型为央企，【央企名录】与【关系证明】必须至少填入一个链接。
        </div>
      </template>

      <!-- 品类页 -->
      <div class="attachment-row">
        <span class="attachment-label">品类页</span>
        <el-input v-model="form.attachmentMap.CATEGORY_PAGE.fileName" placeholder="品类页授权文件名" style="width: 200px" />
        <el-input v-model="form.attachmentMap.CATEGORY_PAGE.fileUrl" placeholder="下载链接/URL" class="flex-grow" />
      </div>

      <!-- 联动显示：中标通知书 -->
      <div class="attachment-row required-row" v-if="form.hasBidNotice">
        <span class="attachment-label">中标通知书 <span class="req">*</span></span>
        <el-input v-model="form.attachmentMap.BID_NOTICE.fileName" placeholder="中标通知书文件名" style="width: 200px" />
        <el-input v-model="form.attachmentMap.BID_NOTICE.fileUrl" placeholder="中标通知书链接" class="flex-grow" />
      </div>

      <!-- 其他附件 -->
      <div class="attachment-row">
        <span class="attachment-label">其他附件</span>
        <el-input v-model="form.attachmentMap.OTHER.fileName" placeholder="其他证明文件名" style="width: 200px" />
        <el-input v-model="form.attachmentMap.OTHER.fileUrl" placeholder="其他附件链接" class="flex-grow" />
      </div>
    </div>

    <el-form-item label="备注" style="margin-top: 16px;">
      <el-input v-model="form.remarks" type="textarea" :rows="3" placeholder="填写合规说明、特许采购等补充条款" />
    </el-form-item>
  </div>
</template>

<script setup>
defineProps({
  form: {
    type: Object,
    required: true
  }
})
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
  align-items: center;
  gap: 12px;
  padding: 8px;
  border-radius: 6px;
  background-color: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  
  .attachment-label {
    width: 120px;
    font-weight: 600;
    color: var(--el-text-color-regular);
    text-align: right;
    .req {
      color: var(--el-color-danger);
    }
  }
  
  .flex-grow {
    flex-grow: 1;
  }
}

.required-row {
  border-color: var(--el-border-color);
  background-color: var(--el-fill-color-light);
}
</style>
