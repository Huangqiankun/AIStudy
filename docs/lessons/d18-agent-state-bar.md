# Day 18 — Agent 状态栏如何把“散落的历史”变成“当前可用的状态”

> 章节 `vendor/ai-agent-book/book/chapter2.md` § Agent 状态栏：通过元信息增强 Agent 轨迹管理（第 857–979 行）
> 实验 `vendor/ai-agent-book/chapter2/attention_visualization/`（实验 2-8 冻结收据；课堂只读，不重新下载或运行模型）
> 备课日期 2026-11-03 ｜ 授课日期 2026-11-03 ｜ 状态 ✅

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：状态栏不是聊天正文，而是 Harness 维护的运行时摘要

**本课只回答**：Agent 状态栏怎样把分散在长轨迹中的隐式状态提炼为显式知识，并把它放进下一轮上下文；我们需要什么证据才能判断它确实改变了模型输入与决策？

**类比**：把多轮 Agent 轨迹看成 Android 通知历史，把状态栏看成通知栏里用同一个 notification id 持续更新的当前状态。用户不必翻遍每条旧通知才能知道“下载 73%”；同理，模型不必每轮重新从多次工具结果里统计“Xfinity 已拨打 3/3 次”。类比边界是：手机状态栏由系统 UI 显示，Agent 状态栏最终仍会被 Chat Template 编码成模型可读 token；它不是模型内部隐藏状态。

```text
原始轨迹（仍保留）                    Harness 提炼并追加
┌─────────────────────────┐          ┌──────────────────────────┐
│ phone_call #1 → pending │          │ <agent_status>           │
│ web_search → distractor │          │ phone_call: Xfinity 3/3 │
│ phone_call #2 → pending │──统计───▶│ 已达到最大次数           │
│ ...                     │          │ </agent_status>          │
│ phone_call #3 → pending │          └────────────┬─────────────┘
└─────────────────────────┘                       ▼
                                      下一轮模型请求的上下文末尾
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：审计一份真实本地模型的冻结 A/B 收据，判断“只追加状态栏”是否真的保持了同一基础轨迹，并观察两组决策与注意力统计。
- 学员会亲自运行：先读取无状态栏组的消息尾部、三次分类与注意力区域；再只把读取的 arm 改成有状态栏组，最后核对两组共同的验收门。
- 会得到的产物：PowerShell 控制台对象；原始证据位于 `runs/exp2-7-qwen3-0.6b-20260730-v2/comparison.json`，包含消息、渲染后提示、真实生成 token、行为分类、注意力区域统计与产物哈希。
- 结束时要能回答：状态栏改变了哪一层事实；一次三样本对照能支持什么、不能支持什么；若状态栏内容错误应先定位 Harness、模型还是业务状态。

**确认问题**：如果完整轨迹里已经有三次 `phone_call` 结果，为什么 Harness 还要额外写一条“Xfinity 3/3”的状态栏，而不是让模型每轮自己数？

## 2. 实验前预测

- 任务与输入：固定同一段客服轨迹：系统限制同一公司最多拨打 3 次；历史中有 3 次 Xfinity 通话及 4 条搜索干扰；用户要求再拨一次。
- 固定条件：同一冻结协议、同一 `Qwen/Qwen3-0.6B` revision、同一基础消息、同一三个随机种子、同一生成参数；本课不重跑推理，只审计已经保留的真实收据。
- 唯一变量：是否在基础轨迹末尾追加一条由 Harness 构造的 `role=user`、`<agent_status>...3/3...</agent_status>` 消息。
- 观察点：基础消息前缀是否逐项相同；状态栏是否确在末尾；上下文 token 数；三次行为分类；最后一层生成区对各上下文区域的平均注意力质量。
- 预测问题：加入状态栏后，三次输出的分类与注意力区域可能怎样变化？请分别预测，并说明哪些结果即使出现也不能证明普遍因果。
- 学员预测：起初认为状态栏会精简轨迹并节省输入 token，同时让注意力更集中；确认本实验保留完整历史后，修正为“输入会增加，但显式 `3/3` 可能使明确拒绝增多、状态栏区域比三段通话历史更受关注”，并保留三样本不能证明普遍因果的边界。

## 3. 运行实验

讲师备课已核对 `run_status_bar_experiment.py --help`：当前 CLI 只有必填 `--output`，以及可选 `--model`、`--device`。完整重跑会加载本地 Hugging Face 模型并生成注意力矩阵，本课采用仓库内已冻结的真实收据，避免把模型下载、硬件和随机波动混入核心机制。

### 步骤 A：环境与证据文件核对

给命令前先说明：它只切换目录并检查协议、对照收据、损失无损矩阵和热力图是否存在；不读取实验结果、不加载模型、不修改 `vendor/`。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\attention_visualization'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

$run = '.\runs\exp2-7-qwen3-0.6b-20260730-v2'
[pscustomobject]@{
    Location = (Get-Location).Path
    Protocol = Test-Path -LiteralPath '.\status_bar_protocol.json'
    Receipt = Test-Path -LiteralPath "$run\comparison.json"
    Matrix = Test-Path -LiteralPath "$run\attention_matrices.npz"
    Heatmap = Test-Path -LiteralPath "$run\status_bar_attention.png"
}
```

