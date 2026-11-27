# Day 41 — MCP 协议证据链

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 工具生态：MCP 与 Skill Hub（第 100–131 行）
>
> 实验 `vendor/ai-agent-book/chapter4/perception-tools/smoke_test_mcp_v2.py`
>
> 备课日期 2026-11-27 ｜ 授课日期 2026-11-27 ｜ 状态 ✅ 已完成

本文件是分步教学材料，不一次性贴入对话。正式实验由学员先预测、再在 PowerShell 中亲自运行并贴回输出。

## 1. 建立概念与实验全景

**本课只回答**：看到“MCP 工具调用成功”时，它能证明证据链走到了哪一层，又不能证明什么？

**类比**：把 MCP 看成 Android 的 Binder/AIDL 互操作协议。客户端与服务端先协商协议，再查询接口清单，最后发起一次事务并收到 reply。Binder 事务成功能证明通信和服务实现返回了结果，但不能单独证明 ViewModel 已采用结果、界面展示正确，更不能证明业务任务完成。

```text
启动 stdio 服务 → 协商协议 → tools/list → tools/call → 工具结果
                                                        ↓
                                      （本实验到此；没有模型请求与答案）
```

**实验全景预告**（不提前揭示结果）：

- 要完成的任务：通过真实 MCP stdio 启动教材感知工具服务器，列出工具，再调用一次 `file_reader`。
- 学员会亲自运行：同一冒烟脚本的 MCP 2.x 临时覆盖组，以及移除该覆盖的默认环境对照组。
- 会得到的证据：进程退出状态，以及 SDK 版本、协商协议、服务器名和工具数量；随后追到客户端检查与服务端工具注册源码。
- 结束时要能回答：一次通过能支持“连接、列出、调用”的哪些判断；为什么仍不能支持“模型看见结果或答案正确”。

**概念确认问题**：一次 Binder `transact()` 返回成功，能否仅凭这一事实断言页面已经正确展示 reply 中的数据？为什么？

## 2. 实验前预测

- 任务与输入：同一 `smoke_test_mcp_v2.py`、同一 `requirements.txt` 文件、同一教材服务器。
- 固定条件：`python -m uv run --extra ch4`、本机 Python/uv、stdio 传输和脚本检查逻辑。
- 单一变化：是否额外加入 `--with 'mcp>=2,<3'`。
- 观察点：导入是否通过、是否能协商协议、是否到达 `tools/list` 与 `tools/call`、最终退出状态。
- 预测问题：两组会在哪个最早阶段出现差异？请给出理由。
- 学员最初预测差异会在服务启动与握手阶段出现；经“模块顶层先导入 `Client`”的提示后，修正为默认环境可能先停在导入阶段。运行对照前一度预测会进入脚本内的版本门禁，经源码顺序复查后再次修正为顶层 `ImportError` 更早。

## 3. 运行实验

备课检查（不是正式实验）：默认 ch4 环境实测为 `mcp==1.28.1`；加入 `--with 'mcp>=2,<3'` 后，临时环境可导入 `mcp.Client`，解析到 `mcp==2.3.0`。备课未启动服务器，未执行 `tools/list` 或 `tools/call`。

### 3.1 MCP 2.x 临时覆盖组

给命令前先向学员说明：

- 输入/读取：读取现有冒烟脚本和 `requirements.txt`，用临时依赖覆盖选择 MCP 2.x。
- 处理：脚本启动教材 stdio 服务器，协商协议，列出工具，确认 `file_reader` 存在，再调用它。
- 输出/用途：成功路径打印 SDK、协议、服务器名和工具数；异常阶段与退出码用于定位最早断点。脚本没有模型请求。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\perception-tools'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch4 --with 'mcp>=2,<3' python smoke_test_mcp_v2.py 2>&1 |
  Tee-Object -FilePath 'lesson-d41-mcp-v2.txt'
