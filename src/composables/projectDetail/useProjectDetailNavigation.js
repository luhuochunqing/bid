export function useProjectDetailNavigation(context) {
  const { route, router } = context

  const goBack = () => router.push('/project')
  const handleEdit = () => router.push(`/document/editor/${route.params.id}`)

  return {
    goBack,
    handleEdit,
  }
}
