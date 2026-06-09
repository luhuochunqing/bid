<template>
  <div class="personnel-container">
    <div class="page-header">
      <h2>人员库 — 投标团队成员管理</h2>
      <div class="header-actions">
        <div class="primary-actions">
          <el-button v-if="canAdd" type="primary" @click="openForm(null)">
            <el-icon><Plus /></el-icon> 新增人员
          </el-button>
        </div>
        <div class="batch-actions" v-if="canBatch">
          <el-button @click="handleDownloadTemplate">
            <el-icon><Download /></el-icon> 下载导入模板
          </el-button>
          <el-button v-if="canImportExport" @click="importDialogVisible = true">
            <el-icon><Upload /></el-icon> 批量导入
          </el-button>
          <el-button v-if="canImportExport" @click="exportDialogVisible = true">
            <el-icon><Download /></el-icon> 批量导出
          </el-button>
          <el-button @click="attachDialogVisible = true">
            <el-icon><Link /></el-icon> 批量关联附件
          </el-button>
        </div>
      </div>
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
            <el-table-column label="状态" width="90">
              <template #default="{row}">
                <el-tag :type="certStatusTagType(row.status)" size="small">{{ certStatusLabel(row.status) }}</el-tag>
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
            <el-icon><Warning /></el-icon> 该人员有 {{ current.expiringCertificatesCount }} 个证书即将到期（30 天内）
          </div>
        </el-tab-pane>

        <!-- Tab 4: 操作日志（4.3.1.3 真实 API 数据，4.3.1.8 对齐展示规则） -->
        <el-tab-pane label="操作日志" name="log">
          <el-table :data="operationLogs" stripe size="small" v-loading="operationLogsLoading">
            <el-table-column prop="createdAt" label="时间" width="180">
              <template #default="{row}">{{ row.createdAt ? row.createdAt.replace('T', ' ').slice(0, 19) : '-' }}</template>
            </el-table-column>
            <el-table-column label="操作人" width="180">
              <template #default="{row}">{{ row.operatorName || '-' }}</template>
            </el-table-column>
            <el-table-column prop="operationType" label="类型" width="120" />
            <el-table-column label="变更摘要" min-width="200">
              <template #default="{row}">
                <span v-if="row.changeDetails && row.changeDetails.length">
                  {{ row.changeDetails.map(d => `${d.field || ''}: ${d.oldValue || '-'} → ${d.newValue || '-'}`).join('; ') }}
                </span>
                <span v-else class="text-muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!operationLogsLoading && !operationLogs.length" class="empty-hint">暂无操作日志（新建人员或无变更时为空）</div>
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

    <!-- 批量导入对话框 -->
    <el-dialog
      v-model="importDialogVisible"
      title="批量导入人员"
      width="560px"
      :close-on-click-modal="false"
      @close="resetImportDialog"
    >
      <div v-if="!importTaskId">
        <el-upload
          drag
          :auto-upload="false"
          :limit="1"
          accept=".xlsx"
          :on-change="onImportFileChange"
          :before-upload="beforeImportUpload"
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将Excel文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .xlsx 格式，文件大小不能超过 10MB</div>
          </template>
        </el-upload>
        <div v-if="importFile" class="import-file-info">
          <el-icon><Document /></el-icon>
          <span>{{ importFile.name }}</span>
          <el-button type="danger" link size="small" @click="importFile = null">移除</el-button>
        </div>
      </div>

      <div v-else-if="importStatus === 'PROCESSING'" class="import-progress">
        <el-progress :percentage="importProgressPercent" :status="importProgressPercent === 100 ? 'success' : ''" />
        <p class="progress-text">{{ importProgressText }}</p>
      </div>

      <div v-else-if="importStatus === 'COMPLETED'" class="import-result">
        <el-result icon="success" title="导入完成">
          <template #sub-title>
            <div class="result-summary">
              <p>成功：{{ importSuccessCount }} 条</p>
              <p v-if="importFailCount > 0">失败：{{ importFailCount }} 条</p>
            </div>
          </template>
          <template #extra>
            <el-button v-if="importFailCount > 0" type="warning" @click="handleDownloadErrorReport">
              下载错误报告
            </el-button>
            <el-button type="primary" @click="importDialogVisible = false; loadData()">完成</el-button>
          </template>
        </el-result>
      </div>

      <div v-else-if="importStatus === 'FAILED'" class="import-result">
        <el-result icon="error" title="导入失败" :sub-title="importErrorMessage">
          <template #extra>
            <el-button type="primary" @click="resetImportDialog">重试</el-button>
          </template>
        </el-result>
      </div>

      <template #footer v-if="!importTaskId">
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!importFile" :loading="importing" @click="handleStartImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 批量导出对话框 -->
    <el-dialog
      v-model="exportDialogVisible"
      title="批量导出人员"
      width="560px"
      :close-on-click-modal="false"
      @close="resetExportDialog"
    >
      <div v-if="!exportTaskId">
        <el-form :model="exportFilters" label-width="100px">
          <el-form-item label="姓名/工号">
            <el-input v-model="exportFilters.keyword" placeholder="留空导出全部" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="exportFilters.status" placeholder="全部" clearable style="width:100%">
              <el-option label="在职" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
              <el-option label="离职" value="TERMINATED" />
            </el-select>
          </el-form-item>
          <el-form-item label="部门">
            <el-input v-model="exportFilters.departmentCode" placeholder="留空不限制" clearable />
          </el-form-item>
          <el-form-item label="持有证书">
            <el-input v-model="exportFilters.certificateKeyword" placeholder="证书名称关键词" clearable />
          </el-form-item>
        </el-form>
        <div class="export-hint">
          <el-icon><InfoFilled /></el-icon>
          导出文件包含 Excel（人员+教育+证书三Sheet）及证书附件ZIP
        </div>
      </div>

      <div v-else-if="exportStatus === 'PROCESSING'" class="export-progress">
        <el-progress :percentage="exportProgressPercent" :status="exportProgressPercent === 100 ? 'success' : ''" />
        <p class="progress-text">{{ exportProgressText }}</p>
      </div>

      <div v-else-if="exportStatus === 'COMPLETED'" class="export-result">
        <el-result icon="success" title="导出完成" :sub-title="`共导出 ${exportTotalCount} 条记录`">
          <template #extra>
            <el-button type="primary" @click="handleDownloadExportFile">下载导出文件</el-button>
            <el-button @click="exportDialogVisible = false">关闭</el-button>
          </template>
        </el-result>
      </div>

      <div v-else-if="exportStatus === 'FAILED'" class="export-result">
        <el-result icon="error" title="导出失败" :sub-title="exportErrorMessage">
          <template #extra>
            <el-button type="primary" @click="resetExportDialog">重试</el-button>
          </template>
        </el-result>
      </div>

      <template #footer v-if="!exportTaskId">
        <el-button @click="exportDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="exporting" @click="handleStartExport">开始导出</el-button>
      </template>
    </el-dialog>

    <!-- 批量关联附件对话框 -->
    <el-dialog
      v-model="attachDialogVisible"
      title="批量关联证书附件"
      width="640px"
      :close-on-click-modal="false"
      @close="resetAttachDialog"
    >
      <div v-if="!attachResults">
        <el-upload
          multiple
          :auto-upload="false"
          accept=".pdf,.jpg,.jpeg,.png"
          :on-change="onAttachFilesChange"
          :before-upload="beforeAttachUpload"
          :show-file-list="true"
          :file-list="attachFileList"
        >
          <el-button type="primary" plain>选择附件文件</el-button>
          <template #tip>
            <div class="el-upload__tip">
              文件命名规范：PER_姓名_工号_序号_证书名.扩展名<br/>
              示例：PER_张三_EMP001_01_一级建造师.pdf
            </div>
          </template>
        </el-upload>
      </div>

      <div v-else class="attach-results">
        <el-result
          :icon="attachResults.failedCount === 0 ? 'success' : 'warning'"
          :title="attachResults.failedCount === 0 ? '关联完成' : '关联完成（含未匹配文件）'"
          :sub-title="`成功关联 ${attachResults.successCount} 个文件，${attachResults.failedCount} 个未匹配`"
        >
          <template #extra>
            <div v-if="attachResults.unmatchedFiles && attachResults.unmatchedFiles.length > 0" class="unmatched-list">
              <div v-for="(f, i) in attachResults.unmatchedFiles" :key="i" class="unmatched-item">
                <el-icon><Warning /></el-icon>
                <span class="unmatched-name">{{ f.fileName }}</span>
                <span class="unmatched-reason">{{ f.reason }}</span>
              </div>
            </div>
            <el-button type="primary" @click="resetAttachDialog">继续上传</el-button>
          </template>
        </el-result>
      </div>

      <template #footer v-if="!attachResults">
        <el-button @click="attachDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="attachFileList.length === 0" :loading="attaching" @click="handleBatchAttach">
          开始关联 ({{ attachFileList.length }} 个文件)
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { useUserStore } from '@/stores/user.js'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Warning, CircleClose, Download, Upload, Link, UploadFilled, Document, InfoFilled } from '@element-plus/icons-vue'
import personnelApi from '@/api/modules/personnel.js'
import personnelBatchApi from '@/api/modules/personnelBatchApi.js'

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

