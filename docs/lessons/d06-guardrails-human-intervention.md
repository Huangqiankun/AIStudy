# Day 6：护栏与人工干预

> 章节 `vendor/ai-agent-book/book/chapter1.md` § 护栏与安全性、人工干预（第 464–504 行）
> 备课材料 `vendor/ai-agent-book/skills/coding-agent-harness/SKILL.md`
> 备课日期 2026-10-19 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：为什么高风险动作不能只靠模型自觉

固定三行开场：

- 上次到哪：Day 5 已用真实 AST 拦截对照，区分 Context / Tools 与 Constrain / Verify / Correct，并确认“工具成功”不等于业务正确。
- 今天讲什么：Day 6「护栏与人工干预」，聚焦上下文层、执行层、数据层三道防线，以及何时必须暂停并把控制权交给人。
- 今天的最小可跑物：一个无真实副作用的退款调度探针；同一笔高风险退款只改变执行边界模式，观察动作是否真正越过 `execute()`。

**本课只回答**：为什么高风险动作不能只靠 Model 在提示词里“自觉谨慎”，而要由模型上下文之外的 Harness 或数据层强制拦截，并在必要时转人工？

**Android / HarmonyOS 类比**：Model 像普通 App 进程提出“访问相机”的请求；执行层护栏像系统的 runtime permission 与 PermissionController，真正的授权决定不由 App 自己宣布；数据层护栏像 Binder 服务端再次校验调用 UID。人在回路像 `ActivityResultContracts.RequestPermission()`：流程暂停，用户的决定作为新的外部结果返回。类比边界是：课堂探针只模拟一次退款调度，不模拟完整操作系统权限栈。

```text
用户/外部数据 ──> [上下文层：模型能看什么] ──> Model 提议动作
                                                   │
                                                   v
                         [执行层：动作生效前评级/审批] ──> 工具
                                                   │
                                                   v
                         [数据层：最终状态仍受规则约束] ──> 世界状态
                                      │
                         高风险或连续失败 ──> 人工决定
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：调度一笔金额超过阈值的退款请求，并输出每个决策点的结构化事件。
- 学员会亲自运行：先运行强制执行边界的基线，再只改变一个环境变量，运行“仅提醒”的对照。
- 会得到的产物：PowerShell 控制台中的 JSON；重点检查 `risk`、`approval`、`tool_result` 与 `final` 是否存在及先后关系。
- 实验不会做什么：不调用真实退款 API，不写业务数据，不调用 LLM；`execute()` 只产生带 `simulated=true` 的回执。
- 结束时要能回答：提示词中的“请谨慎”、执行前的强制门、数据层不变量与人工批准分别能证明什么，不能证明什么。

**确认问题**：一个 Android App 在自己的进程里写了 `if (safe) openCamera()`，而 `safe` 也由这个 App 自己判断。它和系统服务端根据调用 UID 强制检查权限，最关键的信任边界差别是什么？

**学员回答**：首次回答“哪些 UID 的 `safe` 为 true/false”，已接近权限规则内容，但尚未指出信任边界。经解释后识别到 `Binder.getCallingUid()` 是可信身份来源；进一步用“无权限但只记录警告、仍执行 `openCamera()`”复查，正确回答“不算护栏生效，因为没有进行拦截操作”。已能区分身份取证、规则判断与执行前强制阻止；概念确认通过。

## 2. 实验前预测

- 固定请求：`refund(user_id="u-17", amount=5000)`。
- 固定规则：金额大于 `1000` 视为高风险；本次没有人工批准。
- 固定实现：同一份 `classify_risk()`、`dispatch()` 与模拟 `execute()`。
- 唯一变量：`GUARDRAIL_MODE` 为 `enforced` 或 `advisory`。
- 观察点：高风险是只留下提示，还是能阻止 `execute()` 被调用；人工批准缺失如何在事件里体现。

**预测问题**：两次运行中，哪些事件你预计都会出现，哪些只会出现在其中一次？只根据“最终打印了 JSON”，能否判断退款动作已越过执行边界？

**学员预测**：基线首次漏掉 `proposal`，并多写了只属于 `advisory` 分支的 `warning`；但正确判断 `enforced + HUMAN_APPROVED=False` 时不会调用 `execute()`。校准后得到基线事件序列 `proposal → risk → approval`，且无 `tool_result`。对照首次仍预测 `approval` 且不执行；经“只有日志、没有 return/异常”的 Android 控制流例子缩小后，正确判断 `advisory` 会继续到 `execute()`，修正事件序列为 `proposal → risk → warning → tool_result`。

## 3. 运行实验

讲师备课只核实了教材小节、Skill、环境与探针静态结构，没有代跑本课目标探针。探针以内存脚本运行，不修改 `vendor/`，没有真实副作用。

**步骤 A：进入目录并设置环境**（完成概念确认后提供）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter1'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 A 真实证据**：2026-10-19 学员在自己的 PowerShell 中执行成功；`CurrentPath` 为 `F:\AIStudy\vendor\ai-agent-book\chapter1`，`NO_PROXY=localhost,127.0.0.1,::1`，`PYTHONUTF8=1`，`PYTHONIOENCODING=utf-8`。这只确认环境就绪，尚未执行目标探针。

**步骤 B：运行强制执行边界的基线**（收到步骤 A 输出并完成预测后再给）：

```powershell
$probe = @'
import json
import os

