# Day 20 — 隔离优于压缩：怎样让大结果不进入主上下文

> 教材 `vendor/ai-agent-book/book/chapter2.md` § 隔离优于压缩：子 Agent 上下文隔离（第 1089–1095 行）
> 关联实验 `vendor/ai-agent-book/chapter2/context-compression/`；本课最小隔离探针写在本讲义中，不修改 `vendor/`
> 备课日期 2026-11-05 ｜ 授课日期 待开展 ｜ 状态 待开展

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

- 任务与输入：同一个源码定位任务、同一份由 `rg` 生成的源码证据。
- 固定条件：本地 Ollama、`qwen3:8b`、`temperature=0`、`seed=20261105`、相同主 Agent system message。
- 唯一架构变化：主 Agent 的 user message 携带原始搜索证据，或只携带子 Agent 结论。
- 观察点：三次本地请求各自的 `messages`、请求字符数、`prompt_eval_count`、响应正文；不能只看最终答案。
- 预测问题：哪一次请求会包含原始源码证据？两条主 Agent 路径的请求体积可能怎样变化？回答完整性是否一定同步变化？
- 学员预测：待回答。

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

真实输出：待学员运行并贴回。

### 步骤 B：生成固定证据并运行两条路径

在完成预测后才给本步骤。

**输入/读取**：`agent.py` 和 `compression_strategies.py`；固定任务要求定位实现函数、工具结果选取点及 `messages` 进入 API 的位置。

**处理/筛选**：

1. `rg` 用固定关键词和上下文行生成同一份源码证据。
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
$evidence = (& rg -n -C 2 $pattern agent.py compression_strategies.py | Out-String).Trim()

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

**真实证据**：待学员运行。记录完整命令、退出状态、实际模型、控制台输出和产物路径；不预设两条答案谁更好。

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
| 直接主请求 | 待运行 | 待观察 | 把原始 `rg` 证据装入主请求 | 待回答 |
| 子 Agent 请求 | 待运行 | 待观察 | 独立请求读取相同原始证据 | 待回答 |
| 隔离主请求 | 待运行 | 待观察 | 只装入子 Agent 返回内容 | 待回答 |
| 两个最终回答 | 待运行 | 待观察 | 保存响应，不以答对反推请求边界 | 待回答 |

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

学员解释：待回答。

## 6. 单变量对照：子 Agent 的任务包是否自包含

- 唯一改变的条件：把子 Agent 的任务从完整三项验收标准改为“找出与压缩有关的代码，给我结论”。
- 保持不变：隔离架构、模型、原始源码证据、随机 seed、主 Agent 的完整任务和 system message。
- 对照前预测：含糊任务是否一定导致错误？若答案仍正确，能证明任务描述质量不重要吗？待回答。
- 真实结果：待运行。

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
| 待运行后填写 | 待根据请求和响应讨论 | 需配对重复或换任务验证，不能由单次差异推出必然因果 |

## 7. 理解检查：每次只问一题

1. **概念复述**：压缩和子 Agent 隔离分别在哪个时间点处理大体积信息？主 Agent 的消息列表有什么不同？
2. **结果预测**：若子 Agent 看过完整源码但任务包没说明“必须报告调用点”，主 Agent 可能收到什么样的 Result？为什么主 Agent 不一定能自行补回？
3. **故障定位**：最终回答漏了一个调用点。应怎样分别检查原始搜索证据、子 Agent 请求、子 Agent 响应、主 Agent 请求和最终回答，才能判断丢失发生在哪一层？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 压缩是原始信息已进入某个上下文后的有损变换；隔离让原始信息只进入子请求，主请求从一开始只接收结果。是否隔离要查各请求 `messages`。
2. 子 Agent 可能只报告实现函数而遗漏调用点；隔离使主 Agent没有原始证据可重新检索。含糊任务不必然失败，但降低了结果契约的可检查性。
3. 先确认原始证据是否包含调用点，再确认子请求是否收到证据和验收标准，再检查子响应是否保留它，然后检查该响应是否原样进入主请求，最后才看主回答。不能由最终答错直接归因子 Agent 能力。

</details>

卡住时记录具体缺口，换一个“小页面读取大量日志，只回传错误码与证据行”的例子复查。实验、对照、源码和三项理解检查全部完成后才记 `✅`。

## 8. 可选 AI 代码演示

本课步骤 B 已是最小 Harness 演示，不再布置 Kotlin 作业。它使用 Ollama 的无状态聊天请求模拟主/子上下文边界；没有实现完整的子 Agent 生命周期、工具权限和并发调度，这些留到第四章与第十章。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展。
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展。
- 实验命令、模型、证据位置与必要真实摘录：待运行。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 是否采用代码演示及原因：采用讲义内最小 Ollama Harness；不新增项目文件、不修改 `vendor/`。
- 本次观察、假设、待验证点：待开展。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-compression/SKILL.md`。
- 证据边界：保存本地 request body 和 Ollama response，仍不能证明每个 token 的内部使用；回答正确也不能替代请求边界检查。
- 进度处理：授课结束或中断后在 `docs/progress.md` 追加 Day 20 日志；仅备课不追加。
