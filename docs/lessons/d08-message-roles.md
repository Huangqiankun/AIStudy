# Day 08 — 四种消息角色为什么不能混用

> 章节 `vendor/ai-agent-book/book/chapter2.md` § Agent 如何调用大模型：理解 API 的上下文结构（第 41–56 行）
> 实验 `vendor/ai-agent-book/chapter2/local_llm_serving/`
> 备课日期 2026-10-22 ｜ 授课日期 2026-10-22 ｜ 状态 ▶

本文件是分步教学材料，不一次性贴入对话。正式实验和对照均由学员在预测后亲自运行；讲师只核实环境、CLI、源码和命令语法。

## 1. 建立概念：role 是来源与语义边界

**本课只回答**：`system`、`user`、`assistant`、`tool` 分别是谁写入的；为什么相同文本换一个 `role`，对模型就不再是同一种证据？

**类比**：把一次 Agent 请求看成 Android Binder 调用。`role` 不像日志标签，更像 Parcel 的方向与事务身份：调用方请求、服务端先前回复、框架执行结果各有来源；把 reply Parcel 冒充成新的调用方请求，字节可以完全相同，但协议语义已经变了。类比边界是：模型 API 的消息列表会整体重放给无状态模型，而 Binder 事务本身不会这样重建整段历史。

**最小概念**：

- `system`：开发者写入的身份、规则和约束。
- `user`：终端用户提出的任务或补充输入。
- `assistant`：模型先前生成的文本或工具调用请求。
- `tool`：Harness 执行工具后写回的结果；在 OpenAI 兼容协议中用 `tool_call_id` 与调用请求配对。
- `tools`：请求顶层的工具定义，不是第五种消息角色。

```text
开发者 ──system──────────────┐
用户   ──user────────────────┤
模型   ──assistant/tool_calls├─> messages + 顶层 tools ─> 模型
Harness──tool/result─────────┘
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：让本地 `qwen3:8b` 使用确定性的汇率工具换算金额，并保存完整消息列表。
- 学员会亲自运行：先跑标准四角色基线；再固定第一次模型决策与工具结果，只把结果消息的 `role` 从 `tool` 改成 `user` 后重放第二次请求。
- 会得到的产物：`lesson-d08-baseline.json`、`lesson-d08-role-user.json`，以及控制台的角色序列、工具调用和最终响应／协议错误。
- 结束时要能回答：是谁产生每种消息、顶层 `tools` 与 `tool` 消息有何区别、角色错标后应从哪一层定位。

**确认问题**：如果 Harness 已经算出汇率结果，却把它以 `role=user` 追加回消息列表，那么“工具确实执行过”是否足以证明模型收到的是一条工具结果？为什么？

## 2. 实验前预测

- 任务与输入：系统规则要求使用工具；用户要求“使用汇率工具把 100 美元换算成人民币”。
- 固定条件：Ollama、`qwen3:8b`、温度 `0`、同一组工具 schema、同一条第一次 `assistant.tool_calls`、同一个已执行工具结果。
- 基线观察点：最终保存的 `messages[*].role`、`assistant.tool_calls`、`tool` 消息内容、顶层 `tools` 数量、最终回答。
- 对照唯一变量：重放第二次请求前，只把工具结果消息的 `role` 从 `tool` 改为 `user`；不重新采样第一次决策，不重新执行工具。
- 预测问题：对照请求会被协议拒绝，还是仍能生成答案？若能生成，能否仅凭答案正确就认定角色可以混用？说明理由。
- 学员预测：待回答。

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

备课实测的 CLI 提供 `--mode {single,interactive}`、`--task`、`--backend {vllm,ollama,auto}`、`--stream` 与 `--no-stream`。本课为保存消息级证据，使用同目录模块构造 Agent，而不是只看 CLI 的终端摘要。

### 步骤 C：运行基线

以下是一个 PowerShell 步骤；Base64 只用于避免 PowerShell 把中文与 Python 引号改坏。

```powershell
$probe = @'
from pathlib import Path
import json
from ollama_native import OllamaNativeAgent

agent = OllamaNativeAgent(model="qwen3:8b")
agent.conversation_history.append({
    "role": "system",
    "content": "你是汇率助手。用户要求换算货币时必须调用 convert_currency，不能凭记忆编造汇率。"
})
answer = agent.chat("使用汇率工具把 100 美元换算成人民币。", stream=False, temperature=0)
payload = {
    "model": agent.model,
    "answer": answer,
    "messages": agent.conversation_history,
    "tools": agent._convert_tools_to_ollama_format(),
}
Path("lesson-d08-baseline.json").write_text(
    json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
)
summary = {
    "answer": answer,
    "roles": [m.get("role") for m in payload["messages"]],
    "tool_calls": [m.get("tool_calls") for m in payload["messages"] if m.get("tool_calls")],
    "tool_messages": [m for m in payload["messages"] if m.get("role") == "tool"],
    "tools_count": len(payload["tools"]),
}
print(json.dumps(summary, ensure_ascii=False, indent=2))
'@
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch2 python -c "import base64; exec(base64.b64decode('$encoded').decode('utf-8'))"
```

**准备观察什么**：不预设模型一定调用工具；先看 `roles` 是否实际包含四种角色，再分别核对工具调用请求、Harness 结果和最终回答。若模型未调用工具，也保留真实轨迹并分析，不重复抽样挑结果。

**真实证据**：待学员运行并贴回输出。

### 步骤 D：运行单变量角色对照

只在基线已经产生 `tool` 消息后运行。脚本复用基线中第一次模型决策和工具结果，只改一处 `role` 后向同一模型重放；成功响应或错误都会保存。

```powershell
$probe = @'
from copy import deepcopy
from pathlib import Path
import json
import ollama

