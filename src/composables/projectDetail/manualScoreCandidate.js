// Human review can include unverified scoring facts; explicit scheme exclusions remain separate.
export function isManualScoreCandidate(fact, schemeId, schemeCount) {
  if (fact.manualExcluded) return false
  const manual = fact.candidateEligible === true || fact.scopeStatus !== 'OUT_OF_SCHEME'
  if (!manual) return false
  if (!schemeId) return schemeCount === 0
  return !fact.schemeId || fact.schemeId === schemeId
}