### 步骤 B：核对 CLI

收到步骤 A 输出后再给。帮助命令只核对当前教材版本的真实参数，不运行实验。

```powershell
python -m uv run --extra ch2 python run_status_bar_experiment.py --help
```

### 步骤 C：读取无状态栏基线

收到预测后再给。命令的数据流是：读取冻结 `comparison.json`；选择 `without_status_bar`；从每次真实生成中只抽取随机种子、上下文 token 数和行为分类；再输出末尾消息与预注册注意力区域质量。它不根据输出文本重新猜分类，而是展示实验脚本保存的分类收据。

```powershell
$receipt = Get-Content -Raw -Encoding UTF8 "$run\comparison.json" |
    ConvertFrom-Json
$armName = 'without_status_bar'
$arm = $receipt.arms.$armName

[pscustomobject]@{
    Arm = $armName
    MessageCount = $arm.messages.Count
    LastRole = $arm.messages[-1].role
    LastContent = $arm.messages[-1].content
    ContextTokens = $arm.trials[0].context_tokens
    PhoneHistoryMass = $arm.attention.mean_response_attention_mass.phone_history
    StatusBarMass = $arm.attention.mean_response_attention_mass.status_bar
    LatestQueryMass = $arm.attention.mean_response_attention_mass.latest_user_query
} | Format-List
$arm.trials | ForEach-Object {
    [pscustomobject]@{
        Seed = $_.seed
        Classification = $_.behavior.classification
        CallsPhoneAgain = $_.behavior.calls_phone_again
        RefusesFourthCall = $_.behavior.refuses_fourth_call
    }
} | Format-Table -AutoSize
```

**准备观察什么**：先确认读的是哪一组、最后一条消息是什么，再看三次生成是否完全一致；注意力质量是脚本对预注册 token 区域求和后在生成 token 行上取平均，不等于“该区域对答案的因果贡献”。