// 批量操作状态
const importDialogVisible = ref(false)
const exportDialogVisible = ref(false)
const attachDialogVisible = ref(false)

// 权限控制（bid_admin / bid_lead 可导入导出，bid_specialist 只能导出）
const canImportExport = computed(() => {
  const r = userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || ''
  return ['bid_admin', 'bid_lead'].includes(r)
})
const canBatch = computed(() => {
  const r = userStore.userRole || (userStore.currentUser && userStore.currentUser.role) || ''
  return ['bid_admin', 'bid_lead', 'bid_specialist'].includes(r)
})

// 下载导入模板
const handleDownloadTemplate = async () => {
  try {
    await personnelBatchApi.downloadImportTemplate()
    ElMessage.success('模板下载成功')
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 导入对话框状态
const importFile = ref(null)
const importTaskId = ref(null)
const importStatus = ref('') // PROCESSING, COMPLETED, FAILED
const importProgressPercent = ref(0)
const importProgressText = ref('')
const importSuccessCount = ref(0)
const importFailCount = ref(0)
const importErrorMessage = ref('')
const importing = ref(false)
let importPollTimer = null

const resetImportDialog = () => {
  if (importPollTimer) { clearInterval(importPollTimer); importPollTimer = null }
  importFile.value = null
  importTaskId.value = null
  importStatus.value = ''
  importProgressPercent.value = 0
  importProgressText.value = ''
  importSuccessCount.value = 0
  importFailCount.value = 0
  importErrorMessage.value = ''
  importing.value = false
}

const onImportFileChange = (file) => { importFile.value = file.raw }

const beforeImportUpload = (file) => {
  const isXlsx = file.name.toLowerCase().endsWith('.xlsx')
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isXlsx) { ElMessage.error('仅支持 .xlsx 格式'); return false }
  if (!isLt10M) { ElMessage.error('文件大小不能超过 10MB'); return false }
  importFile.value = file
  return false // 阻止自动上传
}

const handleStartImport = async () => {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await personnelBatchApi.startImport(importFile.value)
    const task = res?.data || {}
    importTaskId.value = task.taskId
    importStatus.value = 'PROCESSING'
    importProgressPercent.value = 0
    importProgressText.value = '正在处理导入任务...'

    // 开始轮询进度
    importPollTimer = setInterval(async () => {
      try {
        const progress = await personnelBatchApi.getImportProgress(importTaskId.value)
        const info = progress?.data || {}
        importProgressPercent.value = info.progressPercent ?? 0
        importProgressText.value = info.progressText || '处理中...'
        
        if (info.status === 'COMPLETED') {
          clearInterval(importPollTimer)
          importPollTimer = null
          importStatus.value = 'COMPLETED'
          importSuccessCount.value = info.successCount ?? 0
          importFailCount.value = info.failCount ?? 0
        } else if (info.status === 'FAILED') {
          clearInterval(importPollTimer)
          importPollTimer = null
          importStatus.value = 'FAILED'
          importErrorMessage.value = info.errorMessage || '导入失败'
        }
      } catch (e) {
        // 轮询失败不中断
      }
    }, 2000)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导入任务创建失败')
  } finally {
    importing.value = false
  }
}

