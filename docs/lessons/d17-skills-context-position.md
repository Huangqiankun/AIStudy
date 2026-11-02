# Day 17 — Skill 正文到底进入上下文的哪里

> 章节 `vendor/ai-agent-book/book/chapter2.md` § Skills 在上下文中的位置（第 814–832 行）
> 实验 `vendor/ai-agent-book/chapter2/agent-skills-ppt/`（只读消息布局探针；不作为模型自主路由证据）
> 备课日期 2026-11-02 ｜ 授课日期 2026-11-02 ｜ 状态 ✅

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：目录常驻，说明书在调用点加入

**本课只回答**：Skill 被选中后，完整正文在什么时候、以什么方式进入模型的下一轮上下文；为什么“追加到调用点”比“回头改写开头”更有利于复用已有前缀？

**类比**：把 Skills 看成 Android 的 Manifest 能力目录与 dynamic feature module。启动时只让进程看到 `name + description`，像先看到组件声明；任务命中后，Harness 才在当前调用点加载完整操作手册。类比边界是：Android 模块加载的是可执行字节码，而 Skill 正文是模型下一轮可读的上下文；具体用 `user` 还是 `tool` 角色承载，由运行时决定，不是 Skill 规范写死的。

```text
稳定开头                         只增不改的轨迹
┌────────────────────┐          ┌──────────────────────────────┐
│ system/developer 规则│          │ user 任务                     │
│ Skills 薄目录        │─────────▶│ assistant: 请求加载 pptx     │
│ 核心 tools           │          │ tool/user: 完整 SKILL.md     │
└────────────────────┘          │ assistant: 使用正文继续决策  │
                                └──────────────────────────────┘
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：构造一次最小 Skill 加载回合，并保存“加载前”和“加载后”的消息结构证据。
- 学员会亲自运行：先用教材 `demo.py` 的真实目录构建与 `read_skill` 分发函数生成只读收据；再保持任务、目录和 Skill 正文不变，只把“末尾追加正文”改成“回头改写开头”。
- 会得到的产物：控制台 JSON，包含加载前后角色顺序、调用 ID 配对、正文是否进入下一轮、旧前缀是否逐项保持不变等字段；不写入 `vendor/` 源码。
- 结束时要能回答：规范、具体 Harness 实现和模型自主行为分别需要什么证据；为什么“加载成功”仍不等于“模型已经使用正文完成任务”。

**确认问题**：如果运行时把完整 `SKILL.md` 作为 `tool` 消息追加，而另一个运行时把它作为 `user` 消息追加，是否只有其中一个才算 Skill 渐进式披露？先回答“是”或“否”，再说一句理由。

## 2. 实验前预测

- 任务与输入：固定读取本地 `skills/pptx/SKILL.md`，固定使用一条“把论文做成演示文稿”的任务文本。
- 固定条件：同一教材版本、同一 `scan_skill_catalog()`、`build_system_prompt()` 与 `dispatch()`；不调用在线模型，不生成 PPTX。
- 观察点：首次请求有哪些角色和内容；`read_skill` 的调用与结果 ID 是否配对；下一轮消息列表是否包含完整正文；加载前的消息前缀是否原样保留。
- 预测问题：在不改写已有消息、只在调用点追加 `assistant + tool` 两条消息时，哪些字段应能直接证明“旧前缀没变、正文已经可供下一轮请求使用”？
- 学员预测：追加式加载后正文应位于 `tool` 或 `user` 消息；调用 ID 与正文位置可证明配对和承载位置。经追问后补充：要证明旧前缀未改写，还需逐项比较加载前消息与加载后相同长度的前缀。

## 3. 运行实验

讲师备课已核对 `demo.py --help` 与关键函数签名。正式目标探针由学员在预测后亲自运行；探针会构造消息收据但不会向模型发送请求，因此不能证明模型自主选中了 `pptx`。

### 步骤 A：环境与只读输入核对

给命令前先说明：它只检查实验目录、Skill 入口和源码是否存在，并显示当前路径；不读取 Skill 正文、不构造消息、更不会调用模型。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\agent-skills-ppt'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
[pscustomobject]@{
    Location = (Get-Location).Path
    DemoExists = Test-Path -LiteralPath '.\demo.py'
    SkillExists = Test-Path -LiteralPath '.\skills\pptx\SKILL.md'
}
```

### 步骤 B：核对 CLI

