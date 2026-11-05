# Day 20 — 隔离优于压缩：怎样让大结果不进入主上下文

> 教材 `vendor/ai-agent-book/book/chapter2.md` § 隔离优于压缩：子 Agent 上下文隔离（第 1089–1095 行）
> 关联实验 `vendor/ai-agent-book/chapter2/context-compression/`；本课最小隔离探针写在本讲义中，不修改 `vendor/`
> 备课日期 2026-11-05 ｜ 授课日期 2026-11-05 ｜ 状态 ✅

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行、源码剖析、单变量对照和三项理解检查时，不填完成状态。

## 1. 建立概念：为什么隔离发生在压缩之前

**本课只回答**：同一批大体积工具结果，怎样通过独立模型调用留在子 Agent 的上下文中，只把任务结论送入主 Agent；我们又该用什么证据确认原始结果没有进入主 Agent 请求。

**类比**：子 Agent 上下文隔离类似 Android 的 `startActivityForResult`：被启动页面可以读取很多数据、经历很多中间状态，但返回方只收到约定的 Result `Bundle`。压缩则更像先把整个大对象交给返回方，再调用一次瘦身逻辑。类比边界是：子 Agent 的 Result 通常由模型生成，可能遗漏或误解；它不像类型固定、字段确定的 Android `Bundle` 那样天然可靠。

```text
直接搜索后压缩                         子 Agent 隔离

原始搜索结果 ──▶ 主 Agent 请求          原始搜索结果 ──▶ 子 Agent 请求
                     │                                    │
                     ▼                                    ▼
                事后摘要/删除                         结论性 Result
                     │                                    │
                     ▼                                    ▼
                主上下文已被污染                    主 Agent 请求只接收结论

核对边界：不是看最终答案像不像，而是分别检查每次请求的 messages。
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：定位教材 `context-compression` 实验里“上下文感知压缩”和“压缩结果写入下一轮消息”的源码位置。
- 学员会亲自运行：先生成一份固定源码搜索证据；再让同一 `qwen3:8b` 分别走“主 Agent 直接读原始证据”和“子 Agent 读原始证据、主 Agent 只读结论”两条路径。
- 会得到的产物：`results/d20-isolation-probe.json`，保存三次本地 Ollama 请求的消息、响应、`prompt_eval_count` 与请求字符数。
- 单变量对照：保持隔离架构、模型、源码证据和主任务不变，只把发给子 Agent 的任务包从自包含描述换成含糊描述。
- 结束时要能回答：隔离发生在哪个请求边界；它比压缩少让主 Agent 看见了什么；又新增了哪一种信息丢失风险。

**确认问题**：子 Agent 搜索了十几个文件，但主 Agent 的请求里只有子 Agent 返回的 200 字结论——这十几个文件的内容算不算“已被主 Agent 压缩过”？为什么？

## 2. 实验前预测

- 任务与输入：同一个源码定位任务、同一份由 PowerShell `Select-String` 生成的源码证据。
- 固定条件：本地 Ollama、`qwen3:8b`、`temperature=0`、`seed=20261105`、相同主 Agent system message。
- 唯一架构变化：主 Agent 的 user message 携带原始搜索证据，或只携带子 Agent 结论。
- 观察点：三次本地请求各自的 `messages`、请求字符数、`prompt_eval_count`、响应正文；不能只看最终答案。
- 预测问题：哪一次请求会包含原始源码证据？两条主 Agent 路径的请求体积可能怎样变化？回答完整性是否一定同步变化？
- 学员预测：隔离路径的主请求会更短，但回答完整性不一定更好；还取决于子 Agent 收到的任务描述和返回结果是否完整。实测支持这一风险判断。

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行。对话里一次只给当前步骤的一个代码块，等待学员贴回真实输出后再给下一步。

### 步骤 A：环境与模型核对

**输入/读取**：进入教材的压缩实验目录，并读取本机 Ollama 模型清单。

**处理/筛选**：不调用模型、不读取目标源码内容，只确认服务可访问且 `qwen3:8b` 存在。

**输出/用途**：模型清单与版本信息，用于确认后续三次请求能在同一环境运行；这一步不产生本课实验结论。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\context-compression'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
ollama list
```

真实输出：学员在 PowerShell 中确认本机 Ollama 可用，`qwen3:8b` 已安装；当前目录为 `chapter2/context-compression`。

### 步骤 B：生成固定证据并运行两条路径

在完成预测后才给本步骤。

**输入/读取**：`agent.py` 和 `compression_strategies.py`；固定任务要求定位实现函数、工具结果选取点及 `messages` 进入 API 的位置。