const handleDownloadErrorReport = async () => {
  try {
    await personnelBatchApi.downloadErrorReport(importTaskId.value)
    ElMessage.success('错误报告下载成功')
  } catch {
    ElMessage.error('错误报告下载失败')
  }
}

// 导出对话框状态
const exportFilters = reactive({ keyword: '', status: '', departmentCode: '', certificateKeyword: '' })
const exportTaskId = ref(null)
const exportStatus = ref('')
const exportProgressPercent = ref(0)
const exportProgressText = ref('')
const exportTotalCount = ref(0)
const exportErrorMessage = ref('')
const exporting = ref(false)
let exportPollTimer = null

const resetExportDialog = () => {
  if (exportPollTimer) { clearInterval(exportPollTimer); exportPollTimer = null }
  Object.assign(exportFilters, { keyword: '', status: '', departmentCode: '', certificateKeyword: '' })
  exportTaskId.value = null
  exportStatus.value = ''
  exportProgressPercent.value = 0
  exportProgressText.value = ''
  exportTotalCount.value = 0
  exportErrorMessage.value = ''
  exporting.value = false
}

const handleStartExport = async () => {
  exporting.value = true
  try {
    const res = await personnelBatchApi.startExport(exportFilters)
    const task = res?.data || {}
    exportTaskId.value = task.taskId
    exportStatus.value = 'PROCESSING'
    exportProgressPercent.value = 0
    exportProgressText.value = '正在处理导出任务...'

    exportPollTimer = setInterval(async () => {
      try {
        const progress = await personnelBatchApi.getExportProgress(exportTaskId.value)
        const info = progress?.data || {}
        exportProgressPercent.value = info.progressPercent ?? 0
        exportProgressText.value = info.progressText || '处理中...'
        
        if (info.status === 'COMPLETED') {
          clearInterval(exportPollTimer)
          exportPollTimer = null
          exportStatus.value = 'COMPLETED'
          exportTotalCount.value = info.totalCount ?? 0
        } else if (info.status === 'FAILED') {
          clearInterval(exportPollTimer)
          exportPollTimer = null
          exportStatus.value = 'FAILED'
          exportErrorMessage.value = info.errorMessage || '导出失败'
        }
      } catch (e) {}
    }, 2000)
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '导出任务创建失败')
  } finally {
    exporting.value = false
  }
}

