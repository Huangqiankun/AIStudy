# Day 4 — 下一步由代码决定，还是由 Model 根据反馈决定？

> 章节 `vendor/ai-agent-book/book/chapter1.md` § 编排模式：工作流与自主（第 372–440 行）
> 实验 `vendor/ai-agent-book/chapter1/image-gen-workflow/`
> 备课日期 2026-10-17 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：编排权在谁手里

**本课只回答**：面对任务中的下一步，怎样判断是代码预先决定，还是 Model 根据环境反馈动态决定？

**Android / HarmonyOS 类比**：

- **工作流**像写死的 `ActivityResultLauncher` 调用链：先申请权限，再选文件，再上传；节点内部可以有智能判断，但节点顺序由代码控制。
- **自主 Agent**像 `Looper.loop()` 中的动态消息处理：每次拿到新消息或结果后，处理者再决定下一条消息是什么；路径不是启动前完整写死的。
- 类比边界：Android 的回调通常是确定性程序；LLM 即使看到相同反馈，也可能选择不同动作，所以自主不等于随机，更不等于没有约束。

```text
工作流：用户输入 → [代码固定：改写] → [代码固定：生图] → 输出
                         LLM 可在节点内部工作

自主：  用户目标 → Model 选动作 → 工具结果 → Model 再选动作 → … → 停止
                    路径由每轮反馈动态展开
```

**最小概念**：

- 工作流的拓扑由开发者预定义；适合顺序必须稳定、业务规则不能跳过的任务。
- 自主 Agent 的路径由 Model 结合当前上下文和环境反馈生成；适合步骤数量和异常分支难以预知的任务。
- 关键判断不看“有没有 LLM”，而看**谁拥有节点之间的路由权**。固定工作流的某个节点里使用 LLM，仍然是工作流。
- 选择不是越自主越好：固定路径换来可控性，自主路径换来适应性，同时增加成本、复合错误和失控风险。

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：观察教材的文生图工作流在收到普通请求与“只改写、不要生图”的请求时，实际经过哪些节点。
- 学员会亲自运行：用教材真实的 `run_workflow_route()`；联网的改写和生图叶子节点替换为本地记录桩，只隔离观察编排路径，不修改 `vendor/`。
- 会得到的产物：PowerShell 控制台中的 `events` 与 `nodes` JSON；没有真实图片，桩返回内容必须标为模拟，实际函数调用顺序是真实运行结果。
- 单变量对照：只改变用户需求文本，编排函数、两个节点和其他代码保持不变。
- 结束时要能回答：为什么“节点里用了 LLM”不能证明它是自主 Agent；从轨迹与源码哪里判断路由权属于代码还是 Model。

**确认问题**：某个退款系统固定执行“识别诉求 → 查订单 → 检查规则 → 提交退款”，其中“识别诉求”由 LLM 完成。你认为整个系统是工作流还是自主 Agent？判断依据是什么？

学员回答：判断为工作流，因为只有“识别诉求”由 LLM 完成，后续流程完全固定，符合工作流特征。判断正确。进一步精确：决定性证据不是 LLM 只出现一次，而是它只负责固定节点内部的语义处理，没有决定下一节点、跳过节点或重新规划路径的路由权；即使每个节点内部都调用 LLM，只要节点间拓扑由代码预定义，整体仍是工作流。概念确认通过。

## 2. 实验前预测

- 任务与输入：基线输入是普通生图需求；对照输入明确要求“只改写提示词，不要生成图片”。
- 固定条件：同一个 `run_workflow_route()`、同一份教材源码、同一组本地记录桩；不调用外部模型或图像 API。
- 观察点：`events` 中叶子函数的真实调用顺序，及返回值 `nodes` 中记录的节点顺序。
- 预测问题：只改变用户文本后，第二个 `image_generate` 节点会不会被跳过？是谁有能力作出这个决定？
- 学员预测：认为对照输入中的“不要生成图片”会被 LLM 理解，因此会跳过 `image_generate`，只输出用于画绿植的提示词；决定是否跳过的是 LLM。该判断作为实验前原始预测保留，暂不判定。实验条件已校准：基线与对照是两次独立运行，对照文本替换基线文本，并非追加成同一段连续提示；接下来检查真实编排函数是否把节点间路由权交给 LLM。

## 3. 运行实验

讲师于 2026-10-17 只运行 `main.py --help` 并核实 `pipeline` 可导入、`ROUTE_RUNNERS` 包含 `workflow/native/native_gptimage`；没有运行本课目标路径。

**步骤 A：进入实验目录并准备编码环境**（完成概念确认与预测后提供）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter1\image-gen-workflow'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 A 真实证据**：2026-10-17 学员在自己的 PowerShell 中执行成功；当前位置为 `F:\AIStudy\vendor\ai-agent-book\chapter1\image-gen-workflow`，`PYTHONUTF8=1`、`PYTHONIOENCODING=utf-8`。尚未运行目标探针。

