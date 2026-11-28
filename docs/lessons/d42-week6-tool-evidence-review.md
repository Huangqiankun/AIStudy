# Day 42 — W6 工具全链路证据复盘（buffer）

> 章节：W6 / ch4 buffer 复盘（`vendor/ai-agent-book/book/chapter4.md`）
> 实验：审计教材冻结验证收据 `vendor/ai-agent-book/chapter4/execution-tools/validation/experiment_4_4/real_mcp_gui_20260802T093657Z/summary.json`
> 备课日期 2026-11-28 ｜ 授课日期 待开展 ｜ 状态 待开展

本日是 buffer 日，不引入 Day 43 的 Coding Agent，只复盘 Day 36–41。本文是分步教学材料，不一次性贴入对话；没有实际运行、源码讲解、单变量对照和三项理解检查时，不填完成状态。

## 1. 建立概念：局部绿灯怎样才能成为整体结论

**本课只回答**：工具目录、协议调用和若干执行门都显示成功时，怎样根据门禁定义判断整个实验是否完成，而不把局部绿灯升级成整体成功？

**类比**：Android 发布流水线里，`assembleDebug`、单元测试、截图测试和真机回归是不同门。某个 Binder 调用返回成功、某个测试用例为绿，都不能代替发布规则中的其余必需门。真正的整体结论来自“每个门检查什么”以及“发布策略要求哪些门同时通过”，不是来自绿灯数量看起来很多。

```text
目录/定义 → 请求调用 → 处理器执行 → 工具结果 → 外部业务状态
    │           │            │            │             │
    └────────── 各层收据与局部门禁 ─────────────────────┘
                              ↓
                 汇总策略：core 还是 all？
                              ↓
                    整体状态 / 阻塞项
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：读取教材一份冻结的 20 条收据汇总，列出通过门与阻塞门，并用两种汇总策略做审计。
- 学员会亲自运行：先按 `core` 门禁汇总；再只把门禁策略改为 `all`。
- 会得到的产物：控制台 JSON，包含输入指纹、门禁数量、阻塞门、冻结收据记录的状态和本次审计结论。
- 结束时要能回答：为什么 MCP 非错误返回、工具 payload、外部状态和整体完成是不同层；为什么必须先读门禁定义再解释 `status`。
- 证据边界：输入是教材 2026-08-02 生成的历史收据；本课不会重放真实 MCP、LLM、日历、邮件、虚拟桌面或移动设备。

**确认问题**：一个 Android 发布流水线里 15 个门有 13 个是绿的，仅凭“13/15”能否判断可以发布？如果不能，还必须知道什么？

## 2. 实验前预测

- 任务与输入：固定读取同一份 `summary.json`，并用文件原始字节计算输入指纹。
- 固定条件：同一历史 campaign、同一门禁值、同一 core 门名单、同一审计程序。
- 唯一变量：`D42_GATE=core` 或 `D42_GATE=all`。
- 观察点：`input_fingerprint`、`true_gate_count`、`false_gates`、`recorded_status`、`recorded_official_complete`、`audit_release_allowed`。
- 预测问题：只改变汇总策略后，哪些字段必须保持不变，哪个字段可能改变？这能说明“事实收据”和“判定策略”各自负责什么？
- 学员预测：待回答。

## 3. 运行实验

本课不执行教材端到端验证脚本，不创建外部资源。讲师备课只检查了文件存在、JSON 字段和当前源码锚点，没有代跑下面的目标审计。

### 步骤 A：准备固定审计程序

- 输入/读取：读取教材冻结的 `summary.json` 原始字节与 JSON 字段。
- 处理：计算输入指纹，按教材当前源码中的 core 排除名单分别计算 `all_core` 与 `all_gates`。
- 输出/用途：本步骤只把程序放入 PowerShell 变量；后续运行会输出同一份收据事实与不同门禁策略的判定。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\execution-tools'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$d42Probe = @'
import hashlib
import json
import os
from pathlib import Path

path = Path(
    "validation/experiment_4_4/"
    "real_mcp_gui_20260802T093657Z/summary.json"
)
raw = path.read_bytes()
summary = json.loads(raw)
gates = summary["gates"]

optional_external = {
    "real_calendar_mutation",
    "real_github_pr_mutation",
    "real_email_mutation",
    "real_virtual_desktop_session",
    "real_virtual_mobile_session",
}
core_names = [name for name in gates if name not in optional_external]
all_core = all(gates[name] for name in core_names)
all_gates = all(gates.values())
policy = os.environ["D42_GATE"]
release_allowed = all_core if policy == "core" else all_gates

print(json.dumps({
    "campaign_id": summary["campaign_id"],
    "historical_generated_at": summary["generated_at"],
    "input_fingerprint": hashlib.sha256(raw).hexdigest()[:12],
    "policy": policy,
    "true_gate_count": sum(gates.values()),
    "gate_count": len(gates),
    "false_gates": [name for name, value in gates.items() if not value],
    "all_core": all_core,
    "all_gates": all_gates,
    "recorded_status": summary["status"],
    "recorded_official_complete": summary["official_complete"],
    "audit_release_allowed": release_allowed,
}, ensure_ascii=False, indent=2))
'@
```

