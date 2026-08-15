import { describe, it, expect } from 'vitest'
import { defaultScoreTemplate, defaultScoreResults, defaultSuggestions } from './scoreParseDefaults.js'

describe('scoreParseDefaults.js', () => {
  it('exports 13 default scoring template items with valid weights', () => {
    expect(Array.isArray(defaultScoreTemplate)).toBe(true)
    expect(defaultScoreTemplate.length).toBe(13)

    const totalWeight = defaultScoreTemplate.reduce((acc, item) => acc + item.weight, 0)
    expect(totalWeight).toBe(100)
  })

  it('exports default score results matching template item codes', () => {
    expect(defaultScoreResults).toBeDefined()
    for (const item of defaultScoreTemplate) {
      expect(defaultScoreResults[item.code]).toBeDefined()
    }
  })

  it('exports suggestions for risk or subjective dimensions', () => {
    expect(defaultSuggestions.A1).toBeDefined()
    expect(defaultSuggestions.D2).toContain('CMMI')
  })
})
