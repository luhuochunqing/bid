<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="1000px"
    class="drill-down-dialog"
    destroy-on-close
  >
    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane name="projects">
        <template #label>
          <span class="tab-label">
            <el-icon><Document /></el-icon>
            相关项目
            <el-badge v-if="data.projects.length > 0" :value="data.projects.length" class="tab-badge" />
          </span>
        </template>
        <el-table
          :data="data.projects"
          size="small"
          stripe
          :empty-text="data.projects.length === 0 ? '暂无相关项目' : ''"
        >
          <el-table-column prop="name" label="项目名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="customer" label="客户" width="120" />
          <el-table-column prop="budget" label="预算(万元)" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="manager" label="负责人" width="80" />
          <el-table-column prop="result" label="结果" width="80">
            <template #default="{ row }">
              <span v-if="row.result" :style="{ color: row.result === 'won' ? '#67c23a' : '#f56c6c' }">
                {{ row.result === 'won' ? '中标' : '未中标' }}
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="$emit('go-to-project', row.id)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane name="team">
        <template #label>
          <span class="tab-label">
            <el-icon><User /></el-icon>
            团队信息
            <el-badge v-if="data.team.length > 0" :value="data.team.length" class="tab-badge" />
          </span>
        </template>
        <el-row :gutter="20">
          <el-col :span="data.stats ? 14 : 24">
            <div class="team-section">
              <h4 class="section-title">参与人员</h4>
              <el-table
                :data="data.team"
                size="small"
                border
                :empty-text="data.team.length === 0 ? '暂无团队数据' : ''"
              >
                <el-table-column prop="name" label="姓名" width="100" />
                <el-table-column prop="role" label="角色" width="120" />
                <el-table-column prop="dept" label="部门" width="120" />
                <el-table-column prop="participation" label="参与次数" width="100" align="center" />
                <el-table-column label="中标率" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag
                      :type="row.winRate >= 40 ? 'success' : row.winRate >= 30 ? 'warning' : 'danger'"
                      size="small"
                    >
                      {{ row.winRate }}%
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-col>
          <el-col v-if="data.stats" :span="10">
            <div class="team-section">
              <h4 class="section-title">绩效统计</h4>
              <div class="team-stats">
                <div class="stat-item">
                  <div class="stat-label">总参与次数</div>
                  <div class="stat-value">{{ data.stats.totalParticipation }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">中标次数</div>
                  <div class="stat-value stat-success">{{ data.stats.wonCount }}</div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">团队中标率</div>
                  <div
                    class="stat-value"
                    :class="data.stats.teamWinRate >= 35 ? 'stat-success' : 'stat-warning'"
                  >
                    {{ data.stats.teamWinRate }}%
                  </div>
                </div>
                <div class="stat-item">
                  <div class="stat-label">累计金额(万)</div>
                  <div class="stat-value stat-primary">{{ data.stats.totalAmount }}</div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </el-tab-pane>

      <el-tab-pane name="files">
        <template #label>
          <span class="tab-label">
            <el-icon><FolderOpened /></el-icon>
            相关文件
            <el-badge v-if="data.files.length > 0" :value="data.files.length" class="tab-badge" />
          </span>
        </template>
        <el-table
          :data="data.files"
          size="small"
          stripe
          :empty-text="data.files.length === 0 ? '暂无文件' : ''"
        >
          <el-table-column prop="name" label="文件名" min-width="200" show-overflow-tooltip />
          <el-table-column prop="project" label="所属项目" width="150" show-overflow-tooltip />
          <el-table-column prop="type" label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="getFileTypeColor(row.name)">
                {{ getFileType(row.name) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="uploader" label="上传者" width="80" />
          <el-table-column prop="uploadTime" label="上传时间" width="140" />
          <el-table-column prop="size" label="大小" width="80" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="$emit('preview-file', row)">
                预览
              </el-button>
              <el-button link type="success" size="small" @click="$emit('download-file', row)">
                下载
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :icon="Download" @click="$emit('export')">导出数据</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { Download, Document, FolderOpened, User } from '@element-plus/icons-vue'
import {
  getStatusType,
  getStatusText,
  getFileType,
  getFileTypeColor
} from '../utils/dashboardFormatters.js'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  data: {
    type: Object,
    default: () => ({ projects: [], team: [], files: [], stats: null })
  }
})

const emit = defineEmits([
  'update:modelValue',
  'go-to-project',
  'preview-file',
  'download-file',
  'export'
])

const visible = ref(props.modelValue)
const activeTab = ref('projects')

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) activeTab.value = 'projects'
})

watch(visible, (val) => {
  if (val !== props.modelValue) emit('update:modelValue', val)
})
</script>

<style scoped>
.drill-down-dialog :deep(.el-dialog__body) { padding: 0; }

.drill-down-dialog :deep(.el-tabs--border-card) {
  border: none;
  box-shadow: none;
}

.drill-down-dialog :deep(.el-tabs__content) { padding: 20px; }

.tab-label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tab-badge { margin-left: 4px; }

.team-section { padding: 10px 0; }

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
  margin: 0 0 16px 0;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.team-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  background: var(--bg-subtle);
  border-radius: 8px;
  padding: 20px;
}

.stat-item { text-align: center; }

.stat-label { font-size: 13px; color: var(--text-muted); margin-bottom: 8px; }
.stat-value { font-size: 24px; font-weight: 600; color: var(--gray-750); }
.stat-value.stat-success { color: #67c23a; }
.stat-value.stat-warning { color: #e6a23c; }
.stat-value.stat-primary { color: #409eff; }

@media (max-width: 768px) {
  .drill-down-dialog :deep(.el-dialog) { width: 95% !important; }
  .drill-down-dialog :deep(.el-tabs__content) { padding: 12px; }
  .team-stats { grid-template-columns: 1fr; padding: 12px; }
  .stat-value { font-size: 20px; }
}
</style>
