# Day 38 — 协作工具如何交接“够用但不过量”的上下文

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 协作工具（第 382–427 行）
> 实验 `vendor/ai-agent-book/chapter4/collaboration-tools/`
> 备课日期 2026-11-25 ｜ 授课日期 2026-11-25 ｜ 状态 ✅ 已完成

本文件是分步教学材料，不一次性贴入对话。实验、单变量对照、源码剖析和三项理解检查已完成。

## 1. 建立概念：交接不是复制整段聊天

**本课只回答**：主 Agent 把子任务交给子 Agent 时，怎样用证据判断移交上下文是否“够用但不过量”？

**类比**：Android 的 `startActivityForResult` / HarmonyOS 页面跳转不会把当前进程的整个对象图塞给下一页，而是通过一个边界清晰的 `Bundle` / 参数对象传递完成目标所需的数据。子 Agent 交接包也是这样：太少会让接收方缺事实，太多会增加 token、干扰和隐私暴露。类比边界是：页面参数通常由开发者确定性选择；`llm_generated` 策略还可能让模型参与挑选与压缩，因此需要额外核验。

```text
父轨迹 / 状态
      |
      v
[交接策略：minimal 或 llm_generated]
      |
      +--> prepared_context（实际准备出的移交文本）
      +--> context_tokens / prep_tokens（交接与准备成本）
      +--> 隐私字段检查
      |
      v
子 Agent 的 user 消息 --> 子 Agent 结果 --> 主 Agent 后续决策
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：把同一笔退款判断子任务交给客服子 Agent。
- 学员会亲自运行：先核对 CLI，再分别运行 `minimal` 基线和只改变交接策略的 `llm_generated` 对照。
- 固定条件：同一任务、父上下文、角色、同步模式和隐私规则；清空当前终端中的在线 LLM Key，使两组都走教材明确标注的离线确定性路径。
- 会看到的证据：`prepared_context`、`context_tokens`、`prep_tokens`、`prompt_tokens`、`context_notes` 和离线占位 `result`。
- 结束时要能回答：哪些证据能证明“准备并放入了什么上下文”，哪些证据仍不能证明“真实模型理解并采用了什么”。

**确认问题**：如果把父 Agent 的整段轨迹原样交给子 Agent，除了 token 变多，还可能带来哪一种风险？

## 2. 实验前预测

- 任务与输入：根据订单事实和业务规则判断退款是否可自动批准；父上下文同时含订单事实、业务规则和支付信息。
- 固定条件：Python 3.12 / `ch4` 依赖、同步模式、同一角色、同一 task、同一 parent context；本课明确关闭在线 LLM Key。
- 唯一改变：`--strategy minimal` → `--strategy llm_generated`。
- 观察点：两组 `prepared_context` 的事实覆盖、敏感信息、token 数与离线标记；不把 `status=completed` 或占位 `result` 当作真实模型完成业务判断的证据。
- 预测问题：两组分别会收到哪些事实？哪组可能更贵？支付信息会不会进入交接文本？现有输出能否证明真实子 Agent 已正确判断退款？请说明理由。
- 学员预测：`minimal` 只保留业务规则；`llm_generated` 保留订单事实与业务规则，后者在字段层面足以支持退款判断。实测与该预测一致；支付字段过滤和真实模型调用另按证据核对。

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行。每次只给当前步骤的一个代码块，等待真实输出后再继续。

### 步骤 A：环境准备与 CLI 核对

输入/读取：进入协作工具项目，固定 UTF-8，并清空当前 PowerShell 进程中的在线 LLM 凭据。处理：由 CLI 解析器列出真实子命令和参数。输出/用途：确认存在 `subagent spawn`、两种 strategy、parent context 与 minimal slice 参数；帮助文本不是目标实验结果。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\collaboration-tools'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$env:OPENAI_API_KEY = ''
$env:OPENROUTER_API_KEY = ''
python -m uv run --extra ch4 python main.py subagent spawn -h
```

### 步骤 B：运行 minimal 基线

输入/读取：任务、逐行父上下文、手选的业务规则文本和隐私规则。处理：`minimal` 只把手选文本构造成带来源标签的交接内容，再走离线确定性子 Agent 回合。输出/用途：观察实际交接文本、token 数与离线说明。