收到步骤 A 输出后再给。本命令读取 `demo.py` 的参数定义，只核对当前教材版本支持的入口；帮助文本不是目标实验结果。

```powershell
python -m uv run --extra ch2 python demo.py --help
```

### 步骤 C：运行追加式加载探针

收到预测后再给。命令按下面的数据流工作：

1. 调用教材函数扫描薄目录并构造首次请求的 `system + user` 两条消息。
2. 复制这两条消息作为“加载前快照”。
3. 构造一个带固定调用 ID 的 `assistant.read_skill` 请求，并通过教材真实 `dispatch()` 读取完整 `SKILL.md`。
4. 把结果追加成配对的 `tool` 消息，最后只输出角色顺序、字符量、ID 配对、前缀是否不变及正文是否已进入下一轮等字段。

```powershell
$probe = @'
import json
from pathlib import Path
import demo

catalog = demo.scan_skill_catalog()
system_prompt = demo.build_system_prompt(catalog)
task = "make a presentation from a paper"
messages = [
    {"role": "system", "content": system_prompt},
    {"role": "user", "content": task},
]
before = [dict(message) for message in messages]
call_id = "call_d17"
messages.append({
    "role": "assistant",
    "content": None,
    "tool_calls": [{
        "id": call_id,
        "type": "function",
        "function": {
            "name": "read_skill",
            "arguments": json.dumps({"name": "pptx"}),
        },
    }],
})
skill_body = demo.dispatch(
    catalog, "read_skill", {"name": "pptx"}, Path("output/unused.pptx")
)
messages.append({
    "role": "tool",
    "tool_call_id": call_id,
    "content": skill_body,
})

receipt = {
    "before_roles": [message["role"] for message in before],
    "after_roles": [message["role"] for message in messages],
    "catalog_chars": len(system_prompt),
    "skill_chars": len(skill_body),
    "paired_call_id": (
        messages[-1]["tool_call_id"]
        == messages[-2]["tool_calls"][0]["id"]
    ),
    "prefix_unchanged": messages[:len(before)] == before,
    "next_request_contains_skill": (
        messages[-1]["content"] == skill_body
        and skill_body.startswith("---")
    ),
}
print(json.dumps(receipt, ensure_ascii=False, indent=2))
'@
$probe | python -m uv run --extra ch2 python -
```

**准备观察什么**：不要猜具体字符数；先核对角色序列和 ID，再分别判断“旧消息有没有变化”与“正文是否存在于下一轮将发送的消息列表”。`next_request_contains_skill=true` 也只证明 Harness 构造出的下一轮输入含正文，不能证明模型已经读懂或遵守了正文。

**真实证据（2026-11-02，学员 PowerShell 实跑）**：环境检查得到 `DemoExists=True`、`SkillExists=True`；`--help` 显示当前入口支持 `--paper`、`--output`、`--model`、`--max-turns` 与 `--offline`。追加探针先打印 `read_skill('pptx')` 加载完整正文 `1242` 字符，随后 JSON 显示：`before_roles=[system,user]`、`after_roles=[system,user,assistant,tool]`、`catalog_chars=506`、`skill_chars=1242`，且 `paired_call_id`、`prefix_unchanged`、`next_request_contains_skill` 均为 `true`。这证明本地 Harness 的调用配对、前缀保持和待发送列表含正文；由于探针没有发起 API 请求，不证明服务端接收、缓存命中或模型使用正文。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能直接证明什么 | 仍不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 首次请求 | `before_roles=[system,user]`，薄目录 506 字符 | 薄目录与任务的角色、顺序和体积 | 模型已选择 Skill | 学员确认首次只获得可发现目录与加载规则，不含完整流程 |
| 加载请求 | 人工构造 `assistant.read_skill`，ID 为 `call_d17` | Harness 探针构造了加载请求 | 该调用来自真实模型自主决策 | 学员能区分本地构造与模型调用 |
| 加载结果 | `skill_chars=1242`、`paired_call_id=true`，角色为 `tool` | ID 配对，正文以工具结果加入列表 | 模型已在后续回答中使用正文 | 学员指出正文读取与 ID 配对是不同层事实 |
| 下一轮输入 | `prefix_unchanged=true`、`next_request_contains_skill=true` | 原前缀逐项保持，正文位于待发送列表 | 服务端实际接收、模型遵守、任务完成 | 学员明确后者只证明 Harness 列表含正文，不能证明 LLM 收到 |