**处理/筛选**：

1. PowerShell `Select-String` 用固定关键词定位源码，再读取每个命中点前后各两行，生成同一份带文件名和行号的证据。
2. 直接路径把原始证据放进主 Agent 请求。
3. 隔离路径先把原始证据放进独立的子 Agent 请求，再只把其结论放进主 Agent 请求。
4. 三次请求都保存本地构造的消息、Ollama 返回的提示 token 计数和回答，并比较两个主请求的字符数。

**输出/用途**：控制台只显示关键指标和回答；完整收据写入 `results/d20-isolation-probe.json`。请求中的 `messages` 用于确认边界，`prompt_eval_count` 用作服务端返回的输入规模旁证；二者都不能单独证明模型内部如何利用信息。

```powershell
$model = 'qwen3:8b'
$task = @'
定位本目录的上下文感知压缩链路：
1. 写出选择 context-aware 策略的实现函数；
2. 写出工具结果进入下一轮 messages 前选择原文或压缩内容的位置；
3. 写出 messages 被交给模型 API 的位置。
只依据给定源码证据回答文件、函数和行号；证据不足必须明确说明。
'@

$pattern = 'ContextCompressor|compress_search_results|_context_aware_summary|compressed_result|tool_content|messages=messages'
$evidenceLines = foreach ($match in (
    Select-String -Path 'agent.py','compression_strategies.py' -Pattern $pattern
)) {
    $fileName = Split-Path $match.Path -Leaf
    $sourceLines = Get-Content -Encoding utf8 $match.Path
    $startLine = [Math]::Max(1, $match.LineNumber - 2)
    $endLine = [Math]::Min($sourceLines.Count, $match.LineNumber + 2)

    for ($lineNumber = $startLine; $lineNumber -le $endLine; $lineNumber++) {
        '{0}:{1}:{2}' -f $fileName, $lineNumber, $sourceLines[$lineNumber - 1]
    }
    '--'
}
$evidence = ($evidenceLines -join "`n").Trim()

function Invoke-LocalAgent {
    param(
        [string]$Name,
        [object[]]$Messages
    )

    $body = [ordered]@{
        model = $model
        messages = $Messages
        stream = $false
        think = $false
        options = [ordered]@{
            temperature = 0
            seed = 20261105
            num_predict = 700
        }
    }
    $requestJson = $body | ConvertTo-Json -Depth 10 -Compress
    $response = Invoke-RestMethod `
        -Uri 'http://localhost:11434/api/chat' `
        -Method Post `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([Text.Encoding]::UTF8.GetBytes($requestJson))

    [pscustomobject]@{
        name = $Name
        request = $body
        request_chars = $requestJson.Length
        prompt_eval_count = $response.prompt_eval_count
        response = $response
    }
}

$mainSystem = '你是主 Agent。依据收到的证据完成源码定位；不要补造未提供的代码。'
$directMessages = @(
    @{ role = 'system'; content = $mainSystem }
    @{ role = 'user'; content = "$task`n`n原始源码搜索证据：`n$evidence" }
)
$direct = Invoke-LocalAgent -Name 'direct_main' -Messages $directMessages

$subMessages = @(
    @{ role = 'system'; content = '你是搜索子 Agent。完成源码定位，只返回主 Agent 完成任务所需的结论、行号和证据缺口。' }
    @{ role = 'user'; content = "$task`n`n原始源码搜索证据：`n$evidence" }
)
$sub = Invoke-LocalAgent -Name 'search_subagent' -Messages $subMessages

$isolatedMessages = @(
    @{ role = 'system'; content = $mainSystem }
    @{ role = 'user'; content = "$task`n`n子 Agent 回传：`n$($sub.response.message.content)" }
)
$isolated = Invoke-LocalAgent -Name 'isolated_main' -Messages $isolatedMessages

$receipt = [ordered]@{
    generated_at = (Get-Date).ToString('o')
    model = $model
    fixed_task = $task
    evidence_chars = $evidence.Length
    direct_main = $direct
    search_subagent = $sub
    isolated_main = $isolated
    comparison = [ordered]@{
        direct_main_request_chars = $direct.request_chars
        isolated_main_request_chars = $isolated.request_chars
        direct_main_prompt_eval_count = $direct.prompt_eval_count
        isolated_main_prompt_eval_count = $isolated.prompt_eval_count
    }
}

New-Item -ItemType Directory -Force 'results' | Out-Null
$receipt | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 'results/d20-isolation-probe.json'