**步骤 B：运行基线探针**（收到步骤 A 输出后再给；完整探针在课堂当步提供）：

```powershell
$probe = @'
import json
import pipeline

events = []

def fake_rewrite(requirement):
    events.append({"event": "rewrite", "input": requirement})
    return (
        {"prompt": f"rewritten::{requirement}", "negative_prompt": "", "style_notes": "local stub"},
        {"call_id": "stub-rewrite", "status": "ok"},
    )

def fake_generate(prompt, negative_prompt):
    events.append({"event": "generate", "prompt": prompt})
    return (
        b"stub-image",
        "image/png",
        [{"call_id": "stub-generate", "status": "ok"}],
    )

pipeline.rewrite_prompt = fake_rewrite
pipeline.generate_image_wanx = fake_generate

requirement = "帮我画一盆窗台绿植"
result = pipeline.run_workflow_route(requirement)
print(json.dumps({
    "requirement": requirement,
    "events": events,
    "nodes": [node["node"] for node in result["nodes"]],
}, ensure_ascii=False, indent=2))
'@
$payload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($probe))
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$payload').decode('utf-8'))"
```

记录桩的输出内容是模拟数据；`run_workflow_route()` 对两个桩函数的调用顺序、参数传递和节点组装是教材真实代码实际执行的证据。

**首次基线尝试**：2026-10-17 学员执行 `$probe | python -m uv run --extra ch1 python -`，真实调用顺序和节点记录均为 `rewrite → image_generate`，但 `requirement`、`events[0].input` 与改写后的 prompt 中中文全部变为 `?????????`。这说明 Windows PowerShell 经原生程序 stdin 管道传递脚本文本时发生编码损坏；由于实验输入未被忠实送达，本次只保留为环境故障证据，不能作为有效基线。下一步保持探针与输入不变，改用 Unicode 命令行参数 `python -c $probe` 重跑。

**第二次基线尝试**：`python -m uv run --extra ch1 python -c $probe` 在 Python 解析阶段报 `SyntaxError: unterminated string literal`，错误位置显示内部引号在 Windows PowerShell 向原生程序组装多行参数时已损坏；目标编排函数尚未运行，不能作为行为证据。下一步把同一 `$probe` 编码为纯 ASCII Base64，再在 Python 内解码执行，避免 stdin 编码与命令行引号两类传输问题。

**有效基线真实证据**：2026-10-17 学员将同一 `$probe` 编码为 UTF-8 Base64，再由 Python 解码执行成功。`requirement` 与 `events[0].input` 均原样保留“帮我画一盆窗台绿植”；真实事件顺序为 `rewrite → generate`，其中第二个事件收到 `prompt="rewritten::帮我画一盆窗台绿植"`；返回值的 `nodes` 为 `rewrite → image_generate`。这证明本次确实调用了两个记录桩并完成参数传递，但仅凭表面顺序还不能判断节点路由由代码还是 Model 控制，需结合源码。

## 4. 逐段读真实轨迹

| 片段 | 真实摘录 / 证据字段 | Model / 节点做什么 | Harness / 编排代码做什么 | 学员解释 |
|---|---|---|---|---|
| 输入 | `requirement="帮我画一盆窗台绿植"` | 改写节点将收到需求文本 | `run_workflow_route()` 收到字符串 | 与第一个事件一起解释 |
| 第一个事件 | `events[0].input="帮我画一盆窗台绿植"` | 本地桩模拟改写输出 | 编排代码调用 `rewrite_prompt()` | 学员指出 `rewrite` 指向 `generate`，中间值带有 `rewritten::`；方向正确 |
| 第二个事件 | `events[1].prompt="rewritten::帮我画一盆窗台绿植"` | 本地桩模拟图片字节 | 编排代码把改写结果传给 `generate_image_wanx()` | 经字段校准：前一事件的原始输入与后一事件的改写 prompt 首尾对应，证明参数传递 |
| 节点记录 | `nodes=["rewrite","image_generate"]` | 无新的路由决策证据 | 返回值记录两个节点 | 只能证明顺序，不能单独证明数据依赖或路由权 |

必须区分：桩数据是模拟的；函数调用确实发生、参数确实传递、节点顺序确实由真实源码执行。不能用模拟图片证明真实外部 API 可用。

## 5. 追到关键源码

行号按 2026-10-17 本地 checkout 核实，教材更新后重新定位：