```powershell
$parentContext = @'
case_facts: order_id=A12345, amount_yuan=299, within_days=7, vip_level=gold
business_rules: 7 天内、金额小于 500 元、gold 会员可自动批准退款。
payment_info.card_number: 6222-0000-1111-2222
payment_info.cvv: 123
'@

$minimalSlice = 'business_rules: 7 天内、金额小于 500 元、gold 会员可自动批准退款。'

python -m uv run --extra ch4 python main.py subagent spawn `
  --task '根据订单事实与业务规则，判断这笔退款是否可自动批准，并说明依据。' `
  --strategy minimal --mode sync --role '退款审批子 Agent' `
  --parent-context $parentContext `
  --minimal-slice $minimalSlice `
  --business-rules '只保留任务直接相关事实，不传递支付卡号、CVV、密码或令牌。'
```

**准备观察什么**：`context_strategy` 是否生效；`prepared_context` 实际出现哪些事实；`context_tokens` 与 `prep_tokens`；`context_notes` 是否标明策略；`result` 是否明确标注离线占位。

### 步骤 C：运行 llm_generated 单变量对照

输入与其他参数完全不变。处理只把 strategy 改为 `llm_generated`；由于在线 Key 已清空，教材会明确走“规则式离线摘要”替身，而非冒充真实 LLM。输出/用途：比较同一父上下文经过另一种策略后，交接文本的事实覆盖、敏感信息与成本字段如何变化。

```powershell
python -m uv run --extra ch4 python main.py subagent spawn `
  --task '根据订单事实与业务规则，判断这笔退款是否可自动批准，并说明依据。' `
  --strategy llm_generated --mode sync --role '退款审批子 Agent' `
  --parent-context $parentContext `
  --minimal-slice $minimalSlice `
  --business-rules '只保留任务直接相关事实，不传递支付卡号、CVV、密码或令牌。'
```

**真实证据（2026-11-25，学员 PowerShell）**：

- 首次按 JSON 字段列表传 `--minimal-slice '["business_rules"]'` 时，`prepared_context` 出现字面量 `[business_rules]`，没有取得字段值；这是 PowerShell → Python CLI 的嵌套引号传参失真，不能作为策略对照。改用工具同样支持的纯文本 parent context 与手选文本后重跑。
- 有效 `minimal`：`context_tokens=89`、`prep_tokens=0`、`prompt_tokens=0`；`prepared_context` 只有 task 与完整业务规则，没有订单事实或支付信息。
- 有效 `llm_generated` 离线替身：`context_tokens=130`、`prep_tokens=0`、`prompt_tokens=0`；`prepared_context` 含订单事实与业务规则，没有卡号/CVV 行，并明确标注“规则式离线摘要生成、未调用 LLM”。
- 两组 `result` 都明确标注离线模拟与占位结论。控制台没有真实 provider 请求收据，因此不能证明模型收到、理解或采用交接内容。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 策略输入 | 两组分别回报 `minimal` / `llm_generated` | CLI 实际采用的 strategy 与参数 | 策略内部一定正确 | 能区分生命周期状态与业务完成 |
| 交接文本 | minimal 89 tok；离线摘要 130 tok | 程序准备并保存了哪些移交内容 | 在线模型实际收到、理解或使用 | 能按订单事实、规则与隐私字段检查充分性 |
| 消息桥接 | 源码第 405–412 行 | prepared context 被放进子 Agent `user` 消息对象 | 该对象已发到 provider | 明确还需实际 request/messages 收据 |
| 子 Agent 结果 | `prompt_tokens=0`，离线占位 `done` | 离线桩返回且本地状态更新 | 真实模型完成了退款判断 | 不再把 `success/completed` 当业务成功 |

分别核对：交接策略执行、交接文本形成、消息对象组装、provider 请求、子 Agent 结果、主 Agent 后续采用。缺少后两层证据时不跨层下结论。

## 5. 追到关键源码

**源码锚点**：`vendor/ai-agent-book/chapter4/collaboration-tools/src/subagent_tools.py`。

分三小段读，总计约 33 行：

- 第 198–208 行：`minimal` 如何把 task 与手选切片拼成 `context_text`，并返回 token/准备成本。
- 第 227–240 行：无在线 Key 时，`llm_generated` 如何降级为明确标注的规则式离线摘要。
- 第 405–412 行：`prepared_context` 如何进入记录，并被放进子 Agent 的 `user` 消息对象。