RISK_THRESHOLD = 1000
HUMAN_APPROVED = False

def model_proposal():
    return {"tool": "refund", "user_id": "u-17", "amount": 5000}

def classify_risk(call):
    return "high" if call["amount"] > RISK_THRESHOLD else "low"

def execute(call):
    return {"ok": True, "simulated": True, "receipt": f"refund:{call['user_id']}:{call['amount']}"}

def dispatch(call):
    mode = os.environ.get("GUARDRAIL_MODE", "enforced")
    risk = classify_risk(call)
    events = [
        {"stage": "proposal", "call": call},
        {"stage": "risk", "level": risk, "mode": mode},
    ]
    if risk == "high" and mode == "enforced" and not HUMAN_APPROVED:
        events.append({"stage": "approval", "required": True, "granted": False})
        return {"events": events, "final": {"status": "paused_for_human"}}
    if risk == "high" and mode == "advisory":
        events.append({"stage": "warning", "message": "high risk; advisory only"})
    result = execute(call)
    events.append({"stage": "tool_result", "value": result})
    return {"events": events, "final": {"status": "executed", "result": result}}

print(json.dumps(dispatch(model_proposal()), ensure_ascii=False, indent=2))
'@
$payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
$env:GUARDRAIL_MODE = 'enforced'
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$payload').decode('utf-8'))"
```

**准备观察什么**：不要只看 `final.status`；沿 `proposal → risk → approval/warning → tool_result` 检查动作是否真的越过执行门。`simulated=true` 只证明这次回执来自模拟函数，不代表真实业务成功。

**真实证据**：待学员运行并贴回输出。记录完整命令、退出状态和事件序列；本课不写产物文件。

**基线真实证据**：2026-10-19 学员亲自在 PowerShell 中运行。事件依次为 `proposal(call=refund, amount=5000)`、`risk(level=high, mode=enforced)`、`approval(required=true, granted=false)`；没有出现 `warning` 或 `tool_result`，`final.status=paused_for_human`。输出未显示 Python 异常，PowerShell 返回提示符。该观察与校准后的基线预测一致；是否能仅凭事件缺失断定 `execute()` 未调用，仍需结合源码控制流判断。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | Model / Harness / Environment 的责任 | 学员解释 |
|---|---|---|---|
| 动作提议 | `proposal.call={tool: refund, amount: 5000}` | Model 只提出工具与参数 | 已确认动作提议不等于执行 |
| 风险评级 | `risk.level=high, mode=enforced` | Harness 按独立规则分类 | 已确认命中高风险条件 |
| 审批或警告 | `approval.required=true, granted=false` | 强制门决定暂停，提醒只产生信息 | 已确认审批记录单独不能证明拦截 |
| 工具结果 | 基线无 `tool_result` | 只有出现该事件才有调用 `execute()` 的回执证据 | 需结合源码说明事件缺失 |
| 最终状态 | `paused_for_human` | 汇总流程状态，不能反向替代前面证据 | 已确认最终标签也不能单独证明控制流 |

必须分开核对：Model 是否提出动作、Harness 是否允许动作、工具是否执行、世界状态是否满足业务不变量。课堂探针只覆盖前三项中的最小部分，不伪装成真实退款验证。

**基线逐段剖析**：面对 `approval(required=true, granted=false)`，学员正确判断它不能单独证明未执行，最初理由是“还没看到最终状态”。经校准后理解 `final.status` 也只是同一程序写出的标签，仍需检查工具回执与源码控制流。在反例轨迹同时给出 `approval.granted=false`、`final.status=paused_for_human` 和 `tool_result` 时，学员正确判断护栏没有成功拦截，因为已经出现执行回执。

## 5. 追到关键源码

本章只给护栏高层骨架，没有现成的退款审批项目；依照项目规则，最小补充代码放在本讲义中，不另建工程、不修改 `vendor/`。

**源码锚点**：`docs/lessons/d06-guardrails-human-intervention.md:82-105` 的内存探针；重点定位 `classify_risk()`、`execute()` 与 `dispatch()`，精读 24 行。

- 输入：Model 候选调用、外部模式、独立的批准状态。
- 处理：`classify_risk()` 评级；`dispatch()` 在执行前决定暂停、提醒或调用工具。
- 输出：按时间排序的事件与最终流程状态。
- 关键边界：`execute()` 是否可达由 `dispatch()` 的代码分支决定，而不是由 Model 在同一上下文里自称“安全”决定。
- 当前行号已于 2026-10-19 用 `rg -n` 核实；文件后续若调整，授课前重新定位。

**教材概念锚点**：`book/chapter1.md:476-502`。精读时分三小段：三层定义、执行层独立复核、两类人工干预触发条件；不整段照抄。

**源码读取证据**：2026-10-19 学员在自己的 PowerShell 中读取本文件第 82–105 行，确认第 98 行判断高风险、强制模式和未批准，第 99 行记录审批事件，第 100 行 `return`，第 103 行才调用 `execute()`。

**学员解释**：首次把第 98 行条件判断直接称为“拦截的护栏”；经区分后理解第 98 行负责决定是否命中，第 100 行 `return` 才改变控制流并使第 103 行不可达。删除第 100 行的换例中，正确判断程序会继续调用 `execute()`，出现 `tool_result`，最终状态为 `executed`。源码输入、判断、控制流与输出解释通过。

## 6. 单变量对照

完成基线剖析后，先预测，再运行：

```powershell
$env:GUARDRAIL_MODE = 'advisory'
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$payload').decode('utf-8'))"
```

- 唯一改变：`GUARDRAIL_MODE` 从 `enforced` 改为 `advisory`。
- 保持不变：请求、金额、风险阈值、人工批准状态、风险分类器、执行函数和事件格式。
- 对照前预测：首次仍预测 `approval` 且不执行；经“警告后没有 return/异常”的控制流例子校准，修正为 `proposal → risk → warning → tool_result`，会调用 `execute()`。
- 真实轨迹与结果：2026-10-19 学员亲自运行；`risk.level` 仍为 `high`，`mode=advisory`，随后依次出现 `warning` 与 `tool_result(ok=true, simulated=true, receipt=refund:u-17:5000)`，`final.status=executed`。与修正预测一致。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 同一高风险请求在 `enforced` 下停于 `approval`，在 `advisory` 下出现 `warning → tool_result` | 模式改变了高风险分支的控制流；提醒没有强制阻止副作用 | 若改成低风险金额或得到人工批准，分支会怎样；须另做单变量实验验证 |

一次对照只证明当前代码对当前输入的行为。它不能证明所有高风险动作都被覆盖，也不能证明 `classify_risk()` 的阈值正确；误拒绝与漏放都需要后续评估。

**学员对照解释**：正确用 `tool_result` 和 `final.status=executed` 说明 `warning` 没有阻止动作；经校准后指出 `tool_result` 是更直接的执行回执，`final` 只是同一程序写出的辅助标签。三层换例中，正确把数据库“累计退款不得超过实付金额”的拒绝写入归为数据层，并说明上下文层一旦被攻破，仍需执行层和数据层在不同信任边界提供多重防线。

## 7. 理解检查：每次只问一题

1. **概念复述**：上下文层、执行层、数据层分别管什么？为什么越靠下越不依赖 Model 自己判断？
2. **结果预测**：保持强制模式不变，只把 `HUMAN_APPROVED` 改为 `True`，你预计事件序列怎样变化？哪一项仍不能由本实验证明？
3. **故障定位**：日志有高风险警告，也有 `final.status="executed"`。你先查什么证据，怎样区分“提醒存在”“强制门生效”“真实业务状态正确”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 上下文层限制进入模型的内容，执行层在动作生效前做独立评级、权限与审批，数据层对最终可写状态强制不变量；后两层不能只靠被保护的同一上下文自证安全。
2. 预测应指出批准事件或门控条件改变后 `execute()` 可能可达；但模拟回执不能证明真实退款成功或数据层约束正确。接受带条件与不确定性的表达。
3. 依次检查风险评级输入、门控分支与审批记录、是否真的出现工具调用/结果，再查权威业务状态或数据层审计；警告文本与最终字符串都不能替代这些证据。

</details>

卡住时先用 runtime permission 换例缩小问题，再用一条新的事件序列复查。三项通过且实验、源码、对照均完成，才记 `✅`。

## 8. 可选 AI 代码演示

本课探针由讲师完整提供，学员负责预测、运行、观察与解释；不要求修改 Python。若学员希望继续，可另做“金额降到阈值内”或“人工批准为真”的单变量扩展，但不作为本课硬性作业，也不与主对照同时改变。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：本课不调用模型；待学员运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用内存中的最小 Python 探针；用于隔离“提示”与“强制执行门”，不产生真实副作用
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/coding-agent-harness/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 6 日志；仅备课不追加日志、不修改既有完成状态