| 源码位置 | 输入 → 处理 → 输出 | 课堂问题 |
|---|---|---|
| `image-gen-workflow/pipeline.py:348-367` | 用户需求 → 固定调用改写，再固定调用生图 → 两个节点的结果 | 哪一行把第二步写死？有没有根据用户文本决定是否跳过的分支？ |
| `image-gen-workflow/pipeline.py:404-407` | 路线名 → 字典查找 → 对应路线函数 | 选择 `workflow` 后，谁掌握路线内部的节点顺序？ |
| `context/agent.py:747-893`（与 Day 3 已读源码对照） | 累计轨迹 → Model 每轮返回 `tool_calls` → Harness 执行并把观察追加回轨迹 | 自主路径中，下一项工具动作从哪里产生？ |

本课精读 `pipeline.py:348-367`；Day 3 的 ReAct 循环只作为已完成的对照证据，不重复通读。

**源码读取证据**：2026-10-17 学员在自己的 PowerShell 中读取 `pipeline.py:348-367`。`352` 调用 `rewrite_prompt(requirement)`，`355-357` 随后直接调用 `generate_image_wanx(rewrite["prompt"], rewrite["negative_prompt"])`，`353` 与 `358-367` 分别追加两个节点记录。下一步由学员判断这段代码中的路由权归属。

**学员源码解释**：判断即使 `rewrite_prompt()` 返回“不要生图”的语义，工作流也不会跳过第二步；`355-357` 由编排代码直接发起 `generate_image_wanx()` 调用，且这段函数中没有让 LLM 决定“调用或跳过”的条件分支。解释正确：路由权属于 `run_workflow_route()` 的确定性代码，不属于改写节点中的 LLM。

## 6. 单变量对照：只改变用户需求文本

先问：**若输入改成“只改写提示词，不要生成图片”，同一个工作流会不会跳过第二步？先说明依据，再运行。**

**对照前修正预测**：学员最初认为 LLM 会理解用户语义并跳过生图；读取真实源码后修正为不会跳过，因为第二个函数调用由编排代码无条件发起，当前函数中不存在语义路由分支。接下来用真实轨迹检验修正后的预测。

保持同一 PowerShell 会话，只替换探针里的输入文本：

```powershell
$comparisonProbe = $probe.Replace('帮我画一盆窗台绿植', '只改写提示词，不要生成图片')
$comparisonPayload = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($comparisonProbe))
python -m uv run --extra ch1 python -c "import base64; exec(base64.b64decode('$comparisonPayload').decode('utf-8'))"
```

- 唯一改变：`requirement` 字符串。
- 保持不变：真实编排函数、两个记录桩、运行环境和观察字段。
- 先检查干预生效：输出的 `requirement` 与第一个事件的 `input` 是否变为新文本。
- 再检查路径：`events` 与 `nodes` 是否变化；一次结果只证明当前源码对这两个输入的行为。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据源码与轨迹解释 | 若要支持跳过生图，应把路由判断放在哪里，是否应交给 Model？ |

## 7. 理解检查：每次只问一题

1. **概念复述**：一个 LLM 参与多个固定节点的系统，为什么仍可能是工作流？
2. **结果预测**：业务出现未预设的异常时，固定工作流与自主 Agent 各可能怎样处理？哪些边界仍应由代码固定？
3. **故障定位**：日志显示系统总是执行 `rewrite → image_generate`。你先看哪段源码或哪类轨迹，才能判断这是开发者写死的路径，还是 Model 连续两轮恰好做出相同选择？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 判断标准是节点间路由权，而不是系统里是否出现 LLM；LLM 只负责固定节点内部处理时，整体仍是工作流。
2. 工作流走预设异常分支或交还人工；自主 Agent 可根据反馈重规划，但权限、支付、删除、发布等硬边界仍应由 Harness 约束。具体行为需看实现与实测，不能预设自主 Agent 一定处理正确。
3. 先看编排函数是否按固定顺序直接调用节点、是否存在路由分支；再看自主轨迹中每一步动作是否来自 Model 响应的 `tool_calls`，并与下一轮观察配对。相同表面顺序不等于相同控制权。

</details>

## 8. 可选 AI 代码演示

本课的 PowerShell 探针由讲师提供完整代码，学员只需预测、运行、观察和解释；不要求修改 Python。探针通过运行时替换联网叶子函数来隔离编排路径，不写入 `vendor/`，不增加依赖。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：本课主实验为本地编排探针，不调用模型；真实输出待学员运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用教材真实编排函数 + 本地记录桩，只隔离观察路径控制权
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/loop-engineering/SKILL.md`（仅用于区分循环控制与固定编排；本课定义以第 1 章正文及两段真实源码为准）
- 备课核验：`image-gen-workflow/main.py --help`、`pipeline` 导入、路线名与源码行号已于 2026-10-17 核实；目标探针未代跑
- 进度处理：仅建立 Day 4 讲义，尚未追加上课日志
