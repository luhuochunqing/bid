// Input: source files under src/ and e2e/
// Output: ESLint gate focused on symbol-level bugs (undefined refs, bad imports)
// Pos: project root - frontend static-analysis gate paired with check:line-budgets
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Scope note: ESLint was introduced after the codebase grew. To let CI pass
// from day one, historical violations (prop mutation, unused vars, bare
// console) are kept at `warn`. Only the bug classes that produce live
// runtime errors -- undefined references, unresolved imports, named-import
// typos -- are `error` and will block CI. A separate backlog PR should
// escalate `vue/no-mutating-props` back to `error` once it's cleaned up.
import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import importPlugin from 'eslint-plugin-import'
import vueParser from 'vue-eslint-parser'
import globals from 'globals'

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'backend/**',
      '.wiki/**',
      'coverage/**',
      'playwright-report/**',
      'test-results/**',
      'src/**/*.ts',
      'k6-tests/**',
      'wechat-miniprogram/**',
      'scripts/**',
      '*.config.js',
      '*.config.mjs'
    ]
  },

  js.configs.recommended,

  // JS/MJS files
  {
    files: ['**/*.js', '**/*.mjs'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021
      }
    },
    plugins: {
      import: importPlugin
    },
    settings: {
      'import/resolver': {
        alias: {
          map: [['@', './src']],
          extensions: ['.js', '.mjs', '.ts', '.vue', '.json']
        }
      }
    },
    rules: {
      // Error: bug classes that fail at runtime
      'no-undef': 'error',
      'import/no-unresolved': ['error', {
        ignore: ['^virtual:', '^~', '^vitest', '^node:', '\\?raw$', '\\?url$']
      }],
      'import/named': 'error',
      'no-debugger': 'error',
      // Error: catch const/let TDZ bugs that crash <script setup> at runtime
      'no-use-before-define': ['error', {
        functions: false,
        classes: true,
        variables: true
      }],
      // Warn: historical violations tracked for cleanup
      'no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrors: 'none'
      }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-constant-condition': 'warn',
      'no-empty': 'warn',
      // Catch placeholder TODOs that become forgotten code
      'no-warning-comments': ['warn', {
        terms: ['TODO: not implemented', 'TODO: API', 'FIXME: placeholder', 'HACK:'],
        location: 'anywhere',
      }],
    }
  },

  // Vue SFC files
  ...vue.configs['flat/essential'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module'
      },
      globals: {
        ...globals.browser,
        ...globals.es2021,
        defineProps: 'readonly',
        defineEmits: 'readonly',
        defineExpose: 'readonly',
        defineOptions: 'readonly',
        defineSlots: 'readonly',
        withDefaults: 'readonly'
      }
    },
    plugins: {
      import: importPlugin
    },
    settings: {
      'import/resolver': {
        node: {
          extensions: ['.js', '.jsx', '.mjs', '.ts', '.tsx', '.vue', '.json']
        },
        alias: {
          map: [['@', './src']],
          extensions: ['.js', '.mjs', '.ts', '.vue', '.json']
        }
      }
    },
    rules: {
      // Error: bug classes that fail at runtime
      'no-undef': 'error',
      'import/no-unresolved': ['error', {
        ignore: ['^virtual:', '^~', '^node:', '\\?raw$', '\\?url$', '^@/composables/']
      }],
      'import/named': 'error',
      'no-debugger': 'error',
      // Error: catch const/let TDZ bugs that crash <script setup> at runtime
      'no-use-before-define': ['error', {
        functions: false,
        classes: true,
        variables: true
      }],
      // Warn: historical violations tracked for cleanup
      'no-unused-vars': ['warn', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrors: 'none'
      }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-constant-condition': 'warn',
      'no-empty': 'warn',
      // Catch placeholder TODOs that become forgotten code
      'no-warning-comments': ['warn', {
        terms: ['TODO: not implemented', 'TODO: API', 'FIXME: placeholder', 'HACK:'],
        location: 'anywhere',
      }],
      'vue/multi-word-component-names': 'off',
      // Warn: backlog — 183 existing violations to be fixed in a follow-up PR
      'vue/no-mutating-props': 'warn',
      'vue/no-unused-vars': 'warn'
    }
  },

  // Test files: relax no-unused-vars and allow vitest globals
  {
    files: ['**/*.spec.js', '**/*.test.js', 'e2e/**/*.js'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        ...globals.es2021,
        describe: 'readonly',
        it: 'readonly',
        test: 'readonly',
        expect: 'readonly',
        beforeAll: 'readonly',
        beforeEach: 'readonly',
        afterAll: 'readonly',
        afterEach: 'readonly',
        vi: 'readonly'
      }
    }
  }
]
