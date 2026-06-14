import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { projectsApi } from '@/api'
import { getAccessToken } from '@/api/session.js'

function buildAggregateOnlyDrillDown(stats) {
  return { projects: [], team: [], files: [], stats }
}

export function useChartDrillDown({ dashboardData }) {
  const dialogVisible = ref(false)
  const dialogTitle = ref('')
  const dialogData = ref({ projects: [], team: [], files: [], stats: null })
  const currentContext = ref(null)

  const previewFileDialogVisible = ref(false)
  const previewFileUrl = ref('')
  const previewFileName = ref('')

  async function loadTrendDrillDown(monthData) {
    try {
      const projectResult = await projectsApi.getList()
      const projects = projectResult?.success && Array.isArray(projectResult.data)
        ? projectResult.data.map((project) => ({
          id: project.id,
          name: project.name,
          customer: project.customer || '-',
          budget: project.budget || 0,
          status: project.status || '-',
          manager: project.manager || '-',
          result: project.status === 'won' ? 'won' : project.status === 'lost' ? 'lost' : null
        }))
        : []

      dialogData.value = {
        projects,
        team: [],
        files: [],
        stats: {
          totalParticipation: monthData.bids,
          wonCount: monthData.wins,
          teamWinRate: monthData.rate,
          totalAmount: monthData.amount
        }
      }
    } catch (error) {
      ElMessage.warning('真实项目明细加载失败，当前仅展示聚合统计')
      dialogData.value = buildAggregateOnlyDrillDown({
        totalParticipation: monthData.bids,
        wonCount: monthData.wins,
        teamWinRate: monthData.rate,
        totalAmount: monthData.amount
      })
    }
  }

  function loadCompetitorDrillDown(competitor) {
    dialogData.value = buildAggregateOnlyDrillDown({
      totalParticipation: competitor.bids,
      wonCount: Math.floor(competitor.bids * (competitor.share / 100)),
      teamWinRate: Math.floor(competitor.share),
      totalAmount: competitor.amount
    })
  }

  async function loadProductDrillDown(product) {
    try {
      const projectResult = await projectsApi.getList()
      const projects = projectResult?.success && Array.isArray(projectResult.data)
        ? projectResult.data.map((p) => ({
          id: p.id, name: p.name, customer: p.customer || '-', budget: p.budget || 0,
          status: p.status || '-', manager: p.manager || '-',
          result: p.status === 'won' ? 'won' : p.status === 'lost' ? 'lost' : null
        })) : []
      dialogData.value = { projects, team: [], files: [], stats: {
        totalParticipation: product.bids, wonCount: Math.floor(product.bids * (product.rate / 100)),
        teamWinRate: product.rate, totalAmount: product.revenue
      }}
    } catch {
      dialogData.value = buildAggregateOnlyDrillDown({
        totalParticipation: product.bids, wonCount: Math.floor(product.bids * (product.rate / 100)),
        teamWinRate: product.rate, totalAmount: product.revenue
      })
    }
  }

  function loadRegionDrillDown(region) {
    dialogData.value = buildAggregateOnlyDrillDown({
      totalParticipation: region.bids,
      wonCount: Math.floor(region.bids * (region.rate / 100)),
      teamWinRate: region.rate,
      totalAmount: region.amount
    })
  }

  async function openDialog(type, data) {
    currentContext.value = { type, data }

    switch (type) {
      case 'trend':
        dialogTitle.value = `${data.month} 投标数据详情`
        await loadTrendDrillDown(data)
        break
      case 'competitor':
        dialogTitle.value = `${data.name} 竞争分析详情`
        loadCompetitorDrillDown(data)
        break
      case 'product':
        dialogTitle.value = `${data.name} 产品线详情`
        loadProductDrillDown(data)
        break
      case 'region':
        dialogTitle.value = `${data.name} 区域详情`
        loadRegionDrillDown(data)
        break
    }

    dialogVisible.value = true
  }

  function handleTrendClick(params) {
    const data = dashboardData.value?.trendData?.[params.dataIndex]
    if (data) openDialog('trend', data)
  }

  function handleCompetitorClick(params) {
    const competitor = dashboardData.value?.competitors?.[params.dataIndex]
    if (competitor) openDialog('competitor', competitor)
  }

  function handleProductClick(params) {
    const product = dashboardData.value?.productLines?.[params.dataIndex]
    if (product) openDialog('product', product)
  }

  function handleRegionClick(params) {
    const region = dashboardData.value?.regionData?.[params.dataIndex]
    if (region) openDialog('region', region)
  }

  function previewFile(file) {
    previewFileName.value = file.name
    const ext = file.name.substring(file.name.lastIndexOf('.')).toLowerCase()
    const previewableExts = ['.pdf', '.jpg', '.jpeg', '.png', '.gif', '.txt', '.md']

    if (previewableExts.includes(ext)) {
      previewFileUrl.value = file.url || `/api/files/preview/${file.id}`
      previewFileDialogVisible.value = true
    } else if (['.docx', '.doc', '.xlsx', '.xls', '.pptx', '.ppt'].includes(ext)) {
      ElMessageBox.confirm(
        `${file.name} 暂不支持直接预览，是否下载查看？`,
        '文件预览',
        { confirmButtonText: '下载', cancelButtonText: '取消', type: 'info' }
      ).then(() => {
        downloadFile(file)
      }).catch(() => {})
    } else {
      ElMessage.warning(`文件类型 ${ext} 暂不支持预览，请下载后查看`)
    }
  }

  function downloadFile(file) {
    try {
      const downloadUrl = file.url || `/api/files/download/${file.id}`
      const token = getAccessToken()

      fetch(downloadUrl, {
        method: 'GET',
        credentials: 'include',
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      })
        .then((response) => {
          if (!response.ok) throw new Error('下载失败')
          return response.blob()
        })
        .then((blob) => {
          const url = URL.createObjectURL(blob)
          const link = document.createElement('a')
          link.href = url
          link.download = file.name
          document.body.appendChild(link)
          link.click()
          document.body.removeChild(link)
          URL.revokeObjectURL(url)
          ElMessage.success(`开始下载: ${file.name}`)
        })
        .catch((error) => {
          ElMessage.error(`文件下载失败: ${error.message}`)
        })
    } catch (error) {
      ElMessage.error(`下载失败: ${error.message}`)
    }
  }

  return {
    dialogVisible,
    dialogTitle,
    dialogData,
    currentContext,
    previewFileDialogVisible,
    previewFileUrl,
    previewFileName,
    handleTrendClick,
    handleCompetitorClick,
    handleProductClick,
    handleRegionClick,
    previewFile,
    downloadFile
  }
}