$receipt.comparison | Format-List
'--- direct main answer ---'
$direct.response.message.content
'--- isolated main answer ---'
$isolated.response.message.content
```

**准备观察什么**：

- `direct_main.request.messages` 是否逐字包含 `$evidence`。
- `search_subagent.request.messages` 是否包含同一份 `$evidence`。
- `isolated_main.request.messages` 是否只有任务和子 Agent 结论，而没有原始搜索证据。
- 两个主请求的 `request_chars` 与 `prompt_eval_count` 如何变化；两条答案是否都满足三项定位要求。
- `response.message.content` 是模型输出；不能用它反推请求边界，边界要直接看收据中的 request。

**真实证据**：第一次运行因当前 PowerShell 找不到 `rg`，`evidence_chars=0`，两个模型都回答“证据不足”；该次只能证明空证据路径，不能用于比较隔离效果。课堂随后把证据筛选改为 PowerShell 原生 `Select-String`，生成 4,963 字符、96 行证据，且 `_context_aware_summary`、`tool_content`、`messages=messages` 三个标记均存在，再重新运行并覆盖收据。

有效运行使用本地 Ollama `qwen3:8b`、`temperature=0`、`seed=20261105`。直接主请求为 5,518 字符、`prompt_eval_count=1319`；隔离主请求为 1,211 字符、`prompt_eval_count=491`。完整收据位于 `results/d20-isolation-probe.json`。

### 步骤 C：读取收据中的请求边界

收到步骤 B 的真实输出后才给本步骤。

**输入/读取**：只读取 `results/d20-isolation-probe.json` 中三次请求的消息角色、内容长度和服务端提示 token 计数。

**处理/筛选**：把每次请求展开为一行指标，不重跑模型；另检查原始证据里的一个独特标记是否出现在两个主请求中。

**输出/用途**：用于区分“子 Agent 看过”“主 Agent 看过”和“最终答案提到过”三个不同事实。

```powershell
$r = Get-Content -Raw -Encoding utf8 'results/d20-isolation-probe.json' | ConvertFrom-Json

@($r.direct_main, $r.search_subagent, $r.isolated_main) | ForEach-Object {
    [pscustomobject]@{
        Request = $_.name
        Roles = ($_.request.messages.role -join ' -> ')
        UserChars = ($_.request.messages | Where-Object role -eq 'user').content.Length
        PromptTokens = $_.prompt_eval_count
    }
} | Format-Table -AutoSize

