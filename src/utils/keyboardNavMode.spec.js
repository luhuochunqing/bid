import { afterEach, describe, expect, it } from 'vitest'
import { installKeyboardNavMode } from './keyboardNavMode.js'

afterEach(() => {
  delete document.documentElement.dataset.keyboardNav
})

describe('installKeyboardNavMode', () => {
  it('marks keyboard navigation after Tab and clears it after mouse input', () => {
    const cleanup = installKeyboardNavMode()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    expect(document.documentElement.dataset.keyboardNav).toBeUndefined()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.documentElement.dataset.keyboardNav).toBe('true')

    window.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    expect(document.documentElement.dataset.keyboardNav).toBeUndefined()

    cleanup()
  })

  it('clears keyboard navigation after pointer and touch input', () => {
    const cleanup = installKeyboardNavMode()

    try {
      for (const eventName of ['pointerdown', 'touchstart']) {
        window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
        expect(document.documentElement.dataset.keyboardNav).toBe('true')

        window.dispatchEvent(new Event(eventName, { bubbles: true }))
        expect(document.documentElement.dataset.keyboardNav).toBeUndefined()
      }
    } finally {
      cleanup()
    }
  })

  it('returns cleanup so route tests do not leak global listeners', () => {
    const cleanup = installKeyboardNavMode()
    cleanup()

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.documentElement.dataset.keyboardNav).toBeUndefined()
  })
})
