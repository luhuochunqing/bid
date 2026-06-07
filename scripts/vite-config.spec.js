/* @vitest-environment node */
import { describe, expect, it } from 'vitest'
import config from '../vite.config'

const manualChunks = config.build.rollupOptions.output.manualChunks

describe('vite manual chunks', () => {
  it('keeps Vue runtime ecosystem packages in the same chunk', () => {
    expect(manualChunks('/repo/node_modules/vue/dist/vue.runtime.esm-bundler.js')).toBe('vue-vendor')
    expect(manualChunks('/repo/node_modules/vue-router/dist/vue-router.mjs')).toBe('vue-vendor')
    expect(manualChunks('/repo/node_modules/pinia/dist/pinia.mjs')).toBe('vue-vendor')
    expect(manualChunks('/repo/node_modules/vuedraggable/dist/vuedraggable.umd.js')).toBe('vue-vendor')
  })
})
