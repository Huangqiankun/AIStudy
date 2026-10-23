# Day 09 — 带工具调用的多轮为什么必须完整配对

> 章节 `vendor/ai-agent-book/book/chapter2.md` § 带工具调用的多轮交互（第 95–249 行）
> 实验 `vendor/ai-agent-book/chapter2/local_llm_serving/`
> 备课日期 2026-10-23 ｜ 授课日期待开展 ｜ 状态待开展

本文件是分步教学材料，不一次性贴入对话。正式实验和对照均由学员在预测后亲自运行；讲师只核实环境、CLI、源码和命令语法。

## 1. 建立概念：第二次请求不是“续上连接”，而是重放证据链

**本课只回答**：模型 API 本身无状态时，Harness 怎样把一次工具调用变成模型下一轮可用、可归属的观察？

**类比**：把一次工具调用看成 Android Binder 事务。`assistant.tool_calls` 像发出的 `transact()`，`tool` 消息像 reply Parcel，`tool_call_id` 像事务 token。Harness 不只要保存“返回值文本”，还要让返回值与原调用正确配对；否则即使工具确实执行过，也不能据此证明模型下一轮收到了一条协议完整的工具观察。类比边界是：Binder 驱动维护事务状态，而无状态模型 API 要求 Harness 在下一次请求里重新提交完整消息历史。

```text
第 1 次模型请求
system + user + tools
        │
        ▼
assistant.tool_calls(id = call_x)   ← 模型决策
        │
        ▼
Harness 执行工具
        │
        ▼
第 2 次模型请求
system + user + assistant.tool_calls(call_x) + tool(tool_call_id = call_x) + tools
        │
        ▼
assistant 最终回答，或继续发出 tool_calls
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：让本地 `qwen3:8b` 使用确定性的汇率工具完成一次换算。
- 学员会亲自运行：先捕获同一次任务里的两次真实模型请求；再复用第二次请求，只把工具结果的 `tool_call_id` 改成一个不存在的 ID 后重放。
- 会得到的证据：`lesson-d09-baseline.json`、`lesson-d09-id-mismatch.json`，其中包含每次请求的角色序列、模型返回的工具调用、Harness 写入的结果及最终响应或协议错误。
- 结束时能回答：为什么第二次调用必须重放第一轮 `assistant.tool_calls`，`tool_call_id` 证明什么，以及“工具执行成功”和“模型收到有效观察”为何是两件事。

**确认问题**：Harness 已经成功执行 `convert_currency`，也把正确数值文本放进第二次请求，但 `tool_call_id` 指向一个不存在的调用。你认为“工具执行成功”是否足以证明模型收到了一条协议完整的工具观察？为什么？

**学员回答**：不是一条完整的工具观察，因为 `assistant.tool_calls` 属于模型请求调用工具的记录；没有保留它，工具观察就不完整。讲师补充：缺失的不只是历史文本，还包括工具结果归属于哪次模型调用的协议依据。

## 2. 实验前预测

- 任务与输入：系统规则要求汇率换算必须调用工具；用户要求把 100 美元换算成人民币。
- 固定条件：Ollama、`qwen3:8b`、OpenAI 兼容端点、温度 `0`、同一任务、同一工具定义和同一工具结果文本。
- 基线观察点：两次模型请求各自的 `messages`；第一次响应中的 `assistant.tool_calls[*].id`；第二次请求中对应的 `tool.tool_call_id`；最终回答。
- 对照唯一变量：第二次请求中工具结果的 `tool_call_id`，由真实 ID 改为 `<真实ID>-mismatch`。
- 预测问题：对照会在 API 层拒绝、被模型继续处理，还是产生其他行为？无论结果是哪一种，哪些事实可以证明，哪些因果不能由单次运行推出？
- 学员预测：端点会继续生成；模型会再次请求工具调用，Harness 完成新调用并形成正确匹配的 ID，随后模型使用对应结果生成回答。待分别用 API 响应、新 `tool_calls`、执行记录和下一轮消息验证，不能只看最终文本。

## 3. 运行实验

### 步骤 A：环境准备

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\local_llm_serving'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

### 步骤 B：核对 CLI

```powershell
python -m uv run --extra ch2 python main.py --help
```

备课核实的 CLI 提供 `--mode {single,interactive}`、`--task`、`--backend {vllm,ollama,auto}`、`--stream` 与 `--no-stream`。本课需要保存每次 API 请求，使用同目录的 `OllamaOpenAICompatible` 构造探针。

### 步骤 C：运行基线

以下是一个 PowerShell 步骤；Base64 只用于避免 PowerShell 破坏中文和 Python 引号。

```powershell
$probe = @'
from copy import deepcopy
from pathlib import Path
import json
from ollama_native import OllamaOpenAICompatible

