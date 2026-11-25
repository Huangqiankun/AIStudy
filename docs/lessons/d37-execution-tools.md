# Day 37 — 执行工具如何把无效动作拦在副作用之前

> 核心问题：工具返回“校验失败”时，怎样证明无效内容没有落盘，而不只是相信一段状态文本？

- 教材锚点：`vendor/ai-agent-book/book/chapter4.md`「执行工具」
- 实验目录：`vendor/ai-agent-book/chapter4/execution-tools/`
- 关键实现：`file_tools.py`、`llm_helper.py`、`cli.py`
- 产物：系统临时目录中的 `probe.py` 与 `broken.py`；不修改 `vendor/`

## 1. 建立概念：执行工具多了一条副作用证据链

感知工具像读取 `ContentProvider`：主要风险是读少了却当成全貌。执行工具更像一次带校验的 Room 事务：请求合法、函数返回、事务真正提交、提交后的数据正确，是四件需要分别核对的事。

```text
调用参数
   │
   ▼
路径/权限检查 ──拒绝──> 结构化错误
   │通过
   ▼
内容验证       ──拒绝──> 结构化错误 + 不应产生副作用
   │通过
   ▼
执行写入 ──────────────> success / path / bytes_written
   │
   ▼
独立回读文件系统状态
```

实验全景：

| 项目 | 本课安排 |
|---|---|
| 任务 | 让同一个 `file_write` 分别处理合法与非法 Python 内容 |
| 会运行什么 | CLI 帮助、合法写入、非法写入、关闭验证后的单变量对照 |
| 会观察什么 | `success`、`verification`、`error`，以及 `Test-Path` / 文件回读 |
| 最终能回答什么 | 校验发生在写入前还是写入后；工具返回与真实副作用各能证明什么 |

概念确认：若工具只返回 `success=false, verification=failed`，能否直接断定目标文件从未创建？为什么？

## 2. 实验前预测

固定条件：

- 同一份当前教材源码与 `ch4` 依赖；
- 同一个可丢弃临时工作区；
- 同一个 `broken.py` 路径与同一段非法 Python；
- 不调用外部模型，不使用真实业务目录。

预测分两层写：

1. 工具返回层：打开自动验证时，`success`、`verification`、`error` 会是什么？
2. 业务状态层：命令结束后，`broken.py` 是否存在？

先记录理由，不把一次结果预设成必然规律。

## 3. 运行实验

### 步骤 A：环境准备与 CLI 核对

输入是当前 `execution-tools` 目录和 CLI 的帮助请求；程序只解析入口与参数，不执行目标写入。输出用于核对 `--workspace`、`--no-verify` 和 `write` 子命令确实存在。

```powershell
Set-Location F:\AIStudy\vendor\ai-agent-book\chapter4\execution-tools
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
python -m uv run --extra ch4 python cli.py --help
python -m uv run --extra ch4 python cli.py write --help
```

### 步骤 B：合法写入基线

数据流分四步：创建唯一临时目录 → 把合法 Python 和相对路径交给 `file_write` → 工具校验后尝试写入 → 独立检查文件是否存在并回读内容。重点观察返回中的 `success`、`verification`、`path`、`bytes_written`，再与文件系统状态对照。

```powershell
Set-Location F:\AIStudy\vendor\ai-agent-book\chapter4\execution-tools
$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"
$work = Join-Path ([IO.Path]::GetTempPath()) ("aistudy-d37-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $work | Out-Null
python -m uv run --extra ch4 python cli.py --workspace $work write --path probe.py --content "print('ok')"
"exists=$(Test-Path (Join-Path $work 'probe.py'))"
Get-Content (Join-Path $work 'probe.py')
"work=$work"
```

### 步骤 C：非法内容与写入前验证

输入改为一段缺少函数体的非法 Python，其余仍使用同一工作区和 `file_write`。程序先做路径检查和语法验证，再决定是否写入。输出层看失败字段，业务状态层用 `Test-Path` 单独检查；两类证据不能互相代替。

```powershell
python -m uv run --extra ch4 python cli.py --workspace $work write --path broken.py --content "def broken():" --overwrite
"exists=$(Test-Path (Join-Path $work 'broken.py'))"
```

## 4. 逐段读真实轨迹

收到学员贴回输出后，按以下顺序核对：

1. 请求/调用：CLI 实际收到的 `workspace`、`path`、`content`、`overwrite`；
2. 执行前检查：路径是否在工作区内，语法验证是否运行；
3. 工具结果：`success` / `verification` / `error` 记录了什么；
4. 副作用：`Test-Path` 与回读证明了什么；
5. 结论边界：本次离线 Python 路径能否外推到外部 API、MCP 传输或模型请求。

