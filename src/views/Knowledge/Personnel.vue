<template>
  <div class="personnel-container">
    <div class="page-header">
      <h2>人员库 — 投标团队成员管理</h2>
      <el-button v-if="canAdd" type="primary" @click="openForm(null)">
        <el-icon><Plus /></el-icon> 新增人员
      </el-button>
    </div>

    <el-card class="filter-card">
      <div class="filter-title">筛选条件</div>
      <el-form :inline="true" :model="filters" class="filter-form">
        <!-- 第一行：主搜索 + 性别 + 状态 + 持有证书 -->
        <el-form-item label="姓名/工号">
          <el-input v-model="filters.keyword" placeholder="实时搜索姓名或工号" clearable style="width:180px" @input="debouncedLoad(280)" />
        </el-form-item>
        <el-form-item label="性别">
          <el-select v-model="filters.gender" placeholder="全部" clearable style="width:90px" @change="loadData">
            <el-option v-for="g in GENDER_OPTIONS" :key="g.value" :label="g.label" :value="g.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width:110px" @change="loadData">
            <el-option label="在职" value="ACTIVE" />
            <el-option label="停用" value="INACTIVE" />
            <el-option label="离职" value="TERMINATED" />
          </el-select>
        </el-form-item>
        <el-form-item label="持有证书">
          <el-input v-model="filters.certificateKeyword" placeholder="如：建造师" clearable style="width:160px" @input="debouncedLoad(320)" />
        </el-form-item>

        <!-- 第二行：多选学历 + 学习形式 + 专业 -->
        <el-form-item label="最高学历">
          <el-select v-model="filters.highestEducations" multiple collapse-tags placeholder="多选" style="width:210px" @change="loadData">
            <el-option v-for="e in EDUCATION_OPTIONS" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item label="学习形式">
          <el-select v-model="filters.studyForms" multiple collapse-tags placeholder="多选" style="width:180px" @change="loadData">
            <el-option v-for="s in STUDY_FORM_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="专业">
          <el-input v-model="filters.majorKeyword" placeholder="专业模糊" clearable style="width:140px" @input="debouncedLoad(320)" />
        </el-form-item>

        <!-- 第三行：入职时间范围 + 证书状态多选 + 操作按钮 -->
        <el-form-item label="入职时间">
          <el-date-picker
            v-model="entryDateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            value-format="YYYY-MM-DD"
            style="width:240px"
            @change="onEntryDateRangeChange"
          />
        </el-form-item>
        <el-form-item label="证书状态">
          <el-select v-model="filters.certificateStatuses" multiple collapse-tags placeholder="多选" style="width:210px" @change="loadData">
            <el-option v-for="c in CERT_STATUS_OPTIONS" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">刷新</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" v-loading="loading">
      <el-table :data="records" stripe style="width:100%" @row-click="openDetail">
        <!-- 序号 -->
        <el-table-column type="index" label="序号" width="60" align="center" />
        <!-- 工号（加粗） -->
        <el-table-column prop="employeeNumber" label="工号" width="90" align="center">
          <template #default="{row}">
            <span class="emp-no">{{ row.employeeNumber }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="100" />
        <!-- 性别 Tag -->
        <el-table-column label="性别" width="70" align="center">
          <template #default="{row}">
            <el-tag v-if="row.gender" :type="row.gender==='男'?'primary':'danger'" size="small">{{ row.gender }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="入职时间" width="110" align="center">
          <template #default="{row}">{{ row.entryDate || '-' }}</template>
        </el-table-column>
        <!-- 入职年限（自动计算） -->
        <el-table-column label="入职年限" width="90" align="center">
          <template #default="{row}">
            <span v-if="row.yearsOfService != null">{{ row.yearsOfService }} 年</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号码" width="120" />
        <!-- 最高学历 Tag -->
        <el-table-column label="最高学历" width="90" align="center">
          <template #default="{row}">
            <el-tag v-if="row.highestEducation && row.highestEducation !== '-'" size="small" type="info">{{ row.highestEducation }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 证书数量（可点击跳转证书 Tab） -->
        <el-table-column label="证书数量" width="100" align="center">
          <template #default="{row}">
            <el-tag
              :type="row.certificateCount > 0 ? 'success' : 'info'"
              class="cert-count-clickable"
              @click.stop="openDetail(row, 'certificate')"
            >
              {{ row.certificateCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 即将到期（红色数字 + 警示图标） -->
        <el-table-column label="即将到期" width="100" align="center">
          <template #default="{row}">
            <span v-if="row.expiringCertificatesCount > 0" class="expiry-warn" @click.stop="openDetail(row, 'certificate')">
              <el-icon><Warning /></el-icon> {{ row.expiringCertificatesCount }}
            </span>
            <span v-else class="expiry-ok">0</span>
          </template>
        </el-table-column>
        <el-table-column prop="statusLabel" label="状态" width="80" align="center">
          <template #default="{row}">
            <el-tag :type="row.status==='ACTIVE'?'success':row.status==='TERMINATED'?'danger':'info'">{{ row.statusLabel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right" align="center">
          <template #default="{row}">
            <el-button type="primary" link size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button type="primary" link size="small" @click.stop="openForm(row)">编辑</el-button>

            <template v-if="row.status === 'INACTIVE'">
              <el-button type="success" link size="small" @click.stop="handleRestore(row)">恢复</el-button>
            </template>
            <template v-else>
              <el-button type="danger" link size="small" @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 详情抽屉（800px，4 Tab，对应蓝图 4.3 "查看证书"） -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="800px" :with-header="false">
      <div class="detail-header">
        <div class="detail-title">
          <span class="name">{{ current.name }}</span>
          <span class="emp-no">{{ current.employeeNumber }}</span>
          <el-tag v-if="current.highestEducation && current.highestEducation !== '-'" size="small" type="info" class="ml-8">{{ current.highestEducation }}</el-tag>
        </div>
        <div class="detail-actions">
          <el-button v-if="canEdit" type="primary" size="small" @click="openFormFromDetail">编辑</el-button>
          <el-button size="small" @click="detailVisible = false">关闭</el-button>
        </div>
      </div>

      <el-tabs v-model="detailActiveTab" type="border-card" class="detail-tabs">
        <!-- Tab 1: 基础信息（平铺） -->
        <el-tab-pane label="基础信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="姓名">{{ current.name }}</el-descriptions-item>
            <el-descriptions-item label="工号">{{ current.employeeNumber }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ current.gender || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入职日期">{{ current.entryDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="出生日期">{{ current.birthDate || '-' }}</el-descriptions-item>
            <el-descriptions-item label="入职年限">{{ current.yearsOfService != null ? current.yearsOfService + ' 年' : '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号码">{{ current.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ current.departmentName }}</el-descriptions-item>
            <el-descriptions-item label="最高学历">{{ current.highestEducation || current.education || '-' }}</el-descriptions-item>
            <el-descriptions-item label="技术职称">{{ current.technicalTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">{{ current.statusLabel }}</el-descriptions-item>
          </el-descriptions>
          <div v-if="current.remark" class="remark-display">
            <label>备注：</label>{{ current.remark }}
          </div>
        </el-tab-pane>

        <!-- Tab 2: 教育经历（倒序表格） -->
        <el-tab-pane label="教育经历" name="education">
          <el-table v-if="current.educations && current.educations.length" :data="sortedEducations" stripe size="small">
            <el-table-column prop="schoolName" label="学校名称" min-width="160" />
            <el-table-column label="时间" width="140">
              <template #default="{row}">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
            </el-table-column>
            <el-table-column prop="highestEducation" label="最高学历" width="80" />
            <el-table-column prop="studyForm" label="学习形式" width="100" />
            <el-table-column prop="major" label="专业" min-width="120" />
            <el-table-column label="最高学历学校" width="100" align="center">
              <template #default="{row}">{{ row.isHighestEducationSchool ? '是' : '否' }}</template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-hint">暂无教育经历记录</div>
        </el-tab-pane>

        <!-- Tab 3: 证书与职称（含附件预览/下载） -->
        <el-tab-pane label="证书与职称" name="certificate">
          <el-table v-if="current.certificates && current.certificates.length" :data="current.certificates" stripe size="small">
            <el-table-column prop="name" label="证书名称" min-width="140" />
            <el-table-column prop="certificateNumber" label="编号" width="120" />
            <el-table-column prop="typeLabel" label="类型" width="90" />
            <el-table-column prop="title" label="职称" width="70" />
            <el-table-column label="永久有效" width="80" align="center">
              <template #default="{row}">{{ row.isPermanent ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column prop="expiryDate" label="到期日" width="100" />
            <el-table-column label="状态" width="70">
              <template #default="{row}">
                <el-tag :type="row.expiryTagType" size="small">{{ row.expired ? '过期' : '有效' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="附件" width="120">
              <template #default="{row}">
                <el-link v-if="row.attachmentUrl" :href="row.attachmentUrl" target="_blank" type="primary" @click.stop>下载</el-link>
                <span v-else class="text-muted">无</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-hint">暂无证书记录</div>
          <div v-if="current.expiringCertificatesCount > 0" class="expiry-hint">
            <el-icon><Warning /></el-icon> 该人员有 {{ current.expiringCertificatesCount }} 个证书即将到期（60 天内）
          </div>
        </el-tab-pane>

        <!-- Tab 4: 操作日志（基础展示，完整持久化待后续 h5） -->
        <el-tab-pane label="操作日志" name="log">
          <div class="log-hint">
            变更记录已开始在编辑路径收集（工号变更、教育经历增删、证书替换等）。<br>
            完整操作日志持久化与高级查询将在“操作日志记录范围” h5 完善。本 Tab 当前展示基础变更提示。
          </div>
          <el-table :data="mockOperationLogs" stripe size="small" v-if="mockOperationLogs.length">
            <el-table-column prop="time" label="时间" width="160" />
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="type" label="类型" width="100" />
            <el-table-column prop="summary" label="变更摘要" min-width="200" />
          </el-table>
          <div v-else class="empty-hint">暂无操作日志（新建人员或无变更时为空）</div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 表单弹窗（改造为 3 Tab，对应蓝图 4.3 新增证书） -->
    <el-dialog v-model="formVisible" :title="isEdit?'编辑人员':'新增人员'" width="720px">
      <el-tabs v-model="activeTab" type="border-card">
        <!-- Tab 1: 基础信息 -->
        <el-tab-pane label="基础信息" name="basic">
          <el-form ref="formRef" :model="form" label-width="100px">
            <el-form-item label="姓名" required><el-input v-model="form.name" /></el-form-item>
            <el-form-item label="工号" required>
          <el-input v-model="form.employeeNumber" />
          <div v-if="isEmployeeNumberChanged" class="form-warning">
            ⚠️ 修改工号将影响外部对账，请确认必要性
          </div>
        </el-form-item>
            <el-form-item label="部门"><el-input v-model="form.departmentName" /></el-form-item>
            <!-- 4.3 查看证书 h5 新增字段 -->
            <el-form-item label="性别">
              <el-select v-model="form.gender" placeholder="请选择" style="width:100%">
                <el-option label="男" value="男" />
                <el-option label="女" value="女" />
              </el-select>
            </el-form-item>
            <el-form-item label="入职日期">
              <el-date-picker v-model="form.entryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
            <el-form-item label="出生日期">
              <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
            <el-form-item label="手机号码"><el-input v-model="form.phone" /></el-form-item>
            <el-form-item label="学历"><el-input v-model="form.education" /></el-form-item>
            <el-form-item label="技术职称"><el-input v-model="form.technicalTitle" /></el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- Tab 2: 教育经历（新增，支持多条） -->
        <el-tab-pane label="教育经历" name="education">
          <div v-for="(edu, idx) in form.educations" :key="idx" class="edu-item">
            <el-form-item label="学校名称" required>
              <el-input v-model="edu.schoolName" placeholder="如：清华大学" />
            </el-form-item>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="入学时间">
                  <el-date-picker v-model="edu.startDate" type="month" value-format="YYYY-MM" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="毕业时间">
                  <el-date-picker v-model="edu.endDate" type="month" value-format="YYYY-MM" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="最高学历">
                  <el-select v-model="edu.highestEducation" style="width:100%" placeholder="请选择">
                    <el-option label="初中" value="初中" />
                    <el-option label="高中" value="高中" />
                    <el-option label="中专" value="中专" />
                    <el-option label="大专" value="大专" />
                    <el-option label="本科" value="本科" />
                    <el-option label="硕士" value="硕士" />
                    <el-option label="博士" value="博士" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="学习形式">
                  <el-select v-model="edu.studyForm" style="width:100%" placeholder="请选择">
                    <el-option label="全日制" value="全日制" />
                    <el-option label="非全日制" value="非全日制" />
                    <el-option label="网络教育" value="网络教育" />
                    <el-option label="自学考试" value="自学考试" />
                    <el-option label="其他" value="其他" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="专业">
              <el-input v-model="edu.major" placeholder="如：计算机科学与技术" />
            </el-form-item>
            <el-form-item label="最高学历学校">
              <el-checkbox v-model="edu.isHighestEducationSchool">是否为最高学历学校</el-checkbox>
            </el-form-item>
            <el-button type="danger" size="small" link @click="removeEducation(idx)">删除此教育经历</el-button>
          </div>

          <el-button type="primary" plain size="small" @click="addEducation">+ 添加教育经历</el-button>
          <div v-if="form.educations.length === 0" class="form-warning" style="margin-top:8px;">
            ⚠️ 建议至少保留 1 条教育经历
          </div>
        </el-tab-pane>

        <!-- Tab 3: 证书与职称（蓝图 4.3 "新增证书" h5，含证书附件必填） -->
        <el-tab-pane label="证书与职称" name="certificate">
          <div v-for="(cert,idx) in form.certificates" :key="idx" class="cert-item">
            <el-form-item label="证书名称"><el-input v-model="cert.name" placeholder="如：一级建造师" /></el-form-item>
            <el-form-item label="证书编号"><el-input v-model="cert.certificateNumber" /></el-form-item>
            <el-form-item label="证书类型">
              <el-select v-model="cert.type" style="width:100%">
                <el-option label="建造师" value="CONSTRUCTOR" />
                <el-option label="PMP" value="PMP" />
                <el-option label="工程师" value="ENGINEER" />
                <el-option label="会计师" value="ACCOUNTANT" />
                <el-option label="律师" value="LAWYER" />
                <el-option label="安全工程师" value="SECURITY" />
                <el-option label="IT类证书" value="IT" />
                <el-option label="其他" value="OTHER" />
              </el-select>
            </el-form-item>
            <el-form-item label="到期日期">
              <el-date-picker v-model="cert.expiryDate" type="date" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>

            <!-- 蓝图 4.3 "新增证书" Tab3 "证书附件" 必填 + 校验（PDF/JPG/PNG ≤10MB） -->
            <el-form-item label="职称">
              <el-select v-model="cert.title" placeholder="请选择" style="width:100%">
                <el-option label="初级" value="初级" />
                <el-option label="中级" value="中级" />
                <el-option label="高级" value="高级" />
              </el-select>
            </el-form-item>
            <el-form-item label="永久有效">
              <el-checkbox v-model="cert.isPermanent">永久有效</el-checkbox>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="cert.remark" maxlength="500" show-word-limit />
            </el-form-item>
            <el-form-item label="证书附件" required>
              <el-upload
                :auto-upload="false"
                :limit="1"
                accept=".pdf,.jpg,.jpeg,.png"
                :on-change="(f) => onCertAttachmentChange(f, idx)"
                :on-remove="() => onCertAttachmentRemove(idx)"
                :before-upload="beforeCertAttachmentUpload"
                :show-file-list="false"
              >
                <el-button type="primary" plain size="small">选择附件</el-button>
              </el-upload>
              <div v-if="cert.attachmentName" class="attach-info">
                {{ cert.attachmentName }}
                <el-button type="danger" link size="small" @click="onCertAttachmentRemove(idx)">移除</el-button>
              </div>
              <div class="el-upload__tip">仅支持 PDF/JPG/PNG，≤10MB（蓝图要求）</div>
            </el-form-item>

            <div v-if="cert.id" style="font-size:12px;color:#909399;margin-bottom:4px;">
              修改附件将替换原文件（原文件软删除）
            </div>
            <el-button type="danger" size="small" link @click="form.certificates.splice(idx,1)">删除证书</el-button>
          </div>
          <el-button type="primary" plain size="small" @click="addCertificate">+ 添加证书</el-button>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="formVisible=false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 删除确认弹窗（蓝图 4.3「删除人员」强确认要求） -->
    <el-dialog
      v-model="deleteDialogVisible"
      title="删除人员档案"
      width="520px"
      :close-on-click-modal="false"
      @close="cancelDelete"
    >
      <div v-if="deleteTarget">
        <p>确认删除以下人员的档案？</p>
        <p><strong>工号：</strong>{{ deleteTarget.employeeNumber }}</p>
        <p><strong>姓名：</strong>{{ deleteTarget.name }}</p>

        <div v-if="deleteTarget.certificates && deleteTarget.certificates.length > 0" class="delete-warning">
          ⚠️ 该人员持有 {{ deleteTarget.certificates.length }} 张证书，删除后这些证书的到期提醒将停止。
        </div>

        <el-form label-width="80px" style="margin-top: 16px;">
          <el-form-item label="删除原因" required>
            <el-input
              v-model="deleteReason"
              type="textarea"
              :rows="3"
              placeholder="请填写删除原因（必填）"
              maxlength="200"
              show-word-limit
            />
          </el-form-item>

          <el-form-item>
            <el-checkbox v-model="deleteConfirmed">
              我已确认该人员档案可以删除
            </el-checkbox>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="cancelDelete">取消</el-button>
        <el-button
          type="danger"
          :loading="deleting"
          :disabled="!deleteReason.trim() || !deleteConfirmed"
          @click="confirmDelete"
        >
          确认删除
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Warning, CircleClose } from '@element-plus/icons-vue'
import personnelApi from '@/api/modules/personnel.js'

const records = ref([])
const loading = ref(false)
const filters = reactive({
  keyword: '',
  status: '',
  // === 筛选与搜索 h5 新增 ===
  gender: '',
  highestEducations: [],   // 多选：['本科','硕士']
  studyForms: [],          // 多选：['全日制','非全日制']
  majorKeyword: '',
  entryDateFrom: null,
  entryDateTo: null,
  certificateKeyword: '',
  certificateStatuses: []  // 多选：['VALID','EXPIRING','EXPIRED']
})

const loadData = async () => {
  loading.value = true
  try {
    const { data } = await personnelApi.getList(filters)
    records.value = data || []
  } catch { ElMessage.error('加载失败') }
  finally { loading.value = false }
}

// 日期范围（UI 用，同步拆分到 filters 的 from/to 供 API）
const entryDateRange = ref(null)

function onEntryDateRangeChange(val) {
  if (Array.isArray(val) && val.length === 2) {
    filters.entryDateFrom = val[0]
    filters.entryDateTo = val[1]
  } else {
    filters.entryDateFrom = null
    filters.entryDateTo = null
  }
  loadData()
}

// 选项常量（与后端存储值一致，来自教育/证书 h5）
const GENDER_OPTIONS = [
  { label: '男', value: '男' },
  { label: '女', value: '女' }
]
const EDUCATION_OPTIONS = ['初中', '高中', '中专', '大专', '本科', '硕士', '博士']
const STUDY_FORM_OPTIONS = ['全日制', '非全日制', '网络教育', '自学考试']
const CERT_STATUS_OPTIONS = [
  { label: '有效', value: 'VALID' },
  { label: '即将到期（60天内）', value: 'EXPIRING' },
  { label: '已过期', value: 'EXPIRED' }
]
const detailVisible = ref(false)
const formVisible = ref(false)
const submitting = ref(false)
const current = ref({})
const isEdit = ref(false)
const activeTab = ref('basic')  // 当前激活的 Tab（基础信息 / 教育经历 / 证书与职称）
const originalEmployeeNumber = ref('') // 用于编辑时判断工号是否被修改（显示警示）

// 4.3 "新增证书" Tab3 证书附件：每个 idx 对应一个待上传 File（不进入 JSON payload）
const certAttachmentFiles = ref({}) // idx -> File
const certAttachmentNames = ref({}) // 仅显示用

// 4.3 "新增证书" h5 角色矩阵（bid_admin / bid_lead / bid_specialist 可新增，sales 不可见按钮）
const userStore = useUserStore()
const canAdd = computed(() => {
  const r = userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || ''
  return ['bid_admin', 'bid_lead', 'bid_specialist'].includes(r)
})

const form = ref({ 
  name:'', 
  employeeNumber:'', 
  departmentName:'', 
  gender: '',
  entryDate: null,
  birthDate: null,
  phone: '',
  education:'', 
  technicalTitle:'',
  remark: '',
  certificates: [], 
  educations: []   // 新增：教育经历（对应蓝图 4.3 Tab 2）
})

// 简单防抖（keyword / 专业 / 持有证书名 实时输入时使用）
let searchTimer = null
function debouncedLoad(delay = 350) {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadData(), delay)
}

const detailActiveTab = ref('basic')
const detailTitle = computed(() => current.value?.name ? `${current.value.name}（${current.value.employeeNumber || ''}）详情` : '人员详情')
const sortedEducations = computed(() => {
  const list = current.value?.educations || []
  return [...list].sort((a, b) => (b.startDate || '').localeCompare(a.startDate || ''))
})
// 占位：操作日志（完整持久化待后续 h5，当前展示空或模拟）
const mockOperationLogs = computed(() => {
  if (!current.value?.id) return []
  // 未来可替换为真实查询 /api/personnel/{id}/operation-logs
  return []
})

const canEdit = computed(() => {
  const r = userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || ''
  return ['bid_admin', 'bid_lead', 'bid_specialist'].includes(r)
})

// 重置所有筛选条件（包括新增的多选/日期/持有证书等）
function resetFilters() {
  Object.assign(filters, {
    keyword: '',
    status: '',
    gender: '',
    highestEducations: [],
    studyForms: [],
    majorKeyword: '',
    entryDateFrom: null,
    entryDateTo: null,
    certificateKeyword: '',
    certificateStatuses: []
  })
  entryDateRange.value = null
  loadData()
}

// 实时筛选监听（文本类用防抖，多选/下拉/日期立即触发）
watch(
  () => [
    filters.keyword,
    filters.majorKeyword,
    filters.certificateKeyword,
    filters.gender,
    filters.status,
    filters.highestEducations,
    filters.studyForms,
    filters.certificateStatuses,
    filters.entryDateFrom,
    filters.entryDateTo
  ],
  () => {
    // 文本输入走防抖，其它立即刷新
    const isText = filters.keyword || filters.majorKeyword || filters.certificateKeyword
    if (isText && (filters.keyword?.length > 0 || filters.majorKeyword?.length > 0 || filters.certificateKeyword?.length > 0)) {
      debouncedLoad(320)
    } else {
      loadData()
    }
  },
  { deep: true }
)

const openDetail = (row, targetTabOrColumn = 'basic') => {
  if (!row || !row.id) return
  current.value = row
  // 证书数量点击传 'certificate' 字符串；整行点击传 column 对象或 undefined
  const tab = typeof targetTabOrColumn === 'string' ? targetTabOrColumn : 'basic'
  detailActiveTab.value = tab
  detailVisible.value = true
}
const openForm = (row) => {
  isEdit.value = !!row
  if (row) {
    form.value = {
      id: row.id,
      name: row.name,
      employeeNumber: row.employeeNumber,
      departmentName: row.departmentName,
      gender: row.gender || '',
      entryDate: row.entryDate || null,
      birthDate: row.birthDate || null,
      phone: row.phone || '',
      education: row.education,
      technicalTitle: row.technicalTitle,
      remark: row.remark || '',
      certificates: (row.certificates || []).map(c => ({
        ...c,
        attachmentName: c.attachmentUrl ? (c.attachmentUrl.split('/').pop() || c.attachmentUrl) : ''
      })),
      educations: (row.educations || []).map(e => ({...e}))
    }
    originalEmployeeNumber.value = row.employeeNumber || ''
  } else {
    form.value = {
      name:'',
      employeeNumber:'',
      departmentName:'',
      gender: '',
      entryDate: null,
      birthDate: null,
      phone: '',
      education:'',
      technicalTitle:'',
      remark: '',
      certificates: [],
      educations: []
    }
    originalEmployeeNumber.value = ''
  }
  activeTab.value = 'basic'
  formVisible.value = true
}

const openFormFromDetail = () => {
  detailVisible.value = false
  // 延迟打开表单，避免抽屉动画冲突
  setTimeout(() => openForm(current.value), 300)
}

const handleSubmit = async () => {
  // 蓝图 4.3 "新增证书" 字段校验（至少 1 条教育经历；证书若填写则附件必填）
  // 先检查附件（基于当前数组索引）
  const hasCertWithoutAttach = (form.value.certificates || []).some((c, idx) => c && c.name && !c.attachmentUrl && !certAttachmentFiles.value[idx])
  if (hasCertWithoutAttach) {
    ElMessage.error('请为已填写的证书上传附件（PDF/JPG/PNG ≤10MB）'); return
  }

  // 清理空行（用户可能点了 + 但没填完整；空 cert 行不发给后端，避免 DB not null 失败）
  form.value.educations = (form.value.educations || []).filter(e => e && (e.schoolName || e.highestEducation || e.studyForm))
  const origCerts = form.value.certificates || []
  form.value.certificates = origCerts.filter(c => c && (c.name || c.certificateNumber))
  // 同步清理已删除行的 pending 文件（按原索引保留有效行的）
  const newFiles = {}
  let newIdx = 0
  origCerts.forEach((c, oldIdx) => {
    if (c && (c.name || c.certificateNumber) && certAttachmentFiles.value[oldIdx]) {
      newFiles[newIdx] = certAttachmentFiles.value[oldIdx]
      newIdx++
    }
  })
  certAttachmentFiles.value = newFiles

  if (!form.value.name || !form.value.employeeNumber) { ElMessage.warning('姓名和工号必填'); return }
  if (!form.value.educations || form.value.educations.length === 0) {
    ElMessage.error('请至少添加1条完整的教育经历（学校、学历、学习形式、日期必填）'); return
  }
  // 逐条检查教育经历必填字段（对应 DB not null + 蓝图 Tab2 "全部履历"）
  for (const e of form.value.educations) {
    if (!e.schoolName || !e.highestEducation || !e.studyForm || !e.startDate || !e.endDate) {
      ElMessage.error('教育经历每条都必须填写学校、最高学历、学习形式、入学/毕业时间'); return
    }
  }

  submitting.value = true
  try {
    let createdOrUpdated = null
    if (isEdit.value) {
      const res = await personnelApi.update(form.value.id, form.value)
      const result = res?.data || {}
      createdOrUpdated = result.personnel || result

      if (result.warnings && result.warnings.length > 0) {
        ElMessageBox.alert(
          result.warnings.join('\n'),
          '更新成功（含警示）',
          { type: 'warning', confirmButtonText: '我知道了' }
        )
      } else {
        ElMessage.success('更新成功')
      }
    } else {
      const res = await personnelApi.create(form.value)
      ElMessage.success('创建成功')
      createdOrUpdated = res?.data
    }

    formVisible.value = false

    // 上传待处理的证书附件（先创建人员/证书记录，再上传文件获真实 url）
    if (createdOrUpdated && Object.keys(certAttachmentFiles.value).length > 0) {
      await uploadPendingCertAttachments(createdOrUpdated)
    }

    await loadData()

    // 蓝图要求：保存成功后列表自动刷新，新人员高亮显示 3 秒
    if (!isEdit.value && createdOrUpdated) {
      highlightNewPerson(createdOrUpdated.employeeNumber)
    }
  } catch (e) {
    ElMessage.error(e.message || e?.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

// 新人员高亮 3s（蓝图 "保存成功后" 要求）
function highlightNewPerson(empNo) {
  setTimeout(() => {
    const rows = document.querySelectorAll('.el-table__row')
    rows.forEach(row => {
      const noCell = row.querySelector('.emp-no')
      if (noCell && noCell.textContent.trim() === String(empNo)) {
        row.classList.add('new-person-highlight')
        setTimeout(() => row.classList.remove('new-person-highlight'), 3000)
      }
    })
  }, 200)
}

const deleteDialogVisible = ref(false)
const deleteTarget = ref(null)
const deleteReason = ref('')
const deleteConfirmed = ref(false)
const deleting = ref(false)

const openDeleteDialog = (row) => {
  deleteTarget.value = row
  deleteReason.value = ''
  deleteConfirmed.value = false
  deleteDialogVisible.value = true
}

const handleDelete = (row) => {
  openDeleteDialog(row)
}

const confirmDelete = async () => {
  if (!deleteReason.value.trim() || !deleteConfirmed.value) return

  deleting.value = true
  try {
    await personnelApi.delete(deleteTarget.value.id, deleteReason.value.trim())
    ElMessage.success('删除成功')
    deleteDialogVisible.value = false
    loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

const cancelDelete = () => {
  deleteDialogVisible.value = false
  deleteReason.value = ''
  deleteConfirmed.value = false
}

const handleRestore = async (row) => {
  try {
    await ElMessageBox.confirm(`确认恢复人员「${row.name}」？`, '恢复确认')
    await personnelApi.restore(row.id)
    ElMessage.success('恢复成功')
    loadData()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('恢复失败')
    }
  }
}

const hasExpiringCerts = (row) => row.certificates?.some(c => c.remainingDays != null && c.remainingDays > 0 && c.remainingDays <= 60)
const hasExpiredCerts = (row) => row.certificates?.some(c => c.expired)

// 编辑模式下工号是否被修改（用于显示蓝图要求的警示）
const isEmployeeNumberChanged = computed(() => {
  return isEdit.value && originalEmployeeNumber.value && form.value.employeeNumber !== originalEmployeeNumber.value
})

// 教育经历行操作（Tab 2）
const addEducation = () => {
  form.value.educations.push({
    schoolName: '',
    startDate: null,
    endDate: null,
    highestEducation: '',
    studyForm: '',
    major: '',
    isHighestEducationSchool: false
  })
}

const removeEducation = (idx) => {
  form.value.educations.splice(idx, 1)
}

// 4.3 "新增证书" h5 Tab3 附件支持（蓝图：必填、PDF/JPG/PNG ≤10MB）
function beforeCertAttachmentUpload(file) {
  const okType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
  const okSize = file.size / 1024 / 1024 < 10
  if (!okType) { ElMessage.error('仅支持 PDF/JPG/PNG'); return false }
  if (!okSize) { ElMessage.error('附件不能超过10MB'); return false }
  return true
}

function onCertAttachmentChange(uploadFile, idx) {
  const f = uploadFile.raw
  if (!f) return
  form.value.certificates[idx] = form.value.certificates[idx] || {}
  form.value.certificates[idx].attachmentName = f.name
  certAttachmentFiles.value[idx] = f
  // 临时标记，submit 时会上传换成真实 url
  form.value.certificates[idx].attachmentUrl = `pending:${f.name}`
}

function onCertAttachmentRemove(idx) {
  if (form.value.certificates[idx]) {
    form.value.certificates[idx].attachmentName = ''
    form.value.certificates[idx].attachmentUrl = ''
  }
  delete certAttachmentFiles.value[idx]
}

function addCertificate() {
  form.value.certificates.push({
    name: '', certificateNumber: '', type: 'OTHER', expiryDate: null,
    attachmentName: '', attachmentUrl: '',
    title: '', isPermanent: false, remark: ''
  })
}

// 上传待处理的证书附件（create 后拿到 cert id，再调后端 upload 端点）
async function uploadPendingCertAttachments(createdPerson) {
  if (!createdPerson || !createdPerson.certificates || !createdPerson.id) return
  const certs = createdPerson.certificates || []
  for (let i = 0; i < form.value.certificates.length; i++) {
    const localFile = certAttachmentFiles.value[i]
    const serverCert = certs[i]
    if (localFile && serverCert && serverCert.id) {
      try {
        await personnelApi.uploadCertAttachment(createdPerson.id, serverCert.id, localFile)
      } catch (e) {
        ElMessage.warning(`证书「${form.value.certificates[i].name || '未命名'}」附件上传失败，可在编辑时重试`)
      }
    }
  }
  // 清空本地文件引用
  certAttachmentFiles.value = {}
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.personnel-container { padding: 24px; }
.page-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:20px; h2{font-weight:600;color:var(--el-text-color-primary);margin:0} }
.filter-card,.table-card{ border-radius:8px; border:1px solid var(--el-border-color-lighter); box-shadow:0 2px 8px rgba(0,0,0,.05); }
.expiry-warn{color:var(--el-color-warning);display:flex;align-items:center;gap:4px;font-size:13px}
.expiry-danger{color:var(--el-color-danger);display:flex;align-items:center;gap:4px;font-size:13px}
.expiry-ok{color:var(--el-color-success);font-size:13px}
.cert-item{border:1px dashed var(--el-border-color-lighter);border-radius:4px;padding:12px;margin-bottom:12px}
.edu-item{border:1px dashed var(--el-border-color-lighter);border-radius:4px;padding:12px;margin-bottom:12px}
.form-warning {
  color: var(--el-color-warning);
  font-size: 12px;
  margin-top: 4px;
  line-height: 1.4;
}

.delete-warning {
  color: var(--el-color-danger);
  background-color: #fef0f0;
  padding: 8px 12px;
  border-radius: 4px;
  margin: 12px 0;
  font-size: 13px;
  line-height: 1.5;
}

/* 4.3 "查看证书" h5 列表 11 列 + 800px 4 Tab 抽屉样式 */
.emp-no { font-weight: 600; color: var(--el-text-color-primary); }
.cert-count-clickable { cursor: pointer; user-select: none; }
.cert-count-clickable:hover { opacity: 0.85; }
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.detail-title .name { font-size: 18px; font-weight: 600; margin-right: 8px; }
.detail-title .emp-no { font-size: 14px; color: var(--el-text-color-regular); margin-right: 8px; }
.detail-actions { display: flex; gap: 8px; }
.detail-tabs { margin-top: 8px; }
.empty-hint { color: var(--el-text-color-secondary); font-size: 13px; padding: 20px 0; text-align: center; }
.log-hint { font-size: 12px; color: var(--el-text-color-secondary); background: #f8f9fa; padding: 10px 12px; border-radius: 4px; margin-bottom: 12px; line-height: 1.5; }
.expiry-hint { margin-top: 8px; color: var(--el-color-warning); font-size: 12px; display: flex; align-items: center; gap: 4px; }
.text-muted { color: var(--el-text-color-placeholder); font-size: 12px; }
.ml-8 { margin-left: 8px; }
.remark-display { margin-top: 12px; padding: 8px 12px; background: #f8f9fa; border-radius: 4px; font-size: 13px; line-height: 1.5; }
.remark-display label { font-weight: 600; color: var(--el-text-color-primary); }

/* 4.3 "新增证书" 附件显示 + 新人员高亮（保存成功后 3s） */
.attach-info { font-size: 12px; color: var(--el-text-color-regular); margin-top: 4px; }
.new-person-highlight {
  background-color: var(--el-color-primary-light-9) !important;
  transition: background-color 0.3s;
}
</style>