agent = OllamaOpenAICompatible(model="qwen3:8b")
agent.conversation_history.append({
    "role": "system",
    "content": "你是汇率助手。用户要求换算货币时必须调用 convert_currency，不能凭记忆编造汇率。",
})

real_create = agent.client.chat.completions.create
api_turns = []

def captured_create(**kwargs):
    request = deepcopy(kwargs)
    response = real_create(**kwargs)
    api_turns.append({
        "request": request,
        "response": response.choices[0].message.model_dump(exclude_none=True),
    })
    return response

agent.client.chat.completions.create = captured_create
answer = agent.chat("使用汇率工具把 100 美元换算成人民币。", temperature=0)

payload = {
    "model": agent.model,
    "answer": answer,
    "api_turns": api_turns,
    "conversation_history": agent.conversation_history,
}
Path("lesson-d09-baseline.json").write_text(
    json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
)
summary = {
    "answer": answer,
    "request_roles": [
        [message.get("role") for message in turn["request"]["messages"]]
        for turn in api_turns
    ],
    "tool_call_ids": [
        call["id"]
        for turn in api_turns
        for call in turn["response"].get("tool_calls", [])
    ],
    "tool_result_ids": [
        message.get("tool_call_id")
        for message in agent.conversation_history
        if message.get("role") == "tool"
    ],
}
print(json.dumps(summary, ensure_ascii=False, indent=2))
'@
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch2 python -c "import base64; exec(base64.b64decode('$encoded').decode('utf-8'))"
```

**准备观察什么**：不预设模型一定调用工具，也不预设恰好两次请求。先看 `api_turns` 的真实数量；若出现工具调用，再核对下一次请求是否同时包含原 `assistant.tool_calls` 与匹配的 `tool` 结果。若模型没有调用工具，也保留真实轨迹并分析，不重复抽样挑结果。

### 步骤 D：运行单变量 ID 对照

只在基线确实产生第二次请求且包含 `tool` 消息后运行。脚本完整复用第二次请求，只改第一条工具结果的 `tool_call_id`，并保存响应或错误。

```powershell
$probe = @'
from copy import deepcopy
from pathlib import Path
import json
from openai import OpenAI

baseline = json.loads(Path("lesson-d09-baseline.json").read_text(encoding="utf-8"))
request = deepcopy(baseline["api_turns"][1]["request"])
tool_message = next(message for message in request["messages"] if message.get("role") == "tool")
original_id = tool_message["tool_call_id"]
tool_message["tool_call_id"] = original_id + "-mismatch"

record = {
    "model": baseline["model"],
    "changed_field": "first tool message tool_call_id",
    "original_id": original_id,
    "changed_id": tool_message["tool_call_id"],
    "request": request,
}
client = OpenAI(base_url="http://localhost:11434/v1", api_key="ollama")
try:
    response = client.chat.completions.create(**request)
    record["response"] = response.choices[0].message.model_dump(exclude_none=True)
except Exception as exc:
    record["error"] = {"type": type(exc).__name__, "message": str(exc)}

Path("lesson-d09-id-mismatch.json").write_text(
    json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(record, ensure_ascii=False, indent=2))
