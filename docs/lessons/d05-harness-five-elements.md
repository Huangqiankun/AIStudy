# Day 5：Harness 五要素

> 核心问题：上下文与工具已经让 Agent 能行动，为什么仍不足以证明行动安全、结果正确；约束、验证与纠正怎样把一次工具调用闭成可靠回路？

## 1. 建立概念与实验全景

固定三行开场：

- 上次到哪：Day 4 已用真实编排函数、调用轨迹和源码确认“路由权”是区分固定工作流与自主 Agent 的关键。
- 今天讲什么：Day 5「Harness 五要素」，区分让 Agent“能做事”的 Context / Tools，与让它“可靠做事”的 Constrain / Verify / Correct。
- 今天的最小可跑物：给教材真实 `calculate` 工具套一个最小 Harness，观察安全表达式与越界表达式经过同一条链时留下的结构化证据。

Android / HarmonyOS 类比：Harness 类似 OkHttp Interceptor 链叠加 runtime permission、响应断言与失败恢复；Model 可以提出请求，但请求能否发出、结果是否合格、失败后怎么办，不应只靠 Model 自述。

本课只回答一个问题：五个功能在同一次动作中各负责什么，为什么不能互相冒充。

**实验全景预告**（只说明路线，不揭示结果）：

- 任务：要求计算结果必须为 `14`，并输出每个 Harness 阶段留下的结构化事件。
- 会运行：教材 `ToolRegistry.calculate()` 与其真实 `safe_eval()`，外面只加最小的验证条件和纠正分支；不调用 LLM，不联网，不修改 `vendor/`。
- 会观察：输入上下文、工具名与参数、工具结构化结果、验证是否通过、纠正是否启动、最终结构化结果。
- 单变量对照：只把表达式从普通算术换成包含越界语法的表达式；其他代码、期望值和纠正策略全部不变。
- 结束时应能回答：为什么“工具返回了东西”不等于任务正确；为什么拦截、判定和恢复是三种不同职责。

**概念确认问题**：支付 Agent 已拿到订单上下文，也有 `refund(orderId, amount)` 工具。如果它直接按 Model 给出的参数调用并把工具返回原样展示，五要素里已经有什么，还缺什么？先说判断依据，不要求背英文名。

**学员首次回答**：正确识别订单信息属于 Context、`refund` 属于 Tools；把“展示结果”误归为 Correct，把输出格式封装误归为 Verify，并把“原样展示”直接当成缺少 Constrain 的证据。经退款例子校准：Constrain 检查执行前权限与业务边界，Verify 依据权威状态判断结果，Correct 在验证失败后重试、回退或转人工；输出格式不能证明业务正确。

**换例复查**：学员将“退款金额不超过实付金额”归为 Constrain，将“调用后查询状态是否为 `REFUNDED`”归为 Verify，将“未退款则按幂等键重试、再失败转人工”归为 Correct，且依据均正确。概念边界通过。

## 2. 实验前预测

- 固定任务：最终结果必须为数字 `14`。
- 基线表达式：`2 + 3 * 4`。
- 对照表达式：`().__class__`。
- 固定条件：同一个工具、同一个 AST 白名单、同一个验证规则、同一个纠正策略。
- 观察点：`events` 中每个阶段的输入与结构化结果；不能只看最终值。

预测问题：两种表达式经过同一条 Harness 链时，哪些阶段的事件会相同，哪些可能不同？最终结果如果相同，能否说明两次执行过程一样可靠？

**学员基线预测**：`Constrain=数值`、`Verify=true`、`Correct=none`、`Final=14`。

**学员对照预测**：首次 `Constrain=数值`、`Verify=false`，随后 `Correct=fallback`，备用计算验证通过并得到 `Final=14`。已校准记录结构：进入 `fallback` 后不会再出现第二个 `Correct=none`；首次工具边界究竟返回数值还是错误，保留为待实测判断。

## 3. 运行实验

讲师于 2026-10-17 只核实了 `main.py --help`、`ToolRegistry.calculate(expression)` 与 `safe_eval(expression)` 可导入；没有代跑本课目标探针。