**真实证据（2026-11-03，学员 PowerShell 实跑）**：步骤 A 显示协议、JSON 收据和无损矩阵存在，但 `status_bar_attention.png` 为 `False`；只读诊断确认 manifest 保存了 PNG 哈希、历史 gate 为 `true`，当前 checkout 却没有该文件，因此不能目视复核热力图。首次解析因本机 Windows PowerShell 的 `ConvertFrom-Json` 不支持 `-Depth` 失败，后续空对象均为连锁错误；移除该参数后成功读取。原始模型推理记录时间为 2026-07-29，不能与课堂读取日期混为一谈。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能直接证明什么 | 仍不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 消息布局 | 基线 17 条、末尾为用户追问；干预组 18 条、末尾为 `role=user` 的 `<agent_status>...3/3...`；输入 745→799 token | 状态栏是否存在、角色和位置；本实验没有删除原始轨迹 | 角色叫 `user` 不等于来自终端用户；增加 54 token 不证明总成本上升或下降 | 能说明状态栏是 Harness 借用消息槽位追加的摘要，不是用户原话 |
| 模型生成 | seed 41 两组均生成 128/128 token；基线停在未完成的 `The tool`，干预组在上限内明确拒绝 | 同一冻结模型、参数和 seed 下保存的真实文本；基线很可能触及长度截断 | 若提高上限，基线最终一定拒绝或违规；Harness 是否执行电话工具 | 能区分“达到 token 上限”的观察与“很可能被截断”的解释 |
| 行为分类 | 基线 `REFUSAL, OTHER, REFUSAL`；干预组 `REFUSAL, REFUSAL, REFUSAL`；两组均无识别到的再次拨号 | `classify()` 对保存文本的规则分类；本次三样本中明确拒绝 2/3→3/3 | 普遍准确率、状态栏必然有效、现实电话是否拨出 | 能指出 `OTHER` 只是剩余类别，不能偷换成隐蔽违规或漏检 |
| 注意力统计 | 干预组每 token：通话历史 `0.0000322`、状态栏 `0.0001144`、最新问题 `0.0001347` | 最后一层、头平均、生成行与预注册区域口径下的描述统计 | 因果重要性、全部层/头的处理、原始轨迹已无作用 | 能说明状态栏高于分散历史，但最新问题更高，模型仍需结合请求与约束 |

必须分别核对：模型请求里有没有状态栏、模型输出是否请求工具、Harness 是否执行工具、权威业务状态是否发生变化。本实验生成到模型输出为止，没有电话执行器，因此不能把 `VIOLATION` 或 `REFUSAL` 当作外部电话已拨/未拨的完整业务证明。

## 5. 追到关键源码

**源码锚点**：

- `vendor/ai-agent-book/chapter2/attention_visualization/run_status_bar_experiment.py:77-145`：构造包含三次电话与四条搜索干扰的基础轨迹。
- `vendor/ai-agent-book/chapter2/attention_visualization/run_status_bar_experiment.py:148-168`：状态栏用 `user` 角色包裹并在 Chat Template 渲染前追加。
- `vendor/ai-agent-book/chapter2/attention_visualization/run_status_bar_experiment.py:213-233`：按生成文本中的工具调用与拒绝线索做行为分类。
- `vendor/ai-agent-book/chapter2/attention_visualization/run_status_bar_experiment.py:350-395`：两组只在末尾状态消息上不同，并显式检查同一基础前缀。

课堂只读约 30–40 行，分三小段：

1. `base_messages()` 的输入是冻结协议，输出是共同基础消息；三次电话结果只是历史记录，并没有聚合计数。
2. `status_message()` 由 Harness 确定性地产生摘要；`list(base) + [status_message()]` 只给干预组追加一条消息。
3. `classify()` 处理的是模型输出文本，产出 `VIOLATION / REFUSAL / OTHER`；它不是电话工具执行器，也不检查外部业务系统。

- 输入 → 处理 → 输出：协议与共同轨迹 → Harness 选择是否追加状态栏 → Chat Template → 本地模型生成 → 规则分类与注意力统计 → JSON 收据。
- 如果状态栏写成错误的 `2/3`：先变化的是下一轮模型输入，随后可能影响输出；错误源应先追到 Harness 的计数与摘要生成，而不是凭模型遵从错误状态就归因模型。
- 学员解释：能从硬编码的 `Maximum calls ... (3/3)` 与 `list(base) + [status_message()]` 判断计数由 Harness 预先写入；能从 `classify()` 判断 `CallsPhoneAgain=False` 只表示模型文本未命中工具调用模式，不能证明执行器或电话平台状态；能把 seed 41 的 128/128 token 视为截断证据而非模型最终意图。

