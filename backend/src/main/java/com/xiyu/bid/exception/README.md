# Exception 模块 (异常处理包)

> 一旦我所属的文件夹有所变化，请更新我。

## 职责
异常层统一表达业务失败、资源不存在和参数错误，并配合全局异常处理输出标准 API 错误。异常类只负责语义，不承载业务流程。

## 边界清单
| 文件 | 地位 | 功能 |
|------|------|------|
| `BusinessException.java` | Exception | 业务异常基类 |
| `ResourceNotFoundException.java` | Exception | 资源未找到异常 |