'@
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch2 python -c "import base64; exec(base64.b64decode('$encoded').decode('utf-8'))"
```

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。以下区域只在实际运行后填写；不把教材示例或预期当成本次实测。

| 片段 | 真实摘录 / 证据字段 | 模型决定什么 | Harness 执行或组装什么 | 学员解释 |
|---|---|---|---|---|
| 第一次请求 | 待运行 | 待观察 | 发送初始消息与工具定义 | 待回答 |
| 工具调用响应 | 待运行 | 待观察 | 保存 `assistant.tool_calls` | 待回答 |
| 工具执行 | 待运行 | 不适用 | 执行工具并生成 `tool` 消息 | 待回答 |
| 第二次请求 | 待运行 | 待观察 | 重放历史与匹配结果 | 待回答 |
| 最终响应 | 待运行 | 待观察 | 保存模型输出 | 待回答 |

分别核对工具是否执行、结果是否以匹配 ID 进入下一次请求、模型最后输出什么。正确数值、HTTP 200 或程序正常结束都不能替代前两项证据。

## 5. 追到关键源码

**教材锚点**：`vendor/ai-agent-book/book/chapter2.md:187-249` 与 `300-368`。

**实现锚点**：`vendor/ai-agent-book/chapter2/local_llm_serving/ollama_native.py:421-522` 的 `OllamaOpenAICompatible.chat`。

- 第 426–443 行：Harness 先把用户消息追加进历史，再把当前完整历史和工具定义发给模型。
- 第 451–465 行：模型的工具调用响应以 `assistant.tool_calls` 原样进入历史，保留调用 ID、工具名和参数。
- 第 467–495 行：Harness 解析参数、执行工具，并用 `tool_call_id = tool_call.id` 写入结果。
- 第 497–503 行：Harness 把已经增长的完整历史再次发给无状态模型，同时仍保留工具定义。
- 第 507–521 行：最终模型消息进入历史；是否存在 `tool_calls` 决定它是当前实现的结束输出还是普通文本响应。

关键片段按课堂节奏分小段阅读，总量控制在 20–40 行。Python 只解释列表追加、函数调用和字段配对，不延伸语法教学。

## 6. 单变量对照

- 唯一改变：第二次请求中第一条 `tool.tool_call_id`。
- 保持不变：模型、温度、消息顺序、调用请求、工具结果内容、顶层工具定义以及其他字段。
- 对照前预测：端点会接受错配；模型会再次请求工具，Harness 执行后生成一组正确配对的调用与结果，最后再使用新结果。
- 真实轨迹与结果：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据请求或错误证据讨论 | 待提出重复或跨 provider 验证方法 |

先核对保存文件里的唯一差异，再解释行为。一次对照只证明本次端点与模型的实际响应；不能据此宣称所有 provider 都会拒绝或容忍错配。

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么模型 API 无状态，却仍能在第二次调用中利用第一次工具结果？
2. **结果预测**：如果工具结果文本完全正确，但 `tool_call_id` 与前一条 `assistant.tool_calls[*].id` 不匹配，哪些事实仍成立，哪项证据链断了？
3. **故障定位**：工具日志显示成功，但下一次请求没有匹配的 `assistant.tool_calls` 或 `tool` 消息；应先查模型、Harness 还是工具？具体核对哪些字段？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 不是模型保存了会话，而是 Harness 在下一次请求中重放 system、user、先前 assistant 决策和 tool 结果。
2. 工具可能确实执行过、结果文本也可能正确；但“该结果属于前一调用且作为协议完整观察进入下一轮”的关联证据失效。接口拒绝或模型仍回答都不能反向修复关联。
3. 先查 Harness 的轨迹保存与下一次请求组装；核对调用 ID、结果 ID、消息顺序、结果内容和实际发出的 request。工具日志只证明执行层，不能证明返回通道完整。

</details>

卡住时记录具体缺口，换一个最小 Binder token 配对例子补讲，再检查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课探针由讲师准备，学员负责预测、运行和解释，不要求修改 Python 或 vendor 源码。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：待运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：使用讲义内最小探针保存两次请求；不布置编程作业
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 09 日志；仅备课不追加上课日志。
