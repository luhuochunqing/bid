import echarts from './echarts.js'

// Smoke：按需注册模块可加载、注册不抛错、init API 可用
describe('utils/echarts 按需注册', () => {
  it('default export 提供 init API', () => {
    expect(typeof echarts.init).toBe('function')
  })

  it('重复加载注册清单不抛错（echarts.use 幂等）', async () => {
    await expect(import('./echarts.js')).resolves.toBeTruthy()
  })
})
