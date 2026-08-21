# 构建工具陷阱与调试经验

记录构建工具（Maven、git-commit-id-plugin、worktree 等）的陷阱和调试方法。

---

## 5. Docker 层缓存应急重建：只改 COPY 行等靠后指令时 apt/pip 层全命中

### 场景

2026-08-21 测试环境 sidecar 容器崩溃循环事故中，服务器侧需紧急修复 Dockerfile 并重建镜像。完整构建（apt-get install + pip install）约 8 分钟，事故处置等不起。

### 技巧

Docker 构建是逐层缓存的：**只有某条指令及其输入发生变化时，该层及之后的所有层才会重建**。如果只修改 `COPY` 行这类靠后指令（不改 requirements.txt、不换基础镜像），之前的 apt/pip 重型层全部命中缓存：

```
FROM python:3.x          ← 缓存命中
RUN apt-get install ...  ← 缓存命中（数秒）
RUN pip install -r reqs  ← 缓存命中（数秒）
COPY app.py converter.py image_extractor.py ./   ← 唯一变化的层
CMD [...]                ← 重建
```

实测：8 分钟完整构建降为数秒完成。

### 边界

| 改动内容 | 缓存行为 |
|---------|---------|
| 只改 COPY 行 / CMD / ENV 靠后指令 | 全部前置层命中，秒级 |
| 改 requirements.txt | pip 层失效，重新安装全部依赖 |
| 换基础镜像 tag | 全部失效 |

### 经验教训

1. **应急重建前先判断改动落在哪一层**——改动越靠后，缓存收益越大
2. **不要为了"保险"而 `docker build --no-cache`**——无谓放弃全部缓存，事故场景下浪费时间
3. 该技巧只适用于应急修复；正式修复仍应走仓库 PR 流程保持代码与服务器一致

### 相关文档