本探针的 `assistant` 调用由测试代码明确构造，必须标为 Harness 布局探针。若要建立真实模型证据链，还需另查模型实际请求、模型输出的调用、Harness 执行、工具结果、下一次真实 API 请求和最终任务状态。

## 5. 追到关键源码

**源码锚点**：

- `vendor/ai-agent-book/chapter2/agent-skills-ppt/demo.py:72-100`：薄目录只保留 `name + description` 并渲染到启动提示。
- `vendor/ai-agent-book/chapter2/agent-skills-ppt/demo.py:107-114`：`read_skill` 在被调用时才读取完整正文。
- `vendor/ai-agent-book/chapter2/agent-skills-ppt/demo.py:310-343`：首次消息、模型输出追加、工具执行和配对结果追加。

课堂分三小段读，总量约 35 行：

- 输入 → 处理 → 输出：磁盘 `SKILL.md` → Harness 先筛出薄目录 → 命中后读取全文 → 作为工具结果追加到 `messages`。
- 连接轨迹的关键行：`messages.append(msg...)` 保存调用，随后 `messages.append({role: tool, ...})` 保存结果；下一轮 `chat_with_tools(..., messages, ...)` 才会把累积列表再次发给模型。
- 实现边界：教材 demo 使用 `tool` 角色承载正文；Claude Code/Codex 等运行时可用 `user` 片段注入。Skill 规范约束“目录先可发现、正文选中后加载”，不统一规定 API 角色。
- 学员解释：能从 `catalog[name]` 只保存 `description` 与 `dir` 判断正文未进入薄目录；能指出 `return content` 只证明本地读取，还需追加工具消息并用增长后的 `messages` 发起下一次请求；若正文内容存在但调用 ID 错配，应定位到 Harness 的协议组装/结果配对层，而非文件读取层。

## 6. 单变量对照：追加正文 vs 改写开头

- 唯一改变的条件：完整 Skill 正文的注入策略。A 在调用点追加 `assistant + tool`；B 把正文拼回原 `system` 消息。
- 保持不变：同一任务、同一薄目录、同一 `SKILL.md` 字节、同一 Python 环境；都不调用模型。
- 对照前预测：待回答。

命令按下面的数据流工作：先构造完全相同的首次消息；再读取同一份 Skill 正文；分别生成追加版与改写版；最后比较首次消息是否仍是后续上下文的逐项前缀，并报告正文所在角色与启动时字符量。

```powershell
$comparison = @'
import json
from pathlib import Path
import demo

catalog = demo.scan_skill_catalog()
thin = demo.build_system_prompt(catalog)
task = "make a presentation from a paper"
initial = [
    {"role": "system", "content": thin},
    {"role": "user", "content": task},
]
body = demo.dispatch(
    catalog, "read_skill", {"name": "pptx"}, Path("output/unused.pptx")
)

progressive = [dict(message) for message in initial]
progressive.extend([
    {"role": "assistant", "content": None, "tool_calls": [{
        "id": "call_d17",
        "type": "function",
        "function": {"name": "read_skill", "arguments": "{\"name\":\"pptx\"}"},
    }]},
    {"role": "tool", "tool_call_id": "call_d17", "content": body},
])

eager_rewrite = [dict(message) for message in initial]
eager_rewrite[0] = {
    "role": "system",
    "content": thin + "\n\n== FULL SKILL ==\n" + body,
}

receipt = {
    "same_task": progressive[1] == eager_rewrite[1],
    "same_skill_body": progressive[-1]["content"] == body,
    "progressive_roles": [message["role"] for message in progressive],
    "eager_roles": [message["role"] for message in eager_rewrite],
    "progressive_preserves_initial_prefix": (
        progressive[:len(initial)] == initial
    ),
    "eager_preserves_initial_prefix": (
        eager_rewrite[:len(initial)] == initial
    ),
    "thin_startup_chars": len(thin),
    "eager_startup_chars": len(eager_rewrite[0]["content"]),
}
print(json.dumps(receipt, ensure_ascii=False, indent=2))
'@
$comparison | python -m uv run --extra ch2 python -
```

