<template>
  <div v-show="activeTab === 'basic'" class="tab-content">
    <el-card shadow="never">
      <el-form ref="innerFormRef" :model="form" :rules="rules" label-width="110px" :disabled="saving || isReadOnly">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="项目名称" prop="title">
              <el-input v-model="form.title" placeholder="请输入项目名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总部所在地" prop="region">
              <el-cascader
                ref="regionCascaderRef"
                v-model="regionCascaderValue"
                :options="chinaRegionOptions"
                :props="REGION_CASCADER_PROPS"
                placeholder="选择总部所在地"
                clearable
                filterable
                class="full-width"
                @change="onRegionCascaderChange"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="招标主体" prop="purchaser">
              <el-input v-model="form.purchaser" placeholder="请输入招标主体" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报名截止时间" prop="deadline">
              <el-date-picker v-model="form.deadline" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" placeholder="选择报名截止时间" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开标时间" prop="bidOpeningTime">
              <el-date-picker v-model="form.bidOpeningTime" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm" placeholder="选择开标时间" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户类型" prop="customerType">
              <el-select v-model="form.customerType" placeholder="选择客户类型" class="full-width">
                <el-option v-for="t in customerTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级" prop="priority" required>
              <el-select v-model="form.priority" placeholder="选择优先级" class="full-width">
                <el-option v-for="item in priorities" :key="item.value" :label="item.label" :value="item.value">
                  <div class="priority-option"><span>{{ item.label }} · {{ item.desc }}</span><small>{{ item.standard }}</small></div>
                </el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目类型" prop="projectType">
              <el-select v-model="form.projectType" placeholder="选择项目类型（选填）" clearable class="full-width">
                <el-option v-for="t in projectTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <!-- 联系人1 -->
          <el-col :span="24"><div class="contact-group-title">联系人1</div></el-col>
          <el-col :span="4"><el-form-item label="姓名" prop="contact" label-width="56px"><el-input v-model="form.contact" placeholder="联系人姓名" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="手机号" prop="phone" label-width="64px"><el-input v-model="form.phone" placeholder="手机号" /></el-form-item></el-col>
          <el-col :span="7"><el-form-item label="座机" prop="landline" label-width="56px"><el-input v-model="form.landline" placeholder="座机（如 010-12345678）" /></el-form-item></el-col>
          <el-col :span="7"><el-form-item label="邮箱" prop="mail" label-width="56px"><el-input v-model="form.mail" placeholder="邮箱" /></el-form-item></el-col>
          <!-- 联系人2 -->
          <el-col :span="24"><div class="contact-group-title">联系人2 <span class="optional-tag">选填</span></div></el-col>
          <el-col :span="4"><el-form-item label="姓名" label-width="56px"><el-input v-model="form.contact2" placeholder="联系人姓名" /></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="手机号" prop="phone2" label-width="64px"><el-input v-model="form.phone2" placeholder="手机号" /></el-form-item></el-col>
          <el-col :span="7"><el-form-item label="座机" prop="landline2" label-width="56px"><el-input v-model="form.landline2" placeholder="座机" /></el-form-item></el-col>
          <el-col :span="7"><el-form-item label="邮箱" prop="mail2" label-width="56px"><el-input v-model="form.mail2" placeholder="邮箱" /></el-form-item></el-col>
          <!-- 描述 -->
          <el-col :span="24"><el-form-item label="标讯描述"><el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入标讯描述" maxlength="5000" show-word-limit /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="标讯信息"><el-input v-model="form.tenderInfo" type="textarea" :rows="3" placeholder="请输入标讯信息（选填）" maxlength="5000" show-word-limit /></el-form-item></el-col>
          <!-- AI 粘贴识别 -->
          <el-col :span="24">
            <el-form-item label="粘贴识别">
              <div class="paste-hint">[粘贴识别] 或文字输入，系统将智能拆分回填标讯信息</div>
              <el-input v-model="form.pastedText" type="textarea" :rows="4" maxlength="500000" show-word-limit placeholder="直接粘贴招标公告正文，系统将自动识别并回填字段" :disabled="parsingDocument" />
              <div class="paste-actions"><el-button type="primary" :icon="DocumentCopy" :loading="parsingDocument" @click="$emit('parse-paste')">识别粘贴文字</el-button></div>
            </el-form-item>
          </el-col>
          <!-- 标讯文件 -->
          <el-col :span="24">
            <el-form-item label="标讯文件">
              <div class="upload-hint">支持 PDF/Word 文件上传（≤50MB），上传即保存，自动 AI 解析回填表单字段</div>
              <el-upload class="manual-tender-upload" :auto-upload="false" @change="(file, fileList) => $emit('file-change', file, fileList)" :file-list="form.attachments" :limit="10" :accept="acceptFileTypes" multiple drag>
                <el-icon class="el-icon--upload"><Upload /></el-icon>
                <div class="el-upload__text">{{ parsingDocument ? 'DeepSeek/AI 解析中...' : '将文件拖到此处，或点击选择附件（PDF/Word ≤50MB）' }}</div>
              </el-upload>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { DocumentCopy, Upload } from '@element-plus/icons-vue'
import { chinaRegionOptions } from '@/components/common/chinaRegionData.js'
import { useRegionCascaderValue, REGION_CASCADER_PROPS, createRegionCascaderAutoClose } from '@/composables/useRegionCascaderValue.js'

const innerFormRef = ref(null)

defineExpose({ validate: () => innerFormRef.value?.validate() })

const props = defineProps({
  activeTab: String,
  form: Object,
  rules: Object,
  regions: Array,
  customerTypes: Array,
  projectTypes: Array,
  priorities: Array,
  saving: Boolean,
  isReadOnly: Boolean,
  parsingDocument: Boolean,
  acceptFileTypes: String,
})

defineEmits(['parse-paste', 'file-change'])

/**
 * 双向绑定 cascader path ↔ props.form.region（省+市 / 直辖市仅市 / 港澳台仅本级）。
 */
const regionCascaderValue = useRegionCascaderValue(
  () => props.form.region,
  (v) => { props.form.region = v },
  { emptyValue: '' },
)

// CO-381: 选中市级后自动关闭下拉框
const regionCascaderRef = ref(null)
const onRegionCascaderChange = createRegionCascaderAutoClose(regionCascaderRef)
</script>

<style scoped>
.tab-content { margin-bottom: 80px; }
.full-width { width: 100%; }
.contact-group-title { font-weight: 600; font-size: 14px; margin: 12px 0 4px; color: var(--el-text-color-primary); }
.optional-tag { font-size: 12px; color: var(--el-text-color-secondary); font-weight: 400; margin-left: 4px; }
.priority-option { display: flex; flex-direction: column; gap: 2px; line-height: 1.25; }
.priority-option small { color: var(--el-text-color-secondary); font-size: 12px; }
.paste-hint, .upload-hint { margin-bottom: 8px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.4; }
.paste-actions { display: flex; justify-content: flex-end; margin-top: 8px; }
.manual-tender-upload { width: 100%; }
.manual-tender-upload :deep(.el-upload) { display: block; width: 100%; }
.manual-tender-upload :deep(.el-upload-dragger) { width: 100%; box-sizing: border-box; }
.manual-tender-upload :deep(.el-upload-list) { width: 100%; }
</style>
