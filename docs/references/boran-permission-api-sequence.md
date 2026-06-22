# 泊冉系统获取用户权限接口顺序

> 来源：泊冉系统获取用户权限接口顺序.xlsx
> 导入时间：2026-06-22

## 接口调用顺序

| 接口调用顺序 | 2和3的接口不区分先后顺序 |
| --- | --- |
| 1 | 登录鉴权接口 |
| 2 | 根据token获取员工信息 |
| 3 | 获取当前用户拥有的菜单树 |
| 4 | 获取当前用户拥有的角色 |
| 5 | 登出接口 |

## 接口详情

### 1. 登录鉴权接口

| 项目 | 值 |
| --- | --- |
| 接口名称 | 登录鉴权接口 |
| YApi 地址 | https://yapi.ehsy.com/project/406/interface/api/23352 |
| 接口路径 | `/oauth/login` |
| 说明 | 调用接口前，获取token信息，缓存到本地，设置默认缓存有效期 |

### 2. 根据token获取员工信息

| 项目 | 值 |
| --- | --- |
| 接口名称 | 根据token获取员工信息 |
| YApi 地址 | https://yapi.ehsy.com/project/406/interface/api/23358 |
| 接口路径 | `/oauth/getUserInfo` |
| 说明 | 根据token获取员工的用户信息 |

### 3. 获取当前用户拥有的菜单树

| 项目 | 值 |
| --- | --- |
| 接口名称 | 获取当前用户拥有的菜单树 |
| YApi 地址 | https://yapi.ehsy.com/project/406/interface/api/23484 |
| 接口路径 | `/oauth/getUserPermission` |
| 说明 | 根据token获取用户系统权限接口 |

### 4. 获取当前用户拥有的角色

| 项目 | 值 |
| --- | --- |
| 接口名称 | 获取当前用户拥有的角色 |
| YApi 地址 | https://yapi.ehsy.com/project/406/interface/api/26325 |
| 接口路径 | `/oss/admin-web/v1/output/data/getUserJobListByJobNumberList` |
| 说明 | 根据工号列表查询用户角色 |

### 5. 登出接口

| 项目 | 值 |
| --- | --- |
| 接口名称 | 登出接口 |
| YApi 地址 | https://yapi.ehsy.com/project/406/interface/api/23370 |
| 接口路径 | `/oauth/logout` |
| 说明 | 用于用户登出 |

## 缓存策略

当系统菜单权限或者用户角色在登录那一刻写入缓存，需要退出登录删除缓存，重新登录获取最新权限数据。
