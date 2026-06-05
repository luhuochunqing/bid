import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import appEntry from '../main.js?raw'
import headerSource from '../components/layout/Header.vue?raw'
import loginSource from '../views/Login.vue?raw'
import projectListSource from '../views/Project/List.vue?raw'
import documentEditorSource from '../views/Document/Editor.vue?raw'

const currentFile = fileURLToPath(import.meta.url)
const currentDir = path.dirname(currentFile)
const readStyle = (fileName) => fs.readFileSync(path.join(currentDir, fileName), 'utf8')
const formControls = readStyle('form-controls.css')
const microInteractions = readStyle('micro-interactions.css')

describe('global form control styles', () => {
  it('loads the form control baseline after other global interaction styles', () => {
    const microIndex = appEntry.indexOf("import './styles/micro-interactions.css'")
    const formControlIndex = appEntry.indexOf("import './styles/form-controls.css'")

    expect(microIndex).toBeGreaterThan(-1)
    expect(formControlIndex).toBeGreaterThan(microIndex)
  })

  it('uses one compact size for normal text inputs and selects', () => {
    expect(formControls).toContain('--xiyu-control-height: 40px')
    expect(formControls).toContain('.el-input:not(.el-input--small) .el-input__wrapper')
    expect(formControls).toContain('.el-select:not(.el-select--small) .el-select__wrapper')
    expect(formControls).toContain('height: var(--xiyu-control-height) !important')
  })

  it('removes click and input focus decoration from form controls', () => {
    expect(formControls).toContain('.el-input:focus-within')
    expect(formControls).toContain('.el-select__wrapper.is-focused')
    expect(formControls).toContain('outline: none !important')
    expect(formControls).toContain('transition: none !important')
    expect(formControls).toContain('box-shadow: none !important')
  })

  it('keeps validation errors visible after removing focus decoration', () => {
    expect(formControls).toContain('.el-form-item.is-error .el-input__wrapper')
    expect(formControls).toContain('.el-form-item.is-error .el-select__wrapper')
    expect(formControls).toContain('border-color: var(--xiyu-control-error-border) !important')
  })

  it('keeps a minimal keyboard-only focus affordance', () => {
    expect(appEntry).toContain("import { installKeyboardNavMode } from './utils/keyboardNavMode.js'")
    expect(appEntry).toContain('installKeyboardNavMode()')
    expect(formControls).toContain('html[data-keyboard-nav="true"] .el-input.is-focus .el-input__wrapper')
    expect(formControls).toContain('html[data-keyboard-nav="true"] .el-input__wrapper:has(.el-input__inner:focus)')
    expect(formControls).toContain('border-color: var(--gray-300, #B0B0B0) !important')
  })


  it('provides subtle mouse-user focus feedback on form controls', () => {
    expect(formControls).toContain('html:not([data-keyboard-nav="true"]) .el-input.is-focus .el-input__wrapper')
    expect(formControls).toContain('html:not([data-keyboard-nav="true"]) .el-input__wrapper:has(.el-input__inner:focus)')
    expect(formControls).toContain('html:not([data-keyboard-nav="true"]) .el-select__wrapper.is-focused')
    expect(formControls).toContain('html:not([data-keyboard-nav="true"]) .el-textarea__inner:focus')
  })
  it('does not keep the old global input focus ring source', () => {
    expect(microInteractions).not.toContain('.el-input:focus-within {\n  box-shadow')
    expect(microInteractions).not.toContain('.el-select:focus-within .el-input__wrapper')
  })

  it('does not keep page-level blue input focus overrides', () => {
    for (const source of [headerSource, loginSource, projectListSource, documentEditorSource]) {
      expect(source).not.toContain('.el-input__wrapper.is-focus')
      expect(source).not.toContain('.el-select__wrapper.is-focus')
      expect(source).not.toContain('box-shadow: 0 0 0 3px rgba(3, 105, 161')
    }
  })
})
