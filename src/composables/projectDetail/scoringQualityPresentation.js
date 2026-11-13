// Input: additive fields from the actual-scoring results API
// Output: quality summary independent of task completion and satisfaction counts
// Pos: src/composables/projectDetail/ - Presentation domain helper
const QUALITY_LABELS = { COMPLETE: '评分依据已核验', NEEDS_REVIEW: '评分结果待复核', PARTIAL: '评分结果部分可用', UNKNOWN: '评分质量未知' }
const COVERAGE_LABELS = { COMPLETE: '原件处理完整', PARTIAL: '原件部分覆盖', UNKNOWN: '原件覆盖未知' }

export function scoringQualityPresentation(data = {}) {
  const qualityStatus = Object.hasOwn(QUALITY_LABELS, data.qualityStatus) ? data.qualityStatus : 'UNKNOWN'
  const coverageStatus = data.coverage?.coverageStatus || 'UNKNOWN'
  const counts = ['objectiveScored', 'objectivePending', 'subjectivePending', 'failed'].map(key => data[key])
  const diagnosticsKnown = data.diagnosticsKnown === true && counts.every(value => Number.isInteger(value) && value >= 0)
  const [objectiveScored, objectivePending, subjectivePending, failed] = counts
  const summary = diagnosticsKnown
    ? `客观已评分${objectiveScored}项、待确认${objectivePending}项、主观待评${subjectivePending}项、失败${failed}项`
    : '旧版或诊断未知，请核对逐项结果'
  return {
    qualityStatus, diagnosticsKnown,
    text: `${QUALITY_LABELS[qualityStatus]}；${summary}；${COVERAGE_LABELS[coverageStatus] || COVERAGE_LABELS.UNKNOWN}`,
    success: qualityStatus === 'COMPLETE' && coverageStatus === 'COMPLETE' && diagnosticsKnown && failed === 0 && objectivePending === 0,
  }
}