const handleDownloadExportFile = async () => {
  try {
    await personnelBatchApi.downloadExportFile(exportTaskId.value)
    ElMessage.success('导出文件下载成功')
  } catch {
    ElMessage.error('导出文件下载失败')
  }
}

// 批量关联附件对话框状态
const attachFileList = ref([])
const attachResults = ref(null)
const attaching = ref(false)

const resetAttachDialog = () => {
  attachFileList.value = []
  attachResults.value = null
  attaching.value = false
}

const onAttachFilesChange = (file, fileList) => {
  attachFileList.value = fileList
}

const beforeAttachUpload = (file) => {
  const okType = ['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
  const okSize = file.size / 1024 / 1024 < 10
  if (!okType) { ElMessage.error('仅支持 PDF/JPG/PNG'); return false }
  if (!okSize) { ElMessage.error('附件不能超过10MB'); return false }
  return true
}

const handleBatchAttach = async () => {
  if (attachFileList.value.length === 0) return
  attaching.value = true
  try {
    const files = attachFileList.value.map(f => f.raw).filter(Boolean)
    const res = await personnelBatchApi.batchAttachAttachments(files)
    attachResults.value = res?.data || { successCount: 0, failedCount: 0, unmatchedFiles: [] }
    ElMessage.success(`关联完成：成功 ${attachResults.value.successCount} 个，未匹配 ${attachResults.value.failedCount} 个`)
    await loadData()
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || '批量关联失败')
  } finally {
    attaching.value = false
  }
}

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
// 4.3.1.3 操作日志（真实 API 数据）
const operationLogs = ref([])
const operationLogsLoading = ref(false)

const loadOperationLogs = async () => {
  if (!current.value?.id) return
  operationLogsLoading.value = true
  try {
    const res = await personnelApi.getOperationLogs(current.value.id)
    operationLogs.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    operationLogs.value = []
  } finally {
    operationLogsLoading.value = false
  }
}

// 证书状态标签映射（4.3.1.3 需求：有效/即将到期/已过期/永久有效）
const CERT_STATUS_LABELS = {
  VALID: '有效',
  EXPIRING: '即将到期',
  EXPIRED: '已过期',
  PERMANENT: '永久有效'
}
const CERT_STATUS_TAG_TYPES = {
  VALID: 'success',
  EXPIRING: 'warning',
  EXPIRED: 'danger',
  PERMANENT: 'primary'
}
const certStatusLabel = (status) => CERT_STATUS_LABELS[status] || status || '—'
const certStatusTagType = (status) => CERT_STATUS_TAG_TYPES[status] || 'info'

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
  // 切换到操作日志 Tab 时自动加载
  if (tab === 'log') {
    loadOperationLogs()
  }
}

// 监听 Tab 切换，切换到操作日志时加载数据
watch(detailActiveTab, (tab) => {
  if (tab === 'log') {
    loadOperationLogs()
  }
})
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
  if (form.value.phone && !/^\d{11}$/.test(form.value.phone)) { ElMessage.warning('请输入有效的手机号'); return }
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
.header-actions { display: flex; gap: 12px; align-items: center; }
.primary-actions, .batch-actions { display: flex; gap: 8px; }
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

.import-file-info { display: flex; align-items: center; gap: 8px; margin-top: 12px; padding: 8px 12px; background: var(--el-fill-color-light); border-radius: 4px; }
.import-progress { text-align: center; padding: 20px 0; }
.progress-text { margin-top: 12px; color: var(--el-text-color-secondary); font-size: 13px; }
.import-result { padding: 20px 0; }
.result-summary p { margin: 4px 0; font-size: 14px; }
.export-hint { margin-top: 16px; padding: 10px 12px; background: var(--el-color-primary-light-9); border-radius: 4px; font-size: 13px; color: var(--el-color-primary); display: flex; align-items: center; gap: 6px; }
.export-progress { text-align: center; padding: 20px 0; }
.export-result { padding: 20px 0; }
.attach-results { padding: 20px 0; }
.unmatched-list { text-align: left; margin-top: 16px; max-height: 300px; overflow-y: auto; }
.unmatched-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 4px; background: var(--el-color-danger-light-9); margin-bottom: 4px; font-size: 13px; }
.unmatched-name { font-weight: 500; color: var(--el-color-danger); }
.unmatched-reason { color: var(--el-text-color-secondary); margin-left: auto; }
</style>
