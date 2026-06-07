// Scoped test-id and locator helpers for E2E tests
// Usage:
//   import { tid, byTestId } from '../utils/testid.js'
//   page.locator(byTestId('project-list', 'row-0'))
//   page.getByTestId(tid('project', 'create-btn'))

const TESTID_PREFIX = 'bid'

/** Generate a scoped data-testid attribute value */
export function tid(page, element) {
  return TESTID_PREFIX + '-' + page + '-' + element
}

/** Return a CSS selector string for the given test ID */
export function byTestId(value) {
  return '[data-testid="' + value + '"]'
}