baseline = json.loads(Path("lesson-d08-baseline.json").read_text(encoding="utf-8"))
tool_index = next(i for i, m in enumerate(baseline["messages"]) if m.get("role") == "tool")
messages = deepcopy(baseline["messages"][:tool_index + 1])
messages[tool_index]["role"] = "user"
record = {
    "model": baseline["model"],
    "changed_field": f"messages[{tool_index}].role: tool -> user",
    "messages": messages,
}
try:
    response = ollama.Client().chat(
        model=baseline["model"], messages=messages, tools=baseline["tools"],
        think=True, options={"temperature": 0}
    )
    record["response"] = response.message.model_dump(exclude_none=True)
except Exception as exc:
    record["error"] = {"type": type(exc).__name__, "message": str(exc)}
Path("lesson-d08-role-user.json").write_text(
    json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8"
)
print(json.dumps(record, ensure_ascii=False, indent=2))
'@
$encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch2 python -c "import base64; exec(base64.b64decode('$encoded').decode('utf-8'))"
```

## 4. 逐段读真实轨迹

每次只读一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 谁写入 | 协议语义 | 学员解释 |
|---|---|---|---|---|
| system + user | 待运行 | 待核对 | 待核对 | 待回答 |
| assistant.tool_calls | 待运行 | 待核对 | 待核对 | 待回答 |
| tool result | 待运行 | 待核对 | 待核对 | 待回答 |
| final assistant | 待运行 | 待核对 | 待核对 | 待回答 |

始终分开核对：工具是否执行、Harness 用什么角色回传、模型如何响应。最终文本相同或正确，也不能倒推出角色标注正确。

## 5. 追到关键源码

**教材锚点**：`vendor/ai-agent-book/book/chapter2.md:45-56`。

**实现锚点**：`vendor/ai-agent-book/chapter2/local_llm_serving/ollama_native.py:124-175` 与 `186-193`。

- 第 124–131 行：Harness 把终端输入追加为 `user`，工具定义单独放入顶层 `tools` 参数。
- 第 150–158 行：模型的工具调用请求被保存为 `assistant` 消息。
- 第 160–169 行：Harness 执行工具，再把结果追加为 `tool` 消息。
- 第 171–177 行：完整历史与同一组工具定义被送入下一次模型调用。
- 第 186–193 行：模型最终回复被追加为 `assistant`。

本实验走 Ollama 原生协议，工具结果使用 `tool_name`；教材中的 OpenAI 兼容示例使用 `tool_call_id`。两者都保留 `tool` 的来源语义，但关联字段属于 provider 协议细节，不能混为同一个线格式。

## 6. 单变量对照

- 唯一改变：已执行结果消息的 `role: tool -> user`。
- 保持不变：模型、温度、system/user 初始输入、第一次 assistant 工具调用、工具结果文本、顶层工具定义。
- 对照前预测：待回答。
- 真实轨迹与结果：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据协议与模型行为讨论 | 待提出重复或跨 provider 验证办法 |

单次运行只证明这次 API 与模型的实际行为。即使错标后仍回答正确，也不能推出角色可随意混用；还要区分“接口接受了输入”“模型猜对了内容”和“来源语义被正确保留”。

## 7. 理解检查：每次只问一题

1. **概念复述**：四种角色分别由谁产生？顶层 `tools` 为什么不是第五种角色？
2. **结果预测**：若保留工具结果文本但把 `role=tool` 改成 `role=user`，哪项事实仍成立，哪项证据已经丢失？
3. **故障定位**：工具日志显示执行成功，而下一次模型请求只有 `system/user/assistant`，没有对应 `tool` 结果；先查模型还是 Harness？用什么字段验证？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. `system` 来自开发者，`user` 来自终端用户，`assistant` 来自模型，`tool` 来自 Harness 执行；`tools` 是请求顶层能力清单。
2. 工具执行过仍可能成立；但“模型收到一条由 Harness 标识的工具结果”不成立。正确答案也可能来自文本猜测，不能修复来源证据。
3. 先查 Harness 的历史追加和请求组装；核对 assistant 工具调用、关联字段、下一次 `request.messages` 中的 `role=tool` 及内容。

</details>

## 8. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展。
- 状态：▶。

## 9. 讲师备注

- 正式实验未代跑；只运行了 `main.py --help`，核对 `ollama list` 和源码。
- 基线与对照输出均属于待实测证据，讲义不预填结果。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`。
- 本课开始后不立即追加进度日志；课堂确实中断或完成时，再按规则追加 Day 8 状态。
