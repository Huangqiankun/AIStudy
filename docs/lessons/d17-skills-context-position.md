# Day 17 — Skill 正文到底进入上下文的哪里

> 章节 `vendor/ai-agent-book/book/chapter2.md` § Skills 在上下文中的位置（第 814–832 行）
> 实验 `vendor/ai-agent-book/chapter2/agent-skills-ppt/`（只读消息布局探针；不作为模型自主路由证据）
> 备课日期 2026-11-02 ｜ 授课日期 待开展 ｜ 状态 待开展

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
- 学员预测：待回答。

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
task = "把论文做成演示文稿"
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

**真实证据**：待学员运行并贴回输出。记录命令、退出状态和字段值；若日志先打印第二层加载提示，再出现 JSON，要把两者分别解释为本地分发日志与消息收据。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能直接证明什么 | 仍不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 首次请求 | 待运行 | 薄目录与任务的角色、顺序和体积 | 模型已选择 Skill | 待回答 |
| 加载请求 | 待运行 | 构造了 `assistant.read_skill` 调用 | 该调用来自真实模型自主决策 | 待回答 |
| 加载结果 | 待运行 | ID 配对，正文以 `tool` 结果加入列表 | 模型已在后续回答中使用正文 | 待回答 |
| 下一轮输入 | 待运行 | 原前缀是否逐项保持、正文是否在待发送列表 | 服务端实际接收、模型遵守、任务完成 | 待回答 |

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
- 学员解释：待回答。

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
task = "把论文做成演示文稿"
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
    "content": thin + "\n\n== 完整 Skill ==\n" + body,
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

**真实轨迹与结果**：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 追加策略不需要改写已建立的消息前缀；改写开头会改变此前请求的前缀字节 | 服务端实际 KV Cache 命中率与成本仍需 provider usage/cache 指标或服务端遥测验证，布尔比较本身不能证明真实命中 |

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

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell here-string 内的 Python 仅调用教材现有函数并打印收据，不修改 `vendor/`，也不要求学员学习完整 Python 语法。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：无模型；待学员运行消息布局探针并贴回收据
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用只读消息收据探针；目的是把“磁盘读取、Harness 消息、下一轮请求、模型行为”拆成可核对阶段
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 17 日志；仅备课不追加上课日志。