$needle = '_context_aware_summary'
[pscustomobject]@{
    DirectMainHasRawMarker = (($r.direct_main.request.messages.content -join "`n") -match $needle)
    SubAgentHasRawMarker = (($r.search_subagent.request.messages.content -join "`n") -match $needle)
    IsolatedMainHasRawMarker = (($r.isolated_main.request.messages.content -join "`n") -match $needle)
} | Format-List
```

## 4. 逐段读真实轨迹

以下只在实际运行后填写；不把备课推断写成实测。

| 片段 | 真实摘录 / 证据字段 | 模型看见什么或决定什么 | Harness 做了什么 | 学员解释 |
|---|---|---|---|---|
| 直接主请求 | 5,518 字符；1,319 提示 token；原始标记 `True` | 完整源码筛选证据 | 把 4,963 字符原始证据装入主请求 | 原始证据进入了直接主上下文 |
| 子 Agent 请求 | 原始标记 `True` | 与直接路径相同的原始证据和三项任务 | 独立请求读取原始证据并返回结论 | 原始证据进入子上下文 |
| 隔离主请求 | 1,211 字符；491 提示 token；原始标记 `False` | 完整任务和子 Agent 结论 | 未把原始证据装入主请求 | 隔离发生在子 Agent Result 进入主请求的边界 |
| 两个最终回答 | 两者都覆盖三项定位，但含补造函数名 | 模型根据各自请求生成答案 | 保存响应，不以答对反推请求边界 | 请求隔离成功不等于结论无损或准确 |

证据结论按层次书写：

- **本地 Harness 构造**：收据保存了本地发出的三份 request body。
- **模型服务响应**：Ollama 对每份请求返回响应和 `prompt_eval_count`。
- **模型内部使用**：不能由请求体或正确答案直接证明每个 token 都被有效利用。
- **权威源码事实**：最终定位还要回到原文件核对，不能把模型回答当源码本身。

## 5. 追到关键源码

### 教材实验源码锚点

- `compression_strategies.py:100-129` 的 `compress_search_results`：根据策略选择原文、上下文感知摘要或其他表示。
- `compression_strategies.py:454-501` 的 `_context_aware_summary`：把查询、已有上下文和搜索结果拼成压缩请求。
- `agent.py:609-640`：工具执行后，Harness 选择 `compressed.content` 或原始 `result` 写入 `role=tool` 消息。
- `agent.py:450-468`：`messages` 被作为一个整体交给 `chat.completions.create()`。

### 本课最小 Harness 源码锚点

讲义步骤 B 的核心不是 PowerShell 语法，而是三份消息列表：

1. `directMessages`：主 Agent 的 user content 直接拼入 `$evidence`。
2. `subMessages`：独立模型请求读取 `$evidence` 并生成结论。
3. `isolatedMessages`：主 Agent 只拼入 `$sub.response.message.content`。

输入 → 处理 → 输出：固定源码证据 → 改变哪种表示进入主请求 → 保存请求体、token 旁证与回答。隔离成立与否由第三份请求里有没有原始证据判断，不由答案长度判断。

关键边界：PowerShell 进程仍持有 `$evidence`，所以本实验验证的是**模型请求上下文隔离**，不是操作系统内存隔离。实际多 Agent Harness 还可把子 Agent 放入独立进程或沙箱，但那不是本课核心。

课堂源码核验结论：`agent.py:622-624` 选择 `compressed.content`，`626-632` 是原文分支，`635-640` 把选中内容写入 `role=tool` 后追加到 `messages`；这段逻辑实际位于 `execute_research()`（第 511 行起），不是模型补造的 `_handle_tool_response` 或 `_process_tool_result`。`agent.py:450-468` 的真实函数名为 `_non_streaming_response`，第 462 行只证明本地 `messages` 被交给 API 客户端调用，不能单独证明服务端逐字收到或模型有效使用每段内容。

## 6. 单变量对照：子 Agent 的任务包是否自包含

- 唯一改变的条件：把子 Agent 的任务从完整三项验收标准改为“找出与压缩有关的代码，给我结论”。
- 保持不变：隔离架构、模型、原始源码证据、随机 seed、主 Agent 的完整任务和 system message。
- 对照前预测：含糊描述可能让子 Agent 的搜索目标或回传结果偏移；即使单次仍答对，也不能证明任务描述质量不重要。主 Agent 虽持有完整任务，但没有原始证据，不能可靠恢复未回传的源码事实。
- 真实结果：含糊子 Agent 返回 1,685 字符的泛化压缩概览，反而长于明确任务的 807 字符结论，却没有精确报告 `messages=messages` 的 API 调用位置和关键行号。含糊路径主 Agent 随后补造 `tool_call`、`generate_response`，并把 JSON 格式化位置误说成模型 API 位置。收据位于 `results/d20-brief-comparison.json`。

在完成基线轨迹和源码解释后才给下面命令：

```powershell
$vagueSubMessages = @(
    @{ role = 'system'; content = '你是搜索子 Agent。返回主 Agent 需要的结论。' }
    @{ role = 'user'; content = "找出与压缩有关的代码，给我结论。`n`n原始源码搜索证据：`n$evidence" }
)
$vagueSub = Invoke-LocalAgent -Name 'vague_search_subagent' -Messages $vagueSubMessages

$vagueMainMessages = @(
    @{ role = 'system'; content = $mainSystem }
    @{ role = 'user'; content = "$task`n`n子 Agent 回传：`n$($vagueSub.response.message.content)" }
)
$vagueMain = Invoke-LocalAgent -Name 'vague_brief_main' -Messages $vagueMainMessages

$comparisonReceipt = [ordered]@{
    generated_at = (Get-Date).ToString('o')
    fixed_model = $model
    fixed_evidence_chars = $evidence.Length
    complete_brief_subagent = $sub
    vague_brief_subagent = $vagueSub
    complete_brief_main = $isolated
    vague_brief_main = $vagueMain
}
$comparisonReceipt | ConvertTo-Json -Depth 20 |
    Set-Content -Encoding utf8 'results/d20-brief-comparison.json'

'--- complete brief / sub-agent ---'
$sub.response.message.content
'--- vague brief / sub-agent ---'
$vagueSub.response.message.content
'--- complete brief / main answer ---'
$isolated.response.message.content
'--- vague brief / main answer ---'
$vagueMain.response.message.content
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 明确任务保留三项结构和行号但仍补造函数名；含糊任务变成更长的概览并丢失第三项精确证据，主 Agent又新增补造内容 | 隔离使主 Agent只能在子 Agent Result 的信息边界内工作；完整任务可帮助发现缺项，却不能恢复未回传的源码事实 | 本次配对只展示这一任务和一次采样；仍需换任务或多次配对重复，不能推出含糊任务必然失败 |