**真实轨迹与结果（2026-11-02，学员 PowerShell 实跑）**：`same_task=true`、`same_skill_body=true`，确认只改变注入策略；追加版角色为 `[system,user,assistant,tool]` 且 `progressive_preserves_initial_prefix=true`；改写版角色为 `[system,user]` 且 `eager_preserves_initial_prefix=false`；薄目录启动文本为 506 字符，正文写入 system 后为 1767 字符。字符量不等于 token 或账单；本地前缀相同也不直接证明服务端缓存命中。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 同任务、同正文下，追加版保持初始前缀，system 改写版不保持；启动字符量 506→1767 | 追加策略不需要改写已建立的消息前缀；改写开头会改变此前请求的前缀字节 | 服务端实际 KV Cache 命中率与成本仍需 provider usage/cache 指标或服务端遥测验证，布尔比较本身不能证明真实命中 |

若对照符合预测，只能得出结构层结论：追加版保持了本地消息前缀。不要把它扩大成“零成本”或“服务端一定命中”；正文首次加入仍要计算，具体缓存策略也由 provider 决定。

## 7. 理解检查：每次只问一题

1. **概念复述**：Skill 规范规定了什么时序，具体 Harness 又决定了哪些实现细节？
2. **结果预测**：若把完整正文拼进开头的 system 消息，能力会不会立即消失？前缀复用和启动成本会先发生什么结构变化？
3. **故障定位**：已看到 `read_skill` 成功返回全文，但最终 Agent 仍违反 Skill 规则；你下一步查哪个真实请求或状态，为什么不能直接怪加载器或模型？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 规范约束目录先可发现、正文选中后加载；Harness 决定目录包装、消息角色、激活工具、每轮重建方式和正文如何追加。
2. 能力未必消失，但首次请求更大；若在已有轨迹后回头改写开头，本地前缀不再相同，已有缓存可能失效。实际幅度需遥测。
3. 先查正文是否真的进入随后发给模型的请求，再查模型输出、Harness 执行与权威任务状态；工具返回成功不等于下一轮发送成功，更不等于模型遵守或业务完成。

</details>

卡住时记录具体缺口，换一个文档/表格 Skill 的小例子复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

**本次检查结果**：

1. 概念复述：首次把“模型请求加载”说成规范固定路径；经 `/pptx` 客户端显式激活换例后，能说明规范只约束目录先可发现、正文选中后加载，消息角色与激活方式由 Harness 决定。通过。
2. 结果预测：首次认为全量正文会破坏后续前缀复用；经“首次请求前已全量写入且以后不变”换例后，能区分启动成本增加与中途改写前缀，并保留服务端实际命中需遥测的边界。通过。
3. 故障定位：先提出检查正文是否追加进轨迹；经缩小题后，能依据“本地 messages 有正文、真实 API 请求无正文”把首错定位到 Harness 请求构造/发送层，而非 Skill 文件或模型。通过。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell here-string 内的 Python 仅调用教材现有函数并打印收据，不修改 `vendor/`，也不要求学员学习完整 Python 语法。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分规范时序与 Harness 角色/激活实现；能沿本地读取、消息追加、真实请求、模型输出和权威状态分层定位；能解释追加式加载的前缀优势及证据边界。
- 尚未理解及证据：无遗留理解缺口；本课探针未连接真实 provider，因此没有服务端接收或 KV Cache 命中遥测，不能把结构结论扩大为真实命中率结论。
- 下次从哪一小步继续：Day 18「Agent 状态栏」，观察动态状态为何放在上下文末尾，以及它与 Skill 正文同为追加信息但职责不同。
- 状态：✅

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-02；完成全课。
- 实验命令、模型、证据位置与必要真实摘录：无模型；学员运行两段 PowerShell here-string 只读探针。基线收据为 `system,user → system,user,assistant,tool`，三个布尔字段均为 true；对照收据显示追加版保持前缀、system 改写版不保持。
- 学员的原始预测与后续解释：起初用调用 ID 与正文位置解释所有证据；随后能分别说明 ID 配对、正文承载位置、前缀逐项相等及待发送列表含正文各自证明什么，并主动保留“尚未真实发送”的边界。
- 三项理解检查：概念 通过（换例复查） / 预测 通过（换例复查） / 定位 通过（缩小题复查）
- 是否采用代码演示及原因：采用只读消息收据探针；目的是把“磁盘读取、Harness 消息、下一轮请求、模型行为”拆成可核对阶段
- 本次观察、假设、待验证点：观察到教材 Harness 的追加版保留本地前缀，改写版改变 system；假设追加式布局更利于 KV Cache 复用；真实缓存命中、token 与成本仍待 provider 遥测验证。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`
- 进度处理：已在 `docs/progress.md` 追加 Day 17 `✅` 日志。