若工具说失败但文件存在，优先追查写入和验证的先后顺序；若工具说成功但文件不存在，优先追查工作目录、返回路径和写入异常。不要只重复同一命令。

## 5. 追到关键源码

分三小段阅读，总量约 40 行：

1. `cli.py:58-83`：全局参数如何先写入环境，再延迟构造工具；
2. `file_tools.py:51-100`：路径边界、覆盖审批、语法验证、真实写入与返回字段的顺序；
3. `llm_helper.py:270-291`：Python 验证怎样用 `compile()` 给出确定性结果。

阅读时逐段回答：输入是什么、哪一个分支会提前返回、哪一行才产生文件副作用、返回值能证明到哪一层。

## 6. 单变量对照

唯一变量：在步骤 C 的同一命令上增加全局开关 `--no-verify`。工作区、目标路径、非法内容和覆盖设置保持不变。

先预测：关闭自动验证后，工具结果层和文件系统层各会怎样变化？

命令的数据流仍是“接收相同输入 → 路径检查 → 跳过或执行内容验证 → 尝试写入 → 独立回读”。关注 `verification` 是否变为 `skipped`，也要实际回读 `broken.py`，不能把字段名当作副作用证明。

```powershell
python -m uv run --extra ch4 python cli.py --workspace $work --no-verify write --path broken.py --content "def broken():" --overwrite
"exists=$(Test-Path (Join-Path $work 'broken.py'))"
if (Test-Path (Join-Path $work 'broken.py')) { Get-Content (Join-Path $work 'broken.py') }
```

边界：这个对照验证的是当前 `file_write` 的 Python 写入路径；它不证明审批模型可靠、不证明 Docker 是强沙盒，也不证明外部系统操作可回滚。

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么执行工具的 `success` 字段不能代替业务状态回读？
2. **变化预测**：保持非法内容不变，只关闭 `AUTO_VERIFY_CODE`，预期哪一个执行阶段被移除？哪些阶段仍存在？
3. **故障定位**：若返回 `verification=failed`，但 `broken.py` 已出现，第一步应查哪段源码与哪类证据？

<details>
<summary>参考答案（完成作答后展开）</summary>

1. 它是工具生成的结果记录；独立回读才能确认副作用和最终业务状态，且还要核对路径是否一致。
2. 只移除内容语法验证；参数解析、路径边界检查、写入尝试和返回构造仍存在。
3. 先查 `file_tools.py` 中验证与 `write_text()` 的先后顺序，同时核对真实返回路径、时间和文件内容；不要仅凭失败标签归因。

</details>

## 8. 可选 AI 代码演示

本课不要求 Kotlin 练习。若需要换例，可在另一个临时目录对 `.json` 或 `.js` 做相同的“验证开/关”推演，但必须先确认当前实现对该后缀使用什么验证器。

## 9. 卡点（课堂后共同记录）

- 学员一开始正确区分了“工具返回失败”和“副作用未发生”，并保留了先写后回滚的可能性。
- 追源码时曾把第 82 行的验证调用说成首次副作用，但同时正确识别了失败分支会提前返回；经纠正后明确第 94 行 `write_text()` 才是实际写入点。
- 故障定位时最初只考虑“验证阶段是否创建文件”；缩小问题后，能优先核对工具使用的绝对路径与观察路径，而不是无信息增益地重复运行。
- 实测证据：开启验证时返回 `success=false`、`verification=failed` 且 `exists=False`；保持工作区、路径、内容和覆盖设置不变，仅增加 `--no-verify` 后返回 `success=true`、`verification=skipped`，文件存在且回读为 `def broken():`。
- 下次从哪一小步继续：Day 38，进入协作工具，先区分“子 Agent 返回结果”和“主 Agent 已正确采用结果”。

## 10. 讲师备注

- 备课已核对 `cli.py --help` 与 `write --help`，未代跑本课目标写入实验。
- 当前实验只走本地 CLI 与确定性 Python `compile()` 校验；没有验证 MCP stdio、模型审批、容器沙盒或外部系统副作用。
- 结束时必须分别记录工具结果与 `Test-Path`/回读，不把任一方单独包装成完整结论。
- 课堂完成了非法 Python 的验证开/关单变量对照、`file_tools.py` 与 `llm_helper.py` 源码剖析，以及概念复述、变化预测、故障定位三项检查。
