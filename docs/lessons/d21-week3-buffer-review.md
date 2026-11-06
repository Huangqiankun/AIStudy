# Day 21 — W3 上下文布局与边界复盘（buffer）

> 章节：W3 / ch2 buffer 复盘（`vendor/ai-agent-book/book/chapter2.md`）
> 实验：复用 Day 15–20 的真实证据，重点审计 `vendor/ai-agent-book/chapter2/context-compression/results/d20-*.json`
> 备课日期 2026-11-06 ｜ 授课日期 2026-11-06 ｜ 状态 进行中

本日是 buffer 日，不引入 ch3 的记忆机制，只复盘 W3 已学过的工具定义、Skills、状态栏、压缩与隔离。本文是分步教学材料，不一次性贴入对话。

## 1. 建立概念：先画信息边界，再评价 Agent 表现

**本课只回答**：面对一段可能影响 Agent 决策的信息，怎样判断它应该放在哪个上下文位置、何时进入哪个 Agent 的请求，以及用什么证据确认边界没有放错？

**类比**：Android 里不会把所有能力、状态和大对象都塞进一个 `Intent`。接口能力由 AIDL/接口契约声明；可选功能按需加载；当前进度放在小型状态对象里；跨进程任务只返回一个约定好的 Result Bundle。Agent 的工具定义、Skill、状态栏、压缩和子 Agent 隔离，也是在决定“什么信息、何时、以什么表示、进入谁的请求”。类比边界是：本课讨论模型请求的上下文边界，不等同于进程级内存或安全沙箱隔离。

```text
                         ┌─ tools：可调用接口契约
稳定前缀 ────────────────┤
                         └─ Skill 薄目录：可发现能力
                                   │ 选中后
                                   v
动态轨迹：user → assistant → tool/Skill 正文 → ... → 状态栏
                                   │
                          过长时选择一种处理
                           ┌───────┴────────┐
                           v                v
                    压缩后仍进主请求    原始证据留在子请求
                                           │
                                           v
                                  只把 Result 交给主请求
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：审计同一份源码证据经子 Agent 隔离后，任务包完整与含糊两条路径的信息流；判断每份证据进入了哪个请求、主 Agent 能否恢复未回传的事实。
- 学员会亲自运行：先用一条 PowerShell 只读命令核对冻结收据的请求边界和结果契约，再读关键源码；随后比较唯一变化为“子 Agent 任务描述”的配对收据，不重新调用模型。
- 会得到的产物：四次请求的消息角色、输入/输出字符数、提示 token 旁证、三个源码标记在各请求与回答中的出现位置，以及最终回答中的待核对名称。
- 结束时要能回答：信息“存在于本地、进入子请求、出现在 Result、进入主请求、出现在最终回答、符合权威源码”为什么是六个不同事实；又该怎样在定义、按需加载、状态栏、压缩和隔离之间作选择。

**确认问题**：一个子 Agent 已读过完整源码，主 Agent 只收到一句“已检查，压缩链路正常”。现在主 Agent 给出了正确行号——仅凭这个最终答案，能否证明源码原文进入过主 Agent 的请求？只回答“能”或“不能”，再说一句理由。

## 2. 实验前预测

- 任务与输入：复用 Day 20 的同一份源码证据、同一主任务和同一隔离架构。
- 固定条件：本地 Ollama `qwen3:8b`、`temperature=0`、`seed=20261105`、相同源码证据、相同主 Agent system message；本课只读已有收据，不再次调用模型。
- 唯一变化：子 Agent 的任务包是完整的三项验收标准，或一句含糊的“找出与压缩有关的代码，给我结论”。
- 观察点：子请求里有哪些原始标记；子 Agent Result 保留了哪些任务所需事实；主请求实际携带什么；最终回答中出现的函数名能否回到源码核实。
- 预测问题：两条子 Agent 路径都读到同一份原始证据时，哪一层会最先出现分歧？如果含糊路径返回的文字更多，是否就表示 Result 更完整？为什么？
- 学员预测：待回答。

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行。课堂一次只给当前步骤的一段命令，等待真实输出后再继续。

### 步骤 A：确认收据与源码存在

**输入/读取**：进入 `context-compression` 实验目录，只检查两份冻结收据和两份权威源码文件是否存在。

**处理/筛选**：不解析答案、不调用模型，只确认实验输入齐全。

**输出/用途**：四个布尔值；它们只证明本地文件存在，不证明收据内容或源码结论。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\context-compression'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

[pscustomobject]@{
    IsolationReceipt = Test-Path 'results/d20-isolation-probe.json'
    BriefReceipt = Test-Path 'results/d20-brief-comparison.json'
    AgentSource = Test-Path 'agent.py'
    StrategySource = Test-Path 'compression_strategies.py'
} | Format-List
```