## 7. 理解检查：每次只问一题

1. **概念复述**：压缩和子 Agent 隔离分别在哪个时间点处理大体积信息？主 Agent 的消息列表有什么不同？
2. **结果预测**：若子 Agent 看过完整源码但任务包没说明“必须报告调用点”，主 Agent 可能收到什么样的 Result？为什么主 Agent 不一定能自行补回？
3. **故障定位**：最终回答漏了一个调用点。应怎样分别检查原始搜索证据、子 Agent 请求、子 Agent 响应、主 Agent 请求和最终回答，才能判断丢失发生在哪一层？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 压缩是原始信息已进入某个上下文后的有损变换；隔离让原始信息只进入子请求，主请求从一开始只接收结果。是否隔离要查各请求 `messages`。
2. 子 Agent 可能只报告实现函数而遗漏调用点；隔离使主 Agent没有原始证据可重新检索。含糊任务不必然失败，但降低了结果契约的可检查性。
3. 先确认原始证据是否包含调用点，再确认子请求是否收到证据和验收标准，再检查子响应是否保留它，然后检查该响应是否原样进入主请求，最后才看主回答。不能由最终答错直接归因子 Agent 能力。

</details>

**本次检查结果**：

1. 概念复述：能说明压缩是信息进入上下文后的有损处理，隔离从请求边界阻止原始信息进入主上下文；能指出压缩可能删掉关键事实、隔离可能因任务包或 Result 不完整而偏移。通过。
2. 结果预测：最初把“独立请求历史”误等同于“Result 中的原始标记不会进入主消息”；经 Android Result `Intent` 和 `SECRET_X` 字符串拼接换例后，能区分运行上下文隔离与返回契约泄漏。通过。
3. 故障定位：能按原始证据→子请求→子响应→主请求→最终回答定位首次丢失，并区分子 Agent 已传错与主 Agent在缺证据时新补造。通过。

卡住时记录具体缺口，换一个“小页面读取大量日志，只回传错误码与证据行”的例子复查。实验、对照、源码和三项理解检查全部完成后才记 `✅`。

## 8. 可选 AI 代码演示

本课步骤 B 已是最小 Harness 演示，不再布置 Kotlin 作业。它使用 Ollama 的无状态聊天请求模拟主/子上下文边界；没有实现完整的子 Agent 生命周期、工具权限和并发调度，这些留到第四章与第十章。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能用实际 request body 判断原始证据进入了哪个 Agent；能区分独立请求历史、Result 内容越界、客户端调用参数、服务端收据和模型内部使用；能沿五层证据链定位信息丢失或补造。
- 尚未理解及证据：无遗留理解缺口。本次没有 HTTP 抓包或 Ollama 服务端逐字请求日志，因此不能把本地 request body 当成服务端逐字收据；单次明确/含糊对照不能建立普遍因果。
- 下次从哪一小步继续：Day 21 为 W3 buffer 日，只做本周闭环复盘、补欠账或休息，不上新课。
- 状态：✅。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-05；完成全课。
- 实验命令、模型、证据位置与必要真实摘录：本地 Ollama `qwen3:8b`；有效基线收据为 `results/d20-isolation-probe.json`，任务包对照为 `results/d20-brief-comparison.json`。原始证据 4,963 字符；直接/隔离主请求为 5,518/1,211 字符、1,319/491 提示 token；原始标记为直接主 `True`、子 Agent `True`、隔离主 `False`。
- 学员的原始预测与后续解释：预测隔离可缩短主请求但回答不一定更完整；能解释隔离发生在子 Agent Result 进入主请求的交接点。曾误判 Result 携带的 `SECRET_X` 不会出现在主消息，经字符串拼接换例后纠正为会进入。
- 三项理解检查：概念 通过 / 预测 通过（换例纠正后） / 定位 通过。
- 是否采用代码演示及原因：采用讲义内最小 Ollama Harness；不新增项目文件、不修改 `vendor/`。
- 本次观察、假设、待验证点：观察到请求隔离显著缩小本次主请求，但完整任务的子 Agent仍补造函数名；含糊任务返回更长却更不针对的概览，主 Agent无法恢复缺失证据并新增补造内容。假设自包含任务包和可检查 Result 契约能降低此类偏移；仍需多任务、多次配对及服务端请求收据验证。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-compression/SKILL.md`。
- 证据边界：保存本地 request body 和 Ollama response，仍不能证明每个 token 的内部使用；回答正确也不能替代请求边界检查。
- 进度处理：已在 `docs/progress.md` 追加 Day 20 `✅`；下一 Day 21 为 buffer 日。