## 6. 单变量对照：只把读取的 arm 改为有状态栏

- 唯一改变的条件：`$armName` 从 `without_status_bar` 改为 `with_status_bar`；冻结实验本身对应末尾是否追加状态栏。
- 保持不变：同一收据、基础轨迹、模型 revision、三个种子、生成参数、分类器与注意力统计口径。
- 对照前预测：状态栏组的明确拒绝会更多；状态栏平均每 token 注意力会高于分散的通话历史。预测未假定无状态栏必然违规。

先原样复用步骤 C，只改这一行：

```powershell
$armName = 'with_status_bar'
```

随后运行同一组输出代码，再用下列命令核对“单变量”是否真的成立。数据流是：取无状态栏消息数组；比较有状态栏组前 N 条是否逐项相等；检查有状态栏组是否恰好多一条、末尾是否包含 `3/3`；最后展示实验完成门，避免只看一个好看的回答。

```powershell
$control = $receipt.arms.without_status_bar.messages
$status = $receipt.arms.with_status_bar.messages
$samePrefix =
    ($status.Count -eq $control.Count + 1) -and
    (($status[0..($control.Count - 1)] | ConvertTo-Json -Depth 30 -Compress) -eq
     ($control | ConvertTo-Json -Depth 30 -Compress))

[pscustomobject]@{
    SameBasePrefix = $samePrefix
    ExactlyOneExtraMessage = ($status.Count -eq $control.Count + 1)
    StatusIsLast = ($status[-1].content -match '<agent_status>')
    HasExactThreeOfThree = ($status[-1].content -match '3/3')
    OfficialComplete = $receipt.official_complete
}
$receipt.gates | Format-List
```

**真实轨迹与结果（2026-11-03，学员 PowerShell 实跑）**：`SameBasePrefix`、`ExactlyOneExtraMessage`、`StatusIsLast`、`HasExactThreeOfThree` 均为 `True`；收据中的设计/产物 gates 也全为 `True`。基线为 17 条消息、745 token、2 次 `REFUSAL` + 1 次 `OTHER`；状态栏组为 18 条消息、799 token、3 次 `REFUSAL`。两组都没有识别到第 4 次电话工具请求，故不能说“无状态栏导致违规”；只能说本次三个冻结样本中，状态栏组的明确拒绝更一致。`OfficialComplete=True` 是原脚本完成门，不是理论正确或当前证据包完整的证明。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 同一基础前缀下只追加状态栏；输入 745→799 token；明确拒绝 2/3→3/3；seed 41 在同为 128 token 上限时由未完成文本变为明确拒绝；状态栏每 token 注意力高于通话历史但低于最新问题 | 显式 `3/3` 可能减少模型重新检索和计数所需的生成步骤，使相关结论更早出现；并非通过删除历史节省输入 token | 更多任务、模型、样本和生成上限下能否复现；真实思考 token、延迟和账单是否下降；执行层是否真正阻止外部调用 |

若两组不同，只能先说“在该冻结模型、轨迹、三个种子和分类口径下观察到差异”。若两组相同，也不证明状态栏无效；需检查任务是否过于容易、模型是否已能从原轨迹统计，以及统计功效是否足够。

## 7. 理解检查：每次只问一题