- [root-cause-analysis-sidecar-mammoth-oom.md](file:///Users/user/xiyu/worktrees/trae/docs/lessons/root-cause-analysis-sidecar-mammoth-oom.md) — 同一 sidecar 的历史 OOM 根因分析
- `docs/release/deploy-report-2026-08-21-140th-test.md` — 本次事故完整时间线

---

## 4. Dockerfile COPY 漏文件导致容器崩溃循环（潜伏缺陷被镜像重建暴露）

### 问题

2026-08-21 第 140 次测试环境部署，sidecar 容器替换后立即进入崩溃循环：

```
ModuleNotFoundError: No module named 'image_extractor'
  File "/app/app.py", line 29, in <module>
    from image_extractor import extract_images
```

`docker ps` 显示容器状态 `Restarting (1)`，健康探测返回空。

### 根因

`app.py` 此前新增了 `from image_extractor import extract_images`（图片提取功能），但 `document-converter-sidecar/Dockerfile` 与 `Dockerfile.cn` 的 COPY 行均未同步：

```dockerfile
# ❌ 缺陷：漏掉 image_extractor.py
COPY app.py converter.py ./

# ✅ 修复
COPY app.py converter.py image_extractor.py ./
```

容器内 `image_extractor.py` 不存在 → app.py 启动即崩。

**为什么以前没炸**：线上旧镜像构建于 image_extractor 功能合入之前，当时的 app.py 没有这个 import，所以旧镜像一直正常运行。本次部署触发镜像重建，潜伏缺陷才第一次被执行路径命中。

### 潜伏缺陷暴露模式（核心教训）

> **旧镜像能跑 ≠ Dockerfile 正确。**

Dockerfile 是"声明式构建配方"，但它的正确性只在**下一次构建**时被验证。只要满足以下条件，缺陷可以潜伏任意久：

1. 源码新增了模块/import，但 Dockerfile 未同步
2. 期间没有触发过镜像全量重建（复用旧镜像）
3. 一旦重建 → 立刻在生产/测试环境爆炸

同类风险点：`.dockerignore` 误排除新目录、requirements.txt 漏新依赖、entrypoint 引用未复制的脚本。

### 正确做法

1. **改 app 入口 import 时，同步检查 Dockerfile COPY 清单**——把"Dockerfile 是否覆盖新文件"纳入 code review checklist
2. **新容器替换后立即验证，不能只看 `Up`**：
   ```bash
   docker ps --filter name=<container>           # 看 Up 还是 Restarting
   curl -s http://127.0.0.1:<port>/health        # 应用级健康
   docker logs <container> --tail 20             # 启动日志有无 traceback
   ```
3. **应急修复路径**：服务器侧 sed 改 COPY 行 → 重建（见 §5 层缓存技巧）→ 替换容器 → 健康确认；仓库侧同修并走 PR 消除漂移

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| COPY 清单与源码脱节 | Dockerfile 缺陷只在重建时暴露 | 改入口 import 必查 Dockerfile |
| 旧镜像掩盖缺陷 | 能运行的是旧镜像，不是当前 Dockerfile | 定期用当前 Dockerfile 做验证性重建 |
| 容器 `Up 1 second` 被误判成功 | 崩溃循环初期也显示 Up | 替换后必须 health + logs 双确认 |

### 相关文档

- `docs/release/deploy-report-2026-08-21-140th-test.md` — 本次事故完整时间线与 Smoke 结果
- [root-cause-analysis-sidecar-mammoth-oom.md](file:///Users/user/xiyu/worktrees/trae/docs/lessons/root-cause-analysis-sidecar-mammoth-oom.md) — 同一 sidecar 的历史 OOM 根因分析

---

## 3. Maven target 目录残留旧 Flyway 迁移文件导致打包后版本冲突

### 问题

2026-06-25 部署 `b122e9f4-api8080` 时，后端启动失败，Flyway 报错发现两个 V1096 版本的迁移脚本：
- `V1096__add_users_employee_number_pinyin.sql`（新版本，正确）
- `V1096__add_users_full_name_pinyin.sql`（旧版本，已被 V1097 替代，不应存在）

```
FlywayException: Found more than one migration with version 1096
```

但 `src/main/resources/db/migration-mysql/` 目录下只有一个 V1096 文件。

### 根因

`mvn package` 使用增量编译，`target/classes/db/migration-mysql/` 目录中残留了**之前构建时复制的旧迁移文件**。即使源码目录已经删除或重命名了旧文件，`target/` 目录中的旧文件不会被自动清理，最终被打进 jar 包。

```
源码目录（正确）：
  V1095__add_users_employee_number.sql
  V1096__add_users_employee_number_pinyin.sql  ← 新版本
  V1097__drop_users_full_name_pinyin.sql

target/classes 目录（错误，残留旧文件）：
  V1095__add_users_employee_number.sql
  V1096__add_users_employee_number_pinyin.sql  ← 新复制的
  V1096__add_users_full_name_pinyin.sql        ← 上一次构建残留的！
  V1097__drop_users_full_name_pinyin.sql
```

Flyway 按版本号去重，检测到两个 V1096 直接启动失败。

### 危害

1. **部署直接失败**：Flyway 检测到重复版本后抛异常，应用无法启动
2. **需要回滚**：服务中断，必须恢复上一版本的 jar 和前端资源
3. **排查耗时**：第一反应是怀疑源码有问题，但源码是干净的，容易误导排查方向

### 正确做法

**发布打包必须用 `mvn clean package`，不能省略 `clean`**。

```bash
# ✅ 正确：先 clean 再打包，确保 target 目录干净
mvn clean package -DskipTests

# ❌ 错误：增量打包，target 中可能残留旧文件
mvn package -DskipTests
```

### 发布前验证

打包后、部署前，必须验证 jar 包中 Flyway 迁移脚本无重复版本：

```bash
# 检查 jar 中所有 Flyway 迁移版本是否有重复
jar tf target/bid-poc-1.0.3.jar \
  | grep "migration-mysql/V" \
  | sed 's|BOOT-INF/classes/db/migration-mysql/V||' \
  | sed 's|__.*||' \
  | sort \
  | uniq -d

# 期望输出：空（无重复版本）
# 如果有输出，说明存在重复版本号，不能部署
```

快速检查某个版本范围：

```bash
# 只看 V109x 版本
jar tf target/bid-poc-1.0.3.jar | grep "migration-mysql/V109" | sort
```

### 排查步骤

遇到 Flyway 版本冲突时，按以下顺序排查：

```bash
# 1. 先看源码目录是否有重复（最不可能，但先排除）
ls backend/src/main/resources/db/migration-mysql/ | grep V1096

# 2. 再看 target/classes 目录（大概率在这里）
ls backend/target/classes/db/migration-mysql/ | grep V1096

# 3. 最后看 jar 包内
jar tf backend/target/bid-poc-1.0.3.jar | grep V1096

# 如果 target 中有重复，clean 后重新打包
cd backend && mvn clean package -DskipTests
```

### 经验教训

| 问题 | 教训 | 规范 |
|------|------|------|
| `mvn package` 不清理 target 旧文件 | 增量构建会保留已删除/重命名的资源文件 | **发布打包必须 `clean`**，开发调试可以增量 |
| Flyway 版本冲突导致启动失败 | 迁移脚本是数据库结构的唯一真相，不能有歧义 | 部署前必须验证 jar 中迁移版本无重复 |
| 只看源码目录容易误判 | 源码干净 ≠ 产物干净 | 以 jar 包实际内容为准，不以源码为准 |

### 相关文档

- `docs/release/LIVE_SERVER_DEPLOYMENT_RUNBOOK.md` — 部署运行手册
- `backend/src/main/resources/db/migration-mysql/` — Flyway 迁移脚本目录
- `scripts/release/package-release.sh` — 发布打包脚本

---

## 2. Maven `-DskipTests` 只跳过测试运行，不跳过测试编译

### 问题

执行 `mvn -DskipTests package` 时，以为测试代码有问题也能打包成功，结果测试代码编译错误仍然导致构建失败。

```
# 以为加了 -DskipTests 就能跳过测试相关的一切
mvn -DskipTests package
# 结果：测试代码编译错误照样构建失败
[ERROR] Failed to execute goal maven-compiler-plugin:testCompile
[ERROR] Compilation failure
```

### 根因

Maven 的 `-DskipTests` 参数**只跳过 `surefire:test` 阶段（测试运行）**，不跳过 `compiler:testCompile` 阶段（测试代码编译）。

Maven 构建生命周期：

```
mvn -DskipTests package
  → validate
  → compile          (生产代码编译)
  → test-compile     (测试代码编译 ← 仍会执行！)
  → test             (测试运行 ← 被跳过)
  → package
```

### 容易混淆的三个参数

| 参数 | 跳过编译 | 跳过运行 | 适用场景 |
|------|---------|---------|----------|
| `-DskipTests` | ❌ 不跳过 | ✅ 跳过 | 测试运行慢但代码没问题，想快速打包 |
| `-Dmaven.test.skip=true` | ✅ 跳过 | ✅ 跳过 | 测试代码有问题需要紧急打包绕过 |
| `-DskipTests=false` | ❌ 不跳过 | ❌ 不跳过 | 正常运行测试（默认） |

### 经验教训

1. **不要用 `-DskipTests` 来"绕过"测试代码问题**——它拦不住编译错误
2. **打包失败时，如果报错是 `testCompile` 阶段，先看测试代码**，别盯着生产代码找
3. **临时绕过测试编译用 `-Dmaven.test.skip=true`**，但这是应急手段，事后必须修复测试代码
4. **测试代码是一等公民**，和生产代码同等重要，不同步就是 bug

### 快速验证命令

```bash
# 只编译测试代码，快速验证测试代码是否能编译（不运行测试）
mvn test-compile

# 完全跳过测试（编译+运行都跳过）——应急用
mvn -Dmaven.test.skip=true package

# 只跳过测试运行（测试代码仍编译）——默认打包方式
mvn -DskipTests package
```

### 相关文档

- [root-cause-analysis-tender-test-out-of-sync.md](file:///Users/user/xiyu/xiyu-bid-poc/docs/lessons/root-cause-analysis-tender-test-out-of-sync.md) — 实际踩坑案例：测试代码与生产代码不同步导致打包失败

---

## 1. git-commit-id-maven-plugin 在 worktree 中读取主仓库 HEAD，git.properties 元数据失真

### 问题

在 git worktree 中执行 `mvn package` 构建后，jar 内的 `git.properties` 显示的 commit id **不是当前 worktree 分支的 HEAD**，而是主仓库（main worktree）的 HEAD。

```bash
# 在 worktree agent/zcode/co-277 中构建
$ git log -1 --format='%H'   # 当前 worktree HEAD
9c25985ff...

$ unzip -p target/bid-poc-1.0.3.jar git.properties | grep commit
git.commit.id=1e8d18e...   # ❌ 这是主仓库 HEAD，不是当前分支！
```

### 根因

`git-commit-id-maven-plugin` 通过 `.git` 目录读取 git 信息。在 worktree 中，`.git` 是一个**指向主仓库 git 目录的文件**（不是目录），plugin 读取的是主仓库的 HEAD（通常是 main），而非当前 worktree 的分支 HEAD。

```bash
$ cat .git
gitdir: /Users/user/xiyu/worktrees/zcode/.git/worktrees/zcode   # 指向主仓库

# plugin 读取的是主仓库 HEAD（main 分支），不是当前 worktree 的 HEAD
```

### 危害

部署后用 `git.properties` 验证"jar 是否含本次修复"会得到**错误结论**——git.properties 显示旧 commit，但 jar 内的 class 文件其实是新代码。这会导致：

1. 误判"部署失败/回退"，重复部署浪费时间
2. 误判"代码没编译进去"，反复检查编译流程
3. 排查方向跑偏（去查 CI/CD 流程，而非验证 class 内容）

### 正确做法：用 class 文件内容验证，而非 git.properties

```bash
# ✅ 正确：直接验证 jar 内 class 文件是否含本次修复的符号/字符串
$ unzip -p target/bid-poc-1.0.3.jar \
    BOOT-INF/classes/com/xiyu/bid/integration/external/CrmTenderLinkService.class \
    | strings | grep -E "tryParseChanceId|findProjectLeaderByChanceId"
findProjectLeaderByChanceId   # ✅ 命中，说明新代码已编译进 jar
tryParseChanceId

# ❌ 错误：信任 git.properties（worktree 下会失真）
$ unzip -p target/bid-poc-1.0.3.jar git.properties | grep commit
git.commit.id=1e8d18e...   # 误导：显示主仓库 HEAD，非当前分支
```

服务器端验证同理：

```bash
# 服务器上验证部署的 jar 含新代码
$ sudo unzip -p /opt/xiyu-bid/shared/backend/app.jar \
    BOOT-INF/classes/com/xiyu/bid/integration/external/CrmTenderLinkService.class \
    | strings | grep "tryParseChanceId"
tryParseChanceId   # ✅
```

### 通用规则

1. **worktree 构建的 jar，git.properties 不可信**——它读取主仓库 HEAD，不是当前分支
2. **验证 jar 含某次修复，用 class 文件 `strings` 查特征符号**（方法名、常量字符串），这是编译产物的直接证据
3. **不要用 git.properties 作为部署验证依据**，尤其在 worktree 开发流程下
4. 选择特征符号时挑**本次新增的、唯一的**（如新方法名 `tryParseChanceId`），避免命中旧代码

### 相关命令

```bash
# 查 jar 内 class 是否含某符号
unzip -p <jar> BOOT-INF/classes/<path>.class | strings | grep "<symbol>"

# 查 git.properties（仅供参考，worktree 下不可信）
unzip -p <jar> git.properties | grep -E "commit|branch"

# 查 worktree 的 .git 指向
cat .git
```
