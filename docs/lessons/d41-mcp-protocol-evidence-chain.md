# Day 41 — MCP 协议证据链

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 工具生态：MCP 与 Skill Hub（第 100–131 行）
>
> 实验 `vendor/ai-agent-book/chapter4/perception-tools/smoke_test_mcp_v2.py`
>
> 备课日期 2026-11-27 ｜ 授课日期 待开展 ｜ 状态 待开展

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
- 学员预测：待回答。

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

真实输出：待学员运行并贴回。不要把脚本打印的 `passed` 外推为模型使用或业务答案正确。

## 4. 逐段读真实轨迹

| 阶段 | 运行时证据 | 源码检查 | 可以支持 | 不能支持 |
|---|---|---|---|---|
| 客户端导入 | 待运行 | `mcp` 版本门禁 | 待分析 | 待分析 |
| 协议握手 | 待运行 | `client.protocol_version` | 待分析 | 待分析 |
| 工具发现 | 待运行 | `client.list_tools()` 与名称检查 | 待分析 | 待分析 |
| 工具调用 | 待运行 | `client.call_tool()` 与 `is_error` 检查 | 待分析 | 待分析 |
| 模型与答案 | 本实验无对应记录 | 脚本中无模型请求 | 无 | 模型是否收到、采用以及最终答案是否正确 |

## 5. 追到关键源码

课堂分段阅读，总量控制在约 30 行：

1. `smoke_test_mcp_v2.py:18–23`：读取 SDK 版本并在不是 2.x 时终止。
2. `smoke_test_mcp_v2.py:25–38`：以同一 Python 启动 stdio 服务，检查协商协议，执行 `list_tools` 并检查 `file_reader`。
3. `smoke_test_mcp_v2.py:40–47`：发出 `call_tool`，只检查 `is_error`，随后打印摘要。
4. `src/main.py:57–64` 与 `180–188`：服务器元信息、`file_reader` 注册及参数转交；课堂前重新核实行号。

源码推演问题：如果 `call_tool` 返回 `is_error=false`，但内容为空，这份冒烟脚本是否仍可能打印通过？先根据判定条件回答，再讨论需要增加什么证据。

## 6. 单变量对照

保持脚本、目录、输入、stdio 和 uv extra 不变，只移除 MCP 2.x 临时覆盖：

```powershell
python -m uv run --extra ch4 python smoke_test_mcp_v2.py 2>&1 |
  Tee-Object -FilePath 'lesson-d41-mcp-default.txt'
"exit=$LASTEXITCODE"
```

运行前先预测最早差异点。运行后按“导入 → 握手 → 列表 → 调用 → 结果”逐层定位，不把较早失败说成后续阶段已经执行。

## 7. 理解检查

1. **概念复述**：用自己的话区分 MCP 协议、MCP 服务器、Skill Hub；再说明本实验验证到哪一层。
2. **结果预测**：若协议握手和 `tools/list` 成功，但列表没有 `file_reader`，脚本会在哪一行附近停止？能否说服务端读取函数执行失败？
3. **故障定位**：若 `tools/call` 返回非错误，但 Agent 最终答案没使用文件内容，下一步应补查哪两段证据？

<details><summary>讲师核对要点</summary>

1. MCP 是互操作协议；服务器是按协议暴露能力的进程；Skill Hub 是分发 skill 文件夹的注册表。本实验没有模型请求。
2. 名称门禁在调用前失败，不能据此声称 `file_reader` 实现已经运行或运行失败。
3. 先查工具结果是否进入下一轮模型请求，再查模型响应/最终答案；必要时另核对工具返回内容本身是否满足业务条件。

</details>

## 8. 结束记录

- 实测观察：待填写。
- 解释假设：待填写。
- 尚未验证：真实模型请求、模型采用、远程 Streamable HTTP、Skill Hub 安装与第三方供应链安全。
- 本课状态：待实验、源码讲解、单变量对照与三项理解检查全部完成后更新。
