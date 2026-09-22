export function useProjectDetailNavigation(context) {
  const { route, router, project, assetCheckResult } = context

  const goBack = () => router.push('/project')
  const goToExpensePage = () => router.push('/resource/expense')
  const goToResultPage = () => router.push('/resource/bid-result')
  const goToAssetManagement = () => router.push('/resource/bar')
  const handleEdit = () => router.push(`/document/editor/${route.params.id}`)

  const goToSiteDetail = () => {
    if (assetCheckResult.value?.site?.id) {
      router.push(`/resource/bar/site/${assetCheckResult.value.site.id}`)
    }
  }

  const borrowUK = () => {
    if (assetCheckResult.value?.site?.id) {
      router.push({
        path: `/resource/bar/site/${assetCheckResult.value.site.id}`,
        query: {
          fromProjectId: String(route.params.id || ''),
          fromProjectName: project.value?.name || '',
        },
      })
    }
  }

  const viewSOP = () => {
    if (assetCheckResult.value?.site?.id) {
      router.push(`/resource/bar/sop/${assetCheckResult.value.site.id}`)
    }
  }

  return {
    goBack,
    goToExpensePage,
    goToResultPage,
    goToAssetManagement,
    handleEdit,
    goToSiteDetail,
    borrowUK,
    viewSOP,
  }
}