**步骤 A：进入实验目录并准备编码环境**（完成概念确认与预测后提供）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter1\context'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 A 真实证据**：2026-10-17 学员在自己的 PowerShell 中执行成功；当前位置为 `F:\AIStudy\vendor\ai-agent-book\chapter1\context`，两个 UTF-8 环境变量已在当前会话设置。

**步骤 B：运行基线探针**（收到步骤 A 输出后再给）：

```powershell
$probe = @'
import json
from agent import ToolRegistry

EXPECTED = 14

def run(expression):
    context = {"task": "计算表达式，最终结果必须是 14", "expression": expression}
    events = [{"stage": "context", "value": context}]
    events.append({"stage": "tool", "name": "calculate", "arguments": {"expression": expression}})

    primary = ToolRegistry.calculate(expression)
    events.append({"stage": "constrain", "tool_result": primary})

    passed = "error" not in primary and primary.get("result") == EXPECTED
    events.append({"stage": "verify", "expected": EXPECTED, "passed": passed})

    if passed:
        final = primary
        events.append({"stage": "correct", "action": "none"})
    else:
        fallback = "2 + 3 * 4"
        repaired = ToolRegistry.calculate(fallback)
        repaired_ok = "error" not in repaired and repaired.get("result") == EXPECTED
        events.append({
            "stage": "correct",
            "action": "fallback",
            "expression": fallback,
            "result": repaired,
            "verified": repaired_ok,
        })
        final = repaired if repaired_ok else {"error": "correction failed"}

    print(json.dumps({"events": events, "final": final}, ensure_ascii=False, indent=2))

run("2 + 3 * 4")
'@
$payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$payload').decode('utf-8'))"
```

`constrain` 事件记录工具边界给出的实际结果；它是否实施了语法限制，必须继续追到 `calculate()` 和 `safe_eval()` 源码，不能只凭事件名下结论。

**有效基线真实证据**：2026-10-17 学员亲自运行。日志显示 `Calculating: 2 + 3 * 4`；`context.value.expression` 与 `tool.arguments.expression` 均为原输入；工具边界返回 `result=14,type=int`；`verify.passed=true`；`correct.action=none`；`final.result=14`。本次观察与学员基线预测一致，但只证明当前源码和当前输入的一次行为。

## 4. 逐段读真实轨迹

| 片段 | 真实字段 | 五要素角色 | 学员解释 |
|---|---|---|---|
| 输入 | `events[*].stage == "context"` | Context：把当前任务与候选表达式送入本轮 | 待实测 |
| 动作接口 | `stage == "tool"` | Tools：声明动作名称与参数 | 待实测 |
| 工具边界 | `stage == "constrain"` | Constrain：实际限制由 `safe_eval()` 的白名单实现 | 待源码核对 |
| 结果判定 | `stage == "verify"` | Verify：按任务特定不变量判定，而非相信“工具已返回” | 待实测 |
| 恢复动作 | `stage == "correct"` | Correct：验证失败时执行有界恢复；未失败时不启动 | 待实测 |

始终分开记录：实测观察、对观察的解释、仍需对照或源码确认的判断。

## 5. 追到关键源码

行号按 2026-10-17 本地 checkout 核实，教材更新后重新定位：

| 源码位置 | 输入 → 处理 → 输出 | 课堂问题 |
|---|---|---|
| `context/agent.py:252-282` | 表达式 → 规范化并调用 `safe_eval()` → 结果字典或错误字典 | 工具接口在哪里？错误为何被转成结构化返回？ |
| `context/calc_sandbox.py:49-90` | AST 节点 → 白名单递归检查 → 放行或拒绝 | 这是提示词劝告，还是代码层边界？ |
| `context/calc_sandbox.py:93-110` | 字符串 → 解析、检查、受限求值 → 数值或异常 | 拦截发生在执行前还是执行后？ |
| `context/agent.py:701-729,950-959` | ReAct 循环状态 → `completed` / `task_success` → 调用方语义 | 为什么“程序结束”不能充当任务正确性验证？ |

本课精读约 20–40 行：以 `agent.py:262-282` 和 `calc_sandbox.py:66-110` 中与本次输入直接相关的分支为主，不整段照抄。

