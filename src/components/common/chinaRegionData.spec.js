// Input: chinaRegionData.js helpers
// Output: unit tests for headquarters region normalization/validation
// Pos: src/components/common/ - Helper test
import { describe, it, expect } from 'vitest'
import {
  normalizeHeadquartersRegionPath,
  isValidHeadquartersRegion,
  isMunicipalityName,
  regionValueToCascaderPath,
} from './chinaRegionData.js'

describe('isMunicipalityName', () => {
  it('识别 4 个直辖市', () => {
    expect(isMunicipalityName('北京市')).toBe(true)
    expect(isMunicipalityName('天津市')).toBe(true)
    expect(isMunicipalityName('上海市')).toBe(true)
    expect(isMunicipalityName('重庆市')).toBe(true)
  })

  it('非直辖市返回 false', () => {
    expect(isMunicipalityName('广东省')).toBe(false)
    expect(isMunicipalityName('深圳市')).toBe(false)
    expect(isMunicipalityName('')).toBe(false)
  })
})

describe('normalizeHeadquartersRegionPath', () => {
  it('直辖市仅存市名，丢弃区', () => {
    expect(normalizeHeadquartersRegionPath(['北京市'])).toBe('北京市')
    expect(normalizeHeadquartersRegionPath(['北京市', '东城区'])).toBe('北京市')
    expect(normalizeHeadquartersRegionPath(['上海市', '浦东新区'])).toBe('上海市')
  })

  it('港澳台仅存本级行政区名，丢弃区', () => {
    expect(normalizeHeadquartersRegionPath(['香港特别行政区', '中西区'])).toBe('香港特别行政区')
    expect(normalizeHeadquartersRegionPath(['澳门特别行政区'])).toBe('澳门特别行政区')
    expect(normalizeHeadquartersRegionPath(['台湾省', '台北市'])).toBe('台湾省')
  })

  it('普通省存 省+市', () => {
    expect(normalizeHeadquartersRegionPath(['广东省', '深圳市'])).toBe('广东省深圳市')
    expect(normalizeHeadquartersRegionPath(['江苏省', '淮安市'])).toBe('江苏省淮安市')
  })

  it('空路径返回空串', () => {
    expect(normalizeHeadquartersRegionPath([])).toBe('')
    expect(normalizeHeadquartersRegionPath(null)).toBe('')
  })
})

describe('isValidHeadquartersRegion', () => {
  it('直辖市市-市格式通过', () => {
    expect(isValidHeadquartersRegion('北京市-北京市')).toBe(true)
    expect(isValidHeadquartersRegion('重庆市-重庆市')).toBe(true)
  })

  it('直辖市旧单名仍兼容通过', () => {
    expect(isValidHeadquartersRegion('北京市')).toBe(true)
    expect(isValidHeadquartersRegion('重庆市')).toBe(true)
  })

  it('港澳台单名通过', () => {
    expect(isValidHeadquartersRegion('香港特别行政区')).toBe(true)
    expect(isValidHeadquartersRegion('澳门特别行政区')).toBe(true)
    expect(isValidHeadquartersRegion('台湾省')).toBe(true)
  })

  it('省+市 组合通过', () => {
    expect(isValidHeadquartersRegion('广东省深圳市')).toBe(true)
    expect(isValidHeadquartersRegion('河北省石家庄市')).toBe(true)
  })

  it('直辖市带区不通过', () => {
    expect(isValidHeadquartersRegion('北京市东城区')).toBe(false)
  })

  it('普通省仅省级不通过', () => {
    expect(isValidHeadquartersRegion('广东省')).toBe(false)
  })

  it('港澳台带区不通过', () => {
    expect(isValidHeadquartersRegion('香港特别行政区中西区')).toBe(false)
  })

  it('空值不通过', () => {
    expect(isValidHeadquartersRegion('')).toBe(false)
    expect(isValidHeadquartersRegion(null)).toBe(false)
  })
})

describe('regionValueToCascaderPath', () => {
  it('直辖市市-市格式回填为 [市名]', () => {
    expect(regionValueToCascaderPath('北京市-北京市')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市-上海市')).toEqual(['上海市'])
  })

  it('直辖市旧单名回填为 [市名]', () => {
    expect(regionValueToCascaderPath('北京市')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市')).toEqual(['上海市'])
  })

  it('省+市 回填为 [省名, 市名]', () => {
    expect(regionValueToCascaderPath('广东省深圳市')).toEqual(['广东省', '深圳市'])
  })

  it('省+市+区历史值回退为 [省名, 市名]（cascader 仅两级）', () => {
    expect(regionValueToCascaderPath('广东省深圳市福田区')).toEqual(['广东省', '深圳市'])
  })

  it('直辖市带区历史值回退为 [市名]，避免回显空白', () => {
    expect(regionValueToCascaderPath('北京市东城区')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市浦东新区')).toEqual(['上海市'])
  })

  it('港澳台带区历史值回退为本级行政区名', () => {
    expect(regionValueToCascaderPath('香港特别行政区中西区')).toEqual(['香港特别行政区'])
  })

  it('纯省名历史值补全为本级行政区名，避免回显空白', () => {
    expect(regionValueToCascaderPath('北京')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('广东')).toEqual(['广东省'])
    expect(regionValueToCascaderPath('内蒙古')).toEqual(['内蒙古自治区'])
  })

  it('空值返回 null', () => {
    expect(regionValueToCascaderPath('')).toBeNull()
    expect(regionValueToCascaderPath(null)).toBeNull()
  })

  it('无法匹配时原样返回字符串', () => {
    expect(regionValueToCascaderPath('未知地区')).toBe('未知地区')
  })
})