"exit=$LASTEXITCODE"
```

学员于 2026-11-27 15:00 左右亲自在 PowerShell 运行并贴回：服务启动，读取 `requirements.txt` 974 个字符，随后打印 `sdk=2.3.0`、`protocol=2026-07-28`、`server=perception-tools`、`tools=127` 和 `exit=0`。PowerShell 将写到 stderr 的服务日志包装成了 `NativeCommandError`，但没有 Traceback，进程退出码为 0。

课后回读发现，原定的 `lesson-d41-mcp-v2.txt` 在 15:33 被一份默认环境的 `ImportError` 覆盖，并与 `lesson-d41-mcp-default.txt` 哈希相同；因此不能把该文件当作 A 组成功收据。讲师随后于 15:50 用同一 A 组命令作隔离诊断，成功输出保存在 `lesson-d41-mcp-v2-diagnostic.txt`，明确属于讲师诊断，不冒充学员正式运行。学员贴回的原始字段已记录在本讲义。

## 4. 逐段读真实轨迹

| 阶段 | 运行时证据 | 源码检查 | 可以支持 | 不能支持 |
|---|---|---|---|---|
| 客户端导入 | A 组 SDK 2.3.0 进入后续步骤；默认组在第 12 行 `ImportError`、`exit=1` | 顶层先 `from mcp import Client`，函数内另有 2.x 门禁 | 当前 2.x 覆盖能导入；当前默认 1.28.1 与该脚本不兼容 | 所有 MCP 1.x 程序都不能运行 |
| 协议握手 | A 组打印协议 `2026-07-28` 与服务器名 | `client.protocol_version` 与常量比较 | 本次 stdio 客户端与目标服务器完成指定协议协商 | 已发现或执行具体工具 |
| 工具发现 | A 组打印 `tools=127`，源码要求名单包含 `file_reader` 才能继续 | `client.list_tools()` 与名称检查 | 本次清单有 127 个不同名称且包含目标工具 | 目标工具已经执行 |
| 工具调用 | 服务端记录读取 974 个字符，脚本到达 `passed`、`exit=0` | `client.call_tool()` 只用 `is_error` 作门禁；服务端转交 `read_file` | MCP 客户端发起调用，服务实现读取目标路径并返回非错误结果 | 返回内容非空、语义正确、被模型使用 |
| 模型与答案 | 本实验无对应记录 | 脚本中无模型请求 | 无 | 模型是否收到、采用以及最终答案是否正确 |

## 5. 追到关键源码

课堂分段阅读，总量控制在约 30 行：

1. `smoke_test_mcp_v2.py:18–23`：读取 SDK 版本并在不是 2.x 时终止。
2. `smoke_test_mcp_v2.py:25–38`：以同一 Python 启动 stdio 服务，检查协商协议，执行 `list_tools` 并检查 `file_reader`。
3. `smoke_test_mcp_v2.py:40–47`：发出 `call_tool`，只检查 `is_error`，随后打印摘要。
4. `src/main.py:57–64` 与 `180–188`：服务器元信息、`file_reader` 注册及参数转交；课堂前重新核实行号。

源码推演问题：如果 `call_tool` 返回 `is_error=false`，但内容为空，这份冒烟脚本是否仍可能打印通过？先根据判定条件回答，再讨论需要增加什么证据。

学员确认现有门禁仍可能放过空内容，因为只检查 `result.is_error`。继续追到 `filesystem_tools.py:315–364`，核对路径校验、文件读取、截断、结果组装和 `TextContent` 返回；学员提出应增加 `content` 语义核对。课堂将其具体化为检查目标文件中的辨识标记（如 `mcp>=2,<3`），若要求固定快照完全一致则比较内容哈希。

## 6. 单变量对照

保持脚本、目录、输入、stdio 和 uv extra 不变，只移除 MCP 2.x 临时覆盖：

```powershell
python -m uv run --extra ch4 python smoke_test_mcp_v2.py 2>&1 |
  Tee-Object -FilePath 'lesson-d41-mcp-default.txt'
"exit=$LASTEXITCODE"
```

运行前先预测最早差异点。运行后按“导入 → 握手 → 列表 → 调用 → 结果”逐层定位，不把较早失败说成后续阶段已经执行。

学员实测默认组在 `smoke_test_mcp_v2.py:12` 导入 `Client` 时失败，PowerShell 显示 Traceback，退出码为 1。没有服务器启动日志，后续协议握手、`tools/list` 与 `tools/call` 均未发生。对照只支持“当前默认 MCP 1.28.1 环境与这个 2.x 脚本不兼容”，不能外推为所有 MCP 1.x 服务都不能运行。

## 7. 理解检查

1. **概念复述，通过**：学员区分了 MCP 协议的发现/调用规范、服务器的工具注册与实现、Skill Hub 的能力包分发，并说明 A 组验证到服务器调用。课堂将 Skill Hub 类比从 Activity 清单校准为 Maven 仓库或插件市场；`SKILL.md` 元数据才更像声明信息。
2. **结果预测，通过**：学员判断名单缺少 `file_reader` 时会在调用前停止，不能归因读取实现；经提示把“筛选过滤”从结论收紧为待查假设，还需排查未注册、连错服务器、可见性限制或清单异常。
3. **故障定位，通过**：学员优先检查工具内容是否进入上下文及下一轮请求。经反例复查，明确本地轨迹不等于真实 provider 请求，也不单独证明工具执行；应依次核对实际工具结果、下一轮真实模型请求，再查模型响应与最终答案。对于“文件已保存”的孤立日志，只确认记录存在；文件在核对时不存在说明业务状态不符合预期，仍不能单凭此证明工具从未执行。

<details><summary>讲师核对要点</summary>

1. MCP 是互操作协议；服务器是按协议暴露能力的进程；Skill Hub 是分发 skill 文件夹的注册表。本实验没有模型请求。
2. 名称门禁在调用前失败，不能据此声称 `file_reader` 实现已经运行或运行失败。
3. 先查工具结果是否进入下一轮模型请求，再查模型响应/最终答案；必要时另核对工具返回内容本身是否满足业务条件。

</details>

## 8. 结束记录

- 实测观察：MCP 2.x 覆盖组完成指定协议协商、列出 127 个工具并通过 `file_reader` 非错误调用；默认 1.28.1 组在顶层导入 `Client` 时退出。A 组原文本产物后来被覆盖，另保留讲师隔离诊断文件并在讲义中记录证据来源差异。
- 解释判断：当前默认环境的最早断点是客户端 API 导入，不是握手；A 组的 `passed` 只覆盖脚本设置的版本、协议、名单与 `is_error` 门禁。
- 尚未验证：返回内容的辨识标记或哈希、真实模型请求与采用、远程 Streamable HTTP、Skill Hub 安装及第三方供应链安全。
- 本课状态：实验、源码讲解、单变量对照与三项理解检查均已完成。