**源码读取证据**：2026-10-17 学员在自己的 PowerShell 中读取 `agent.py:262-282` 与 `calc_sandbox.py:66-90`。前者在 `272` 调用 `safe_eval()`，成功时于 `274-278` 返回数值结构，失败时于 `280-282` 捕获异常并返回 `{"error": ...}`；后者在 `87-90` 将未列入白名单的 `Attribute` 等节点拒绝。

**学员源码解释**：能沿真实代码说明 `ast.Attribute → calc_sandbox.py:90 抛出 UnsafeExpression → agent.py:280 捕获 → agent.py:282 返回结构化错误`，并据此把对照预测从首次“返回数值”修正为首次返回错误、`Verify=false`、进入 `fallback`。源码输入、处理、输出解释通过。

## 6. 单变量对照

先预测，再只替换探针末行的表达式：

```powershell
$comparisonProbe = $probe.Replace('run("2 + 3 * 4")', 'run("().__class__")')
$comparisonPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($comparisonProbe))
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$comparisonPayload').decode('utf-8'))"
```

- 唯一改变：候选表达式。
- 保持不变：任务、期望值、工具接口、白名单、验证条件、纠正策略和输出字段。
- 先核对干预生效，再比较每个阶段；不能只比较 `final`。
- 一次对照只证明当前源码对这两个输入的行为，不能推断白名单覆盖了所有攻击形式。

**对照真实证据**：2026-10-17 学员在同一 PowerShell 会话中只把末行输入从 `2 + 3 * 4` 替换为 `().__class__`。日志先记录 `Calculating: ().__class__`，随后出现 `unsupported syntax: Attribute`；`constrain.tool_result` 为结构化错误，`verify.passed=false`；`correct.action=fallback` 后再次调用原工具计算 `2 + 3 * 4`，`correct.verified=true`；最终 `final.result=14`。实测与学员读取源码后的修正预测一致。

## 7. 理解检查：每次只问一题

1. **概念复述**：Context / Tools 与 Constrain / Verify / Correct 的职责边界是什么？
2. **结果预测**：如果去掉 Verify、仍保留 Constrain 与 Correct，出现“安全但算错”的结果时会怎样？
3. **故障定位**：日志显示工具返回成功、Agent 也输出了最终答案，但业务结果错误；你先查哪类证据，如何区分工具执行、结果验证和纠正是否分别发生？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. Context 决定模型看到什么，Tools 决定能通过什么接口观察或行动；Constrain 在行动前或边界处限制不允许的操作，Verify 用任务特定证据判断结果是否合格，Correct 在失败后重试、回退或升级人工。
2. Correct 没有可靠的失败信号，可能根本不启动；约束只能证明动作在允许范围内，不能证明业务结果正确。
3. 分别检查工具参数与结构化返回、独立验收条件及其输入、纠正分支及后续状态；终止文本或 `completed=true` 不能替代任务正确性证明。

</details>

## 8. 可选代码演示

本课探针由讲师完整提供，学员负责预测、运行、观察与解释；不要求修改 Python。探针只在内存中调用教材工具并打印证据，不写 `vendor/`、不新增依赖。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待课堂记录。
- 尚未理解及证据：待课堂记录。
- 下次从哪一小步继续：待课堂记录。
- 状态：▶

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-10-17 开课，等待学员进入概念阶段。
- 实验命令、模型、证据位置与必要真实摘录：本课不调用模型；目标探针尚未运行。
- 学员的原始预测与后续解释：待记录。
- 三项理解检查：概念 待完成 / 预测 待完成 / 定位 待完成。
- 是否采用代码演示及原因：采用最小 Python 探针，把五个职责映射到同一条调用链，同时复用教材真实工具与安全边界。
- 本次观察、假设、待验证点：`ToolRegistry.calculate()` 和 `safe_eval()` 已验证可导入；目标输入的实际输出必须由学员运行后记录。
- 对应备课 skill：`vendor/ai-agent-book/skills/loop-engineering/SKILL.md`（用于验证、纠正和终止条件的边界；五要素定义以第 1 章正文为准）。
- 备课核验：`context/main.py --help`、函数签名与源码行号已于 2026-10-17 核实；目标探针未代跑。
