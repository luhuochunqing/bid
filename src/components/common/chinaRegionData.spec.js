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
  it('CO-381: 选二级统一存 一级+二级 格式（含直辖市/港澳台）', () => {
    // 普通省
    expect(normalizeHeadquartersRegionPath(['广东省', '深圳市'])).toBe('广东省深圳市')
    expect(normalizeHeadquartersRegionPath(['江苏省', '淮安市'])).toBe('江苏省淮安市')
    // 直辖市
    expect(normalizeHeadquartersRegionPath(['北京市', '北京市'])).toBe('北京市北京市')
    expect(normalizeHeadquartersRegionPath(['上海市', '上海市'])).toBe('上海市上海市')
    // 台湾省
    expect(normalizeHeadquartersRegionPath(['台湾省', '台北市'])).toBe('台湾省台北市')
    // 港澳
    expect(normalizeHeadquartersRegionPath(['香港特别行政区', '中西区'])).toBe('香港特别行政区中西区')
    expect(normalizeHeadquartersRegionPath(['澳门特别行政区', '花地玛堂区'])).toBe('澳门特别行政区花地玛堂区')
  })

  it('CO-381: 选一级存本级行政区名', () => {
    expect(normalizeHeadquartersRegionPath(['北京市'])).toBe('北京市')
    expect(normalizeHeadquartersRegionPath(['广东省'])).toBe('广东省')
    expect(normalizeHeadquartersRegionPath(['香港特别行政区'])).toBe('香港特别行政区')
    expect(normalizeHeadquartersRegionPath(['澳门特别行政区'])).toBe('澳门特别行政区')
  })

  it('空路径返回空串', () => {
    expect(normalizeHeadquartersRegionPath([])).toBe('')
    expect(normalizeHeadquartersRegionPath(null)).toBe('')
  })
})

describe('isValidHeadquartersRegion', () => {
  it('CO-381: 统一 一级+二级 格式通过（含直辖市/港澳台）', () => {
    expect(isValidHeadquartersRegion('广东省深圳市')).toBe(true)
    expect(isValidHeadquartersRegion('北京市北京市')).toBe(true)
    expect(isValidHeadquartersRegion('上海市上海市')).toBe(true)
    expect(isValidHeadquartersRegion('台湾省台北市')).toBe(true)
    expect(isValidHeadquartersRegion('香港特别行政区中西区')).toBe(true)
    expect(isValidHeadquartersRegion('澳门特别行政区花地玛堂区')).toBe(true)
  })

  it('直辖市旧市-市格式仍兼容通过', () => {
    expect(isValidHeadquartersRegion('北京市-北京市')).toBe(true)
    expect(isValidHeadquartersRegion('重庆市-重庆市')).toBe(true)
  })

  it('直辖市/港澳台旧单名仍兼容通过', () => {
    expect(isValidHeadquartersRegion('北京市')).toBe(true)
    expect(isValidHeadquartersRegion('重庆市')).toBe(true)
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

  it('空值不通过', () => {
    expect(isValidHeadquartersRegion('')).toBe(false)
    expect(isValidHeadquartersRegion(null)).toBe(false)
  })
})

describe('regionValueToCascaderPath', () => {
  it('CO-381: 统一 一级+二级 格式回填为 [一级, 二级]（含直辖市/港澳台）', () => {
    expect(regionValueToCascaderPath('广东省深圳市')).toEqual(['广东省', '深圳市'])
    expect(regionValueToCascaderPath('北京市北京市')).toEqual(['北京市', '北京市'])
    expect(regionValueToCascaderPath('上海市上海市')).toEqual(['上海市', '上海市'])
    expect(regionValueToCascaderPath('台湾省台北市')).toEqual(['台湾省', '台北市'])
    expect(regionValueToCascaderPath('香港特别行政区中西区')).toEqual(['香港特别行政区', '中西区'])
    expect(regionValueToCascaderPath('澳门特别行政区花地玛堂区')).toEqual(['澳门特别行政区', '花地玛堂区'])
  })

  it('直辖市旧市-市格式回填为 [市名]（兼容历史数据）', () => {
    expect(regionValueToCascaderPath('北京市-北京市')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市-上海市')).toEqual(['上海市'])
  })

  it('直辖市/港澳台旧单名回填为 [本级行政区名]（兼容历史数据）', () => {
    expect(regionValueToCascaderPath('北京市')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市')).toEqual(['上海市'])
    expect(regionValueToCascaderPath('香港特别行政区')).toEqual(['香港特别行政区'])
    expect(regionValueToCascaderPath('台湾省')).toEqual(['台湾省'])
  })

  it('省+市+区历史值回退为 [省名, 市名]（cascader 仅两级）', () => {
    expect(regionValueToCascaderPath('广东省深圳市福田区')).toEqual(['广东省', '深圳市'])
  })

  it('直辖市带区历史值回退为 [市名]，避免回显空白', () => {
    expect(regionValueToCascaderPath('北京市东城区')).toEqual(['北京市'])
    expect(regionValueToCascaderPath('上海市浦东新区')).toEqual(['上海市'])
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
