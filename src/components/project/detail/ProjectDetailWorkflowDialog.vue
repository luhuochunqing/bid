<template>
  <el-dialog v-model="detail.processDialogVisible.value" title="标书编制流程" width="900px" :close-on-click-modal="false">
    <el-tabs v-model="detail.activeProcessTab.value" type="border-card">
      <el-tab-pane label="初稿编制" name="draft">
        <el-form :model="detail.draftForm.value" label-width="120px">
          <el-form-item label="编制人"><span>{{ detail.draftForm.value.preparer || detail.userStore.userName }}</span></el-form-item>
          <el-form-item label="使用模板"><el-select v-model="detail.draftForm.value.templateId" placeholder="请选择模板" style="width: 300px;"><el-option v-for="tpl in detail.templates.value" :key="tpl.id" :label="tpl.name" :value="tpl.id"><div style="display: flex; justify-content: space-between;"><span>{{ tpl.name }}</span><span style="color: var(--text-muted); font-size: 12px;">{{ tpl.category }}</span></div></el-option></el-select></el-form-item>
          <el-form-item label="初稿文件"><el-upload :with-credentials="true" :action="detail.uploadAction.value" :headers="detail.uploadHeaders.value" :before-upload="detail.ensureDemoUpload" :on-success="detail.handleDraftFileSuccess" :file-list="detail.draftFileList.value" :limit="3" accept=".doc,.docx,.pdf" drag><el-icon class="el-icon--upload"><UploadFilled /></el-icon><div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div><template #tip><div class="el-upload__tip">支持 doc、docx、pdf 格式，最多上传3个文件</div></template></el-upload></el-form-item>
          <el-form-item label="备注说明"><el-input v-model="detail.draftForm.value.remark" type="textarea" :rows="3" placeholder="请输入初稿编制的备注说明" /></el-form-item>
        </el-form>
        <div class="dialog-footer"><el-button @click="detail.processDialogVisible.value = false">取消</el-button><el-button type="primary" @click="detail.handleSaveDraft">保存初稿</el-button></div>
      </el-tab-pane>
      <el-tab-pane label="内部评审" name="review">
        <div class="review-section">
          <div class="section-header"><span>评审人员</span><el-button type="primary" size="small" :icon="Plus" @click="detail.handleAddReviewer">添加评审人</el-button></div>
          <el-table :data="detail.reviewers.value" border style="width: 100%; margin-bottom: 20px;">
            <el-table-column prop="name" label="评审人" width="120" />
            <el-table-column prop="role" label="角色" width="120"><template #default="{ row }"><el-tag size="small" :type="detail.getReviewerRoleType(row.role)">{{ detail.getReviewerRoleText(row.role) }}</el-tag></template></el-table-column>
            <el-table-column prop="status" label="评审状态" width="100"><template #default="{ row }"><el-tag :type="detail.getReviewStatusType(row.status)" size="small">{{ detail.getReviewStatusText(row.status) }}</el-tag></template></el-table-column>
            <el-table-column prop="comment" label="评审意见" min-width="200" show-overflow-tooltip />
            <el-table-column prop="reviewTime" label="评审时间" width="160" />
            <el-table-column label="操作" width="100" fixed="right"><template #default="{ row, $index }"><el-button v-if="row.status === 'pending'" link type="primary" size="small" @click="detail.handleRemindReviewer(row)">提醒</el-button><el-button link type="danger" size="small" @click="detail.handleRemoveReviewer($index)">移除</el-button></template></el-table-column>
          </el-table>
          <el-divider>评审总览</el-divider>
          <el-progress :percentage="detail.getReviewProgress()" :stroke-width="20" :show-text="true"><span class="progress-text">评审进度: {{ detail.getReviewedCount() }}/{{ detail.reviewers.value.length }}</span></el-progress>
          <div v-if="detail.reviewers.value.some((r) => r.status === 'rejected')" class="review-summary"><el-alert title="存在评审未通过，请处理相关意见" type="error" :closable="false" show-icon /></div>
        </div>
        <div class="dialog-footer"><el-button @click="detail.processDialogVisible.value = false">关闭</el-button><el-button type="primary" :disabled="!detail.canCompleteReview()" @click="detail.handleCompleteReview">完成评审</el-button></div>
      </el-tab-pane>
      <el-tab-pane label="用印申请" name="seal">
        <el-form :model="detail.sealForm.value" label-width="120px">
          <el-form-item label="用印类型" required><el-checkbox-group v-model="detail.sealForm.value.sealTypes"><el-checkbox label="official">公章</el-checkbox><el-checkbox label="contract">合同章</el-checkbox><el-checkbox label="legal">法人章</el-checkbox><el-checkbox label="finance">财务章</el-checkbox></el-checkbox-group></el-form-item>
          <el-form-item label="用印事由"><el-input v-model="detail.sealForm.value.reason" type="textarea" :rows="2" placeholder="请说明用印事由" /></el-form-item>
          <el-form-item label="用印文件"><el-upload :with-credentials="true" :action="detail.uploadAction.value" :headers="detail.uploadHeaders.value" :before-upload="detail.ensureDemoUpload" :on-success="detail.handleSealFileSuccess" :file-list="detail.sealFileList.value" :limit="5" accept=".pdf,.doc,.docx"><el-button type="primary" :icon="Upload">上传待盖章文件</el-button><template #tip><div class="el-upload__tip">请上传需要盖章的文件，最多5个</div></template></el-upload></el-form-item>
          <el-form-item label="用印数量"><el-input-number v-model="detail.sealForm.value.count" :min="1" :max="100" /><span style="margin-left: 8px;">份</span></el-form-item>
          <el-form-item label="期望完成时间"><el-date-picker v-model="detail.sealForm.value.expectedTime" type="datetime" placeholder="选择期望完成时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" /></el-form-item>
        </el-form>
        <div class="dialog-footer"><el-button @click="detail.processDialogVisible.value = false">取消</el-button><el-button type="primary" @click="detail.handleSubmitSeal">提交用印申请</el-button></div>
      </el-tab-pane>
      <el-tab-pane label="封装提交" name="submit">
        <el-form :model="detail.submitForm.value" label-width="120px">
          <el-form-item label="标书封装检查"><el-checkbox-group v-model="detail.submitForm.value.checkList"><el-checkbox label="tech">技术方案已完成</el-checkbox><el-checkbox label="business">商务文件已完成</el-checkbox><el-checkbox label="qualification">资质文件已准备</el-checkbox><el-checkbox label="price">报价文件已确认</el-checkbox><el-checkbox label="seal">用印已完成</el-checkbox><el-checkbox label="package">标书已装订</el-checkbox></el-checkbox-group></el-form-item>
          <el-form-item label="封装方式"><el-radio-group v-model="detail.submitForm.value.packageType"><el-radio value="paper">纸质封装</el-radio><el-radio value="electronic">电子标书</el-radio><el-radio value="both">纸质+电子</el-radio></el-radio-group></el-form-item>
          <el-form-item label="密封要求"><el-input v-model="detail.submitForm.value.sealRequirement" type="textarea" :rows="2" placeholder="请输入密封要求，如：密封条加盖公章" /></el-form-item>
          <el-form-item label="递交方式"><el-radio-group v-model="detail.submitForm.value.deliveryMethod"><el-radio value="online">线上递交</el-radio><el-radio value="offline">现场递交</el-radio><el-radio value="courier">快递递交</el-radio></el-radio-group></el-form-item>
          <el-form-item v-if="detail.submitForm.value.deliveryMethod !== 'online'" label="递交时间"><el-date-picker v-model="detail.submitForm.value.deliveryTime" type="datetime" placeholder="选择递交时间" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" /></el-form-item>
          <el-form-item v-if="detail.submitForm.value.deliveryMethod === 'offline' || detail.submitForm.value.deliveryMethod === 'courier'" label="递交地址"><el-input v-model="detail.submitForm.value.deliveryAddress" placeholder="请输入递交地址" /></el-form-item>
          <el-form-item label="备注"><el-input v-model="detail.submitForm.value.remark" type="textarea" :rows="2" placeholder="其他需要说明的事项" /></el-form-item>
        </el-form>
        <div class="dialog-footer"><el-button @click="detail.processDialogVisible.value = false">取消</el-button><el-button type="primary" :disabled="detail.submitForm.value.checkList.length < 6" @click="detail.handleSubmitPackage">确认封装提交</el-button></div>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup>
import { inject } from 'vue'
import { Plus, Upload, UploadFilled } from '@element-plus/icons-vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const detail = inject(projectDetailKey)
</script>