### 步骤 B：core 门禁基线

在学员完成预测后再给。命令读取步骤 A 中固定的程序，只把审计判定绑定到 core 门；重点观察收据事实与本次策略判定分别在哪些字段中。

```powershell
$env:D42_GATE = 'core'
$d42Probe | python -m uv run --no-sync --extra ch4 python -
```

**真实证据**：待学员运行并贴回。需记录命令、退出状态、指纹和关键字段；输出只是对历史收据的本地审计，不是外部工具的新执行证据。

## 4. 逐段读冻结收据

每轮只展示一个片段，等学员解释后继续：

| 层次 | 冻结收据 / 字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| campaign 输入 | `campaign_id`、`generated_at`、文件哈希 | 本次审计读取的是哪份历史快照 | 今天重新执行了 campaign | 待回答 |
| MCP 传输 | `receipts/16_calendar_preflight.json` 的 `mcp_result_is_error` | MCP 调用是否以协议错误返回 | payload 业务操作成功 | 待回答 |
| 工具 payload | 同一收据的 `payload.success` 与 `error` | 工具实现报告的业务结果 | 外部状态已按目标改变 | 待回答 |
| 局部门禁 | `gates.<name>` | 当前门禁规则对对应收据的判断 | 其他门也通过、整体完成 | 待回答 |
| 汇总策略 | `status`、`official_complete`、`blockers` | 冻结脚本如何汇总门禁 | 每项原始业务状态都已独立回读 | 待回答 |

特别核对同一日历收据中“`mcp_result_is_error=false` 与 `payload.success=false` 可以同时存在”；这两项回答的是不同问题，并不矛盾。

## 5. 追到关键源码

**源码锚点**：`vendor/ai-agent-book/chapter4/execution-tools/run_experiment_4_4.py:187-197,210-217,227-244`，合计约 35 行，课堂分三小段阅读。

1. `187–197`：多个原始字段怎样组合成 linter、文件编辑和路径逃逸门。
2. `210–217`：长输出、外部工具与日历/邮件门分别检查什么。
3. `227–244`：怎样计算 core、`passed / blocked / failed`、`official_complete` 与 `blockers`。

**输入 → 处理 → 输出**：receipts、LLM receipts 与外部见证 → 各门布尔值 → core/all 汇总 → summary JSON。

**版本边界**：冻结 `summary.json` 的 `experiment` 字段是 `4-3`，当前脚本写出的是 `4-4`；因此当前源码可用于理解现行汇总机制，但不能冒充生成这份旧收据时的精确源码快照。若要做可复现审计，还需要对应提交或把生成脚本版本写进 manifest。

**学员解释**：待回答。

## 6. 单变量对照

- 唯一改变的条件：`D42_GATE: core → all`。
- 保持不变：同一文件、同一指纹、同一 gates、同一 core 名单和同一程序。
- 对照前预测：待回答。

```powershell
$env:D42_GATE = 'all'
$d42Probe | python -m uv run --no-sync --extra ch4 python -
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据字段与源码讨论 | 若要把某个外部门改为可选，应怎样修改规则并增加哪类审查 |

先核对指纹和所有事实字段是否相同，再解释策略字段的差异。两次输出只证明这份审计程序在固定历史输入上的行为，不证明真实外部系统现在可用。

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么 `mcp_result_is_error=false`、`payload.success=true`、外部状态正确和 `official_complete=true` 是四种不同结论？
2. **结果预测**：若只把 `real_email_mutation` 从必需门移到可选门，固定收据下哪些字段应保持不变，哪些汇总字段可能改变？
3. **故障定位**：某次报告 `official_complete=false`，但所有 MCP 调用都非错误；你会怎样从 `blockers` 追到具体 receipt、payload、外部状态与门禁源码？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 协议返回、工具自报、独立业务状态、策略汇总分别属于传输、执行、外部事实和发布判定层，前一层不能自动推出后一层。
2. 输入指纹、原始 gates、通过/失败数量不应改变；core 名单、core 汇总和依赖该策略的判定可能改变。冻结文件中原有 `recorded_*` 不会因本地审计规则改变。
3. 先读 `blockers` 定位失败门，再找该门的定义与输入字段；回到具体 receipt 区分协议错误和 payload 失败；副作用类工具另查独立外部状态，最后确认汇总规则是否把该门列为必需。

</details>

卡住时用 Android 发布流水线的“测试用例绿灯—测试套件—发布门—线上状态”换例复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选 AI 代码演示

本课不安排 Kotlin 或 Python 编程作业。审计程序由讲师提供，学员负责预测、运行、解释证据边界。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展。
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展。
- 实验命令、证据位置与必要真实摘录：待运行。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 本次观察、假设、待验证点：教材冻结收据不是本机当前外部系统状态；当前源码与冻结收据存在实验编号漂移，需限制源码归因。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`、`tool-discovery/SKILL.md`、`mcp-skill-hub/SKILL.md`。
- 进度处理：授课结束或中断后追加 Day 42 日志；仅备课不追加日志。