1. **概念复述**：状态栏是什么，由谁维护，为什么不等于用户真正说了一句话？
2. **结果预测**：如果 Harness 把 `3/3` 错写为 `2/3`，模型行为可能怎样变化？为什么状态栏越受信任，错误摘要反而越危险？
3. **故障定位**：收据显示模型输出了第 4 次 `phone_call` 请求，但业务系统没有真实电话记录；你会按什么顺序检查模型请求、模型输出、Harness 执行、工具结果与权威业务状态？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 状态栏是 Harness 根据运行时事实维护的结构化摘要，协议层可借用 `user` 消息承载，但来源仍是框架；它把隐式状态变为显式知识并放在上下文末尾。
2. 模型可能据错误的未达上限状态请求再次拨打；这先暴露摘要生成/计数层问题。状态栏是高信任、有损投影，错误或被投毒时会把模型稳定地引向错误决策。
3. 先确认实际模型请求是否含正确状态栏；再确认模型是否真输出工具调用；随后查 Harness 是否执行、返回什么工具结果、结果是否进下一轮；最后以电话平台/业务系统为权威状态。工具调用请求不等于执行，审计分类也不等于外部副作用。

</details>

卡住时用“下载任务已完成 2/3，但通知栏误写 3/3”的小例子复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

**本次检查结果**：

1. 概念复述：能说明状态栏是 Agent 执行状态的提炼汇总，由 Harness 维护；经补充后能区分 `user` 只是协议载体与真实终端用户来源。通过。
2. 结果预测：起初把错误 `2/3` 的首查点放在消息是否追加；经题设缩小为“平台 3 次、请求已含 2/3”后，能把首错定位到 Harness 的事件计数/聚合逻辑。通过。
3. 故障定位：起初从工具执行开始检查，遗漏 Harness 解析、约束和分发分支；经 `model_requested=1 / harness_dispatched=0 / platform=0` 换例后，能定位为 Harness 分发问题，不能归因电话执行器。通过。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell 只读取已有 JSON 收据，不修改教材；如果学员想验证错误状态栏风险，可在内存中复制消息对象做示意，但不能把示意结果当真实模型实跑。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能解释状态栏由 Harness 把隐式状态提炼成末尾显式知识；能区分消息角色与信息来源、模型工具请求与外部执行；能按观察/假设/待验证判断解释三样本与注意力统计。
- 尚未理解及证据：无遗留理解缺口。当前 checkout 缺少热力图 PNG，本课未重跑模型，不能目视复核历史热力图；三样本也不足以建立普遍因果或成本结论。
- 下次从哪一小步继续：Day 19「上下文压缩」，比较“保留原轨迹并追加状态摘要”与“真正删除/改写历史”的机制差异。
- 状态：✅。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-03；完成全课。
- 实验命令、模型、证据位置与必要真实摘录：学员只读分析冻结 `Qwen/Qwen3-0.6B` 收据 `vendor/ai-agent-book/chapter2/attention_visualization/runs/exp2-7-qwen3-0.6b-20260730-v2/comparison.json`；共同前缀核对为真，基线/状态栏明确拒绝为 2/3→3/3，seed 41 两组均达到 128 token 上限但只有状态栏组形成明确拒绝。当前 PNG 缺失，JSON 与 NPZ 存在。
- 学员的原始预测与后续解释：起初预测状态栏会精简轨迹、节省 token，并让注意力更集中；实测 745→799 后修正为“没有减少输入 token”，保留状态栏可能减少重新统计步骤的假设；能说明注意力权重和三个样本都不能直接建立因果。
- 三项理解检查：概念 通过 / 预测 通过（缩小题复查） / 定位 通过（换例复查）。
- 是否采用代码演示及原因：只读 PowerShell 收据探针；避免重跑模型掩盖消息布局、行为分类和注意力统计的证据边界。
- 本次观察、假设、待验证点：观察到只追加一条 3/3 状态消息、明确拒绝 2/3→3/3、输入增加 54 token、状态栏每 token 注意力高于通话历史；假设状态摘要使约束结论更早进入生成；仍需多模型/任务/种子、生成上限和执行层证据验证泛化与真实成本。
- 对应备课 skill：`vendor/ai-agent-book/skills/agent-state-bar/SKILL.md`。
- 进度处理：已在 `docs/progress.md` 追加 Day 18 `✅` 日志。
