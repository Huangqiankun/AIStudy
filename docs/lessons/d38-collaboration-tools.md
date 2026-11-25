# Day 38 — 协作工具如何交接“够用但不过量”的上下文

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 协作工具（第 382–427 行）
> 实验 `vendor/ai-agent-book/chapter4/collaboration-tools/`
> 备课日期 2026-11-25 ｜ 授课日期 2026-11-25 ｜ 状态 ▶ 进行中

本文件是分步教学材料，不一次性贴入对话。实验与对照尚未由学员运行，理解检查尚未完成。

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
- 学员预测：待回答。

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

输入/读取：任务、父上下文、手选字段列表和隐私规则。处理：`minimal` 只从父上下文挑出 `business_rules`，构造带来源标签的交接文本，再走离线确定性子 Agent 回合。输出/用途：观察实际交接文本、token 数与离线说明；不要从命令说明预判字段的真实值。

```powershell
python -m uv run --extra ch4 python main.py subagent spawn `
  --task '根据订单事实与业务规则，判断这笔退款是否可自动批准，并说明依据。' `
  --strategy minimal --mode sync --role '退款审批子 Agent' `
  --parent-context '{"case_facts":{"order_id":"A12345","amount_yuan":299,"within_days":7,"vip_level":"gold"},"business_rules":"7 天内、金额小于 500 元、gold 会员可自动批准退款。","payment_info":{"card_number":"6222-0000-1111-2222","cvv":"123"}}' `
  --minimal-slice '["business_rules"]' `
  --business-rules '只保留任务直接相关事实，不传递支付卡号、CVV、密码或令牌。'
```

**准备观察什么**：`context_strategy` 是否生效；`prepared_context` 实际出现哪些事实；`context_tokens` 与 `prep_tokens`；`context_notes` 是否标明策略；`result` 是否明确标注离线占位。

### 步骤 C：运行 llm_generated 单变量对照

输入与其他参数完全不变。处理只把 strategy 改为 `llm_generated`；由于在线 Key 已清空，教材会明确走“规则式离线摘要”替身，而非冒充真实 LLM。输出/用途：比较同一父上下文经过另一种策略后，交接文本的事实覆盖、敏感信息与成本字段如何变化。

```powershell
python -m uv run --extra ch4 python main.py subagent spawn `
  --task '根据订单事实与业务规则，判断这笔退款是否可自动批准，并说明依据。' `
  --strategy llm_generated --mode sync --role '退款审批子 Agent' `
  --parent-context '{"case_facts":{"order_id":"A12345","amount_yuan":299,"within_days":7,"vip_level":"gold"},"business_rules":"7 天内、金额小于 500 元、gold 会员可自动批准退款。","payment_info":{"card_number":"6222-0000-1111-2222","cvv":"123"}}' `
  --minimal-slice '["business_rules"]' `
  --business-rules '只保留任务直接相关事实，不传递支付卡号、CVV、密码或令牌。'
```

**真实证据**：待学员运行并贴回输出。控制台输出不含真实 LLM 请求收据；如未来配置真实 provider，还需单独保存和核对 request/response receipt。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 策略输入 | 待运行 | CLI 实际采用的 strategy 与参数 | 策略内部一定正确 | 待回答 |
| 交接文本 | `prepared_context` 待运行 | 程序准备并保存了哪些移交内容 | 在线模型实际收到、理解或使用 | 待回答 |
| 消息桥接 | 结合源码核对 | prepared context 被放进子 Agent `user` 消息对象 | 该对象已发到 provider | 待回答 |
| 子 Agent 结果 | 待运行 | 离线桩返回了什么、状态如何更新 | 真实模型完成了退款判断 | 待回答 |

分别核对：交接策略执行、交接文本形成、消息对象组装、provider 请求、子 Agent 结果、主 Agent 后续采用。缺少后两层证据时不跨层下结论。

## 5. 追到关键源码

**源码锚点**：`vendor/ai-agent-book/chapter4/collaboration-tools/src/subagent_tools.py`。

分三小段读，总计约 33 行：

- 第 198–208 行：`minimal` 如何把 task 与手选切片拼成 `context_text`，并返回 token/准备成本。
- 第 227–240 行：无在线 Key 时，`llm_generated` 如何降级为明确标注的规则式离线摘要。
- 第 405–412 行：`prepared_context` 如何进入记录，并被放进子 Agent 的 `user` 消息对象。

输入 → 处理 → 输出：task / parent context / strategy → 选择或摘要 → `prepared_context` 与成本字段 → `messages`。这里能核对“程序准备和组装了什么”；由于第 340–341 行会转入离线回合，本课运行不会生成真实 provider 请求。

学员解释：待回答。

## 6. 单变量对照

- 唯一改变：`--strategy minimal` → `--strategy llm_generated`。
- 保持不变：task、parent context、role、mode、minimal slice、business rules、依赖与离线运行条件。
- 对照前预测：待回答。
- 真实轨迹与结果：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 由策略分支与过滤源码解释 | 若接入真实 LLM，生成上下文是否同样完整、无泄漏且能支持正确决策 |

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

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展。
- 状态：▶ 进行中。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-25 已开始，待记录。
- 实验命令、证据位置与必要真实摘录：待学员运行；当前环境未配置 OpenAI/OpenRouter Key。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 本次观察、假设、待验证点：已实测 CLI `--help` 可用且 UTF-8 正常；未运行本课目标命令。源码显示无 Key 时使用规则式离线摘要与离线占位回合；真实模型接收与使用仍待未来在线收据验证。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`。
- 进度处理：课程结束或中断后才向 `docs/progress.md` 追加日志；仅建立讲义不记录完成。