**真实证据**：待学员运行并贴回输出。

### 步骤 B：审计四次请求的边界

收到步骤 A 输出并完成预测后才给本步骤。

**输入/读取**：`results/d20-brief-comparison.json` 中完整/含糊两条隔离路径的子 Agent 与主 Agent 请求和响应。

**处理/筛选**：

1. 把四次请求各压成一行，只保留角色、user 字符数、回答字符数和服务端返回的提示 token 旁证。
2. 用三个源码标记分别检查它们是否出现在子请求、子结果、主请求和主回答。
3. 输出模型回答里声称的函数名片段，留到源码步骤核对。

**输出/用途**：定位两条路径第一次分歧发生在哪一层；字符数只表示体积，标记只表示文本出现，二者都不自动代表正确理解。

```powershell
$r = Get-Content -Raw -Encoding UTF8 'results/d20-brief-comparison.json' | ConvertFrom-Json
$runs = @(
    $r.complete_brief_subagent
    $r.vague_brief_subagent
    $r.complete_brief_main
    $r.vague_brief_main
)

$runs | ForEach-Object {
    [pscustomobject]@{
        Name = $_.name
        Roles = ($_.request.messages.role -join ' -> ')
        UserChars = ($_.request.messages | Where-Object role -eq 'user').content.Length
        AnswerChars = $_.response.message.content.Length
        PromptTokens = $_.prompt_eval_count
    }
} | Format-Table -AutoSize

$needles = '_context_aware_summary','tool_content','messages=messages'
$markerRows = foreach ($run in $runs) {
    $requestText = $run.request.messages.content -join "`n"
    $answerText = $run.response.message.content
    foreach ($needle in $needles) {
        [pscustomobject]@{
            Run = $run.name
            Marker = $needle
            InRequest = $requestText.Contains($needle)
            InAnswer = $answerText.Contains($needle)
        }
    }
}
$markerRows | Format-Table -AutoSize
```

**真实证据**：待学员运行并贴回输出。不能用历史讲义中的结果替代本次 PowerShell 输出。

## 4. 逐段读真实轨迹

| 片段 | 真实摘录 / 证据字段 | 能直接证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 完整子请求 | 待运行 | 待观察 | 模型有效使用了全部证据 | 待回答 |
| 含糊子请求 | 待运行 | 待观察 | 任务目标足够明确 | 待回答 |
| 两种 Result | 待运行 | 回传文本实际包含什么 | 主 Agent 能恢复未写入的事实 | 待回答 |
| 两个主请求 | 待运行 | Result 是否进入主请求 | 原始源码进入了主请求 | 待回答 |
| 最终回答 | 待运行 | 模型最后表达了什么 | 函数名、行号符合源码 | 待回答 |

本课统一使用六层证据链：本地已有信息 → 子 Agent 请求 → 子 Agent Result → 主 Agent 请求 → 最终回答 → 权威源码。不能用后一层的“看起来正确”倒推前一层一定发生。

## 5. 追到关键源码

**源码锚点**（当前 checkout 已核实，课堂仍以文件为准）：

- `compression_strategies.py:100-129`：`compress_search_results()` 根据策略选择不同表示。
- `compression_strategies.py:454-501`：`_context_aware_summary()` 生成上下文感知摘要。
- `agent.py:450-468`：`_non_streaming_response()` 把 `messages` 交给 API 客户端。
- `agent.py:622-640`：选择压缩内容或原文，构造 `role=tool` 后追加到下一轮消息列表。

课堂分小段读取，总量控制在 20–40 行：

- 输入 → 处理 → 输出：工具原始结果和压缩候选 → Harness 选择进入主轨迹的表示 → `messages` 被提交给模型客户端。
- 关键边界：模型回答里的函数名不是源码事实；必须在原文件中找到对应 `def` 或明确标记“证据不足/补造”。
- W3 迁移：工具定义决定模型先看到什么接口；Skill 决定完整指令何时加载；状态栏在末尾追加显式状态；压缩替换主轨迹中的表示；隔离让原始证据停留在另一次请求。五者都要回到实际 request body 验证。
- 学员解释：待回答。

## 6. 单变量对照：完整任务包 vs 含糊任务包

- 唯一改变：子 Agent 的 user task 是否包含三项可验收目标。
- 保持不变：同一模型、同一 seed、同一原始源码证据、同一隔离架构、同一主任务与主 system message。
- 对照前预测：待回答。
- 对照收据：`results/d20-brief-comparison.json`，由步骤 B 只读，不重跑模型。
- 真实轨迹与结果：待学员运行、解释并回到源码核验。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据首次分歧位置提出 | 待提出多次配对或更强收据验证方法 |

若输出字符更多，也只能观察到 Result 更长；“更完整”必须按主任务的验收字段逐项核对。单次冻结运行不能证明任务描述在所有模型上必然导致同样差异。

## 7. 理解检查：每次只问一题

1. **概念复述**：怎样依据“稳定性、相关范围、是否要保留原文、目标 Agent”在工具定义、Skill、状态栏、压缩和隔离之间作选择？
2. **结果预测**：如果子 Agent Result 漏掉一个源码事实，但主 Agent 仍输出了对应函数名，你会怎样预测它的可靠性，并说明理由？
3. **故障定位**：主 Agent 回答错误时，怎样沿六层证据找到第一个分歧点；哪些证据可以区分“子任务不完整”“Result 丢信息”“主 Agent 补造”和“收据字段不足”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 稳定且全局的接口契约放 `tools`/稳定前缀；按任务选择的详细流程用 Skill；从长轨迹推导出的当前状态用末尾状态栏；仍需主 Agent 看到但可换表示的历史用压缩；只需结论、不需原始大证据的工作用隔离。选择后仍需检查实际请求。
2. 该名称可能来自任务先验、语言模式或补造，不能因碰巧存在/不存在就倒推来源；先查 Result 和主 request，再回源码核对。
3. 依次核对本地证据、子 request、子 Result、主 request、最终文本、权威源码；第一个缺失或变形处才是优先故障层。若缺真实出站请求或服务端收据，只能限定到本地构造层。

</details>

卡住时针对具体层补讲，并换一个 Android/HarmonyOS 小例子复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展；通过后进入 Day 22 / ch3 记忆。
- 状态：▶ 进行中。

## 9. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-06；待课堂推进。
- 实验命令、模型、证据位置与必要真实摘录：冻结收据来自 Day 20 的 `qwen3:8b`；本课只读，不把旧运行冒充本次模型调用。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 是否采用代码演示及原因：不新增代码；用 PowerShell 只读审计既有 JSON 和权威源码。
- 本次观察、假设、待验证点：待开展。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`。
- 进度处理：仅创建讲义不追加日志；本课结束或中断后按真实进度在 `docs/progress.md` 追加 Day 21 记录。