输入 → 处理 → 输出：task / parent context / strategy → 选择或摘要 → `prepared_context` 与成本字段 → `messages`。这里能核对“程序准备和组装了什么”；由于第 340–341 行会转入离线回合，本课运行不会生成真实 provider 请求。

学员解释：能说明 `picked` 为空时只保留 task；`prep_tokens=0` 不等于没有本地过滤与格式化；固定字符串规则会漏掉 `pan` 等别名并可能误删含 `password` 的普通行；本地 `messages` 对象不证明 provider 请求已经发生。

## 6. 单变量对照

- 唯一改变：`--strategy minimal` → `--strategy llm_generated`。
- 保持不变：task、parent context、role、mode、minimal slice、business rules、依赖与离线运行条件。
- 对照前预测：minimal 只有业务规则；离线摘要保留订单事实与业务规则，字段层面更充分。
- 真实轨迹与结果：预测命中。minimal 89 tok；离线摘要 130 tok；两组均无支付原值、无模型调用。离线摘要的“充分”只针对交接字段，不代表真实业务判断完成。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 策略变化后交接文本由“task + 规则”变成“task + 订单事实 + 规则”；卡号/CVV 未出现 | 由 `_prepare_minimal_context`、离线 `_offline_summarize_context` 与固定敏感标记解释 | 若接入真实 LLM，生成上下文是否同样完整、无泄漏且能支持正确决策 |

先核对唯一变量确实生效。即使离线 `result.status` 为 `done`，也只代表确定性桩按约定返回，不把它计作真实子 Agent 的业务成功。

## 7. 理解检查：每次只问一题

1. **概念复述**：协作工具为什么不能只把“spawn 成功”当任务完成？本课的交接证据链有哪些层？
2. **结果预测**：若 minimal 的手选字段从 `["business_rules"]` 改为 `["case_facts","business_rules"]`，哪些输出可以有依据地预测，哪些仍不能预测？
3. **故障定位**：控制台显示 `status=completed`，但子 Agent 的业务判断明显缺少订单金额。第一步查哪个字段；要证明真实模型确实收到了该字段，还缺哪类收据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 启动/完成状态、交接文本、消息对象、provider 请求、模型响应、主 Agent 采用是不同层；协作工具还要管理消息、状态与取消，不能把生命周期标签等同业务正确。
2. 可预测 `prepared_context` 会加入两个手选字段、`context_tokens` 通常变化；不能仅凭离线桩预测真实模型的结论、用法或真实计费 token。
3. 先查 `prepared_context` 是否含金额，再查发往 provider 的实际 request/messages 收据；仅有本地 record 或占位 result 不证明网络请求发生。

</details>

## 8. 可选 AI 代码演示

默认不布置编程作业。本课使用教材已有 CLI 与源码，不另建工程、不修改 `vendor/`。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分交接文本、本地消息对象、provider 请求、模型响应、主 Agent 采用与业务完成；能根据 `prepared_context` 判断字段充分性，并识别离线替身的证据边界。
- 尚未理解及证据：无阻塞性缺口。初始把订单号视为审批条件，后能区分定位字段与金额/天数/会员等级三项决策条件；初始误判固定关键词能识别 `pan` 卡号，经逐行匹配换例后修正。
- 下次从哪一小步继续：Day 39 主动工具发现。
- 状态：✅ 已完成。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-25，完成实验、对照、源码与三项检查。
- 实验命令、证据位置与必要真实摘录：学员 PowerShell 控制台；minimal `89/0/0`，离线摘要 `130/0/0`（依次为 context/prep/prompt tokens）；两组结果均明确为离线占位。
- 学员的原始预测与后续解释：预测 minimal 只留规则、离线摘要保留订单事实与规则并在字段层面足以判断，实测命中；能解释为什么生命周期完成不等于真实模型或业务完成。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过。
- 本次观察、假设、待验证点：实测离线规则按固定字符串逐行过滤，`card_number`/`cvv` 行未进入交接文本；源码推导 `pan` 别名可能漏过滤、含 `password` 的普通行可能误删。未验证真实 LLM、provider 请求/响应、真实计费 token 或主 Agent 采用结果。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`。
- 进度处理：已向 `docs/progress.md` 追加 Day 38 完成日志。
