import test from 'node:test'
import assert from 'node:assert/strict'
import { byTestId, tid } from './testid.js'

test('generates scoped bid-prefixed test ids', () => {
  assert.equal(tid('project-archive', 'table'), 'bid-project-archive-table')
  assert.equal(tid('project', 'create-btn'), 'bid-project-create-btn')
})

test('builds data-testid attribute selectors from generated ids', () => {
  const value = tid('project-archive', 'export-btn')
  assert.equal(byTestId(value), '[data-testid="bid-project-archive-export-btn"]')
})
