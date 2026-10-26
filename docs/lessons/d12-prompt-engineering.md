# Day 12 — 怎样用消融实验判断一段提示词是否真的有用

> 章节 `vendor/ai-agent-book/book/chapter2.md` § 提示工程：优化系统提示词（第 602–730 行）
> 实验 `vendor/ai-agent-book/chapter2/prompt-engineering/`
> 备课日期 2026-10-26 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 0. 证据边界

- 本课先离线读取仓库内已完成的 Kimi K3 Tau-Bench 实验收据，不发起新的模型请求，也不把历史收据冒充为本次运行。
- 历史 campaign 固定了模型、十个 task id、协议、seed 等配置；各 arm 的目标差异由 `ablation_config` 记录。
- `reward` 是 vendored Tau-Bench 环境对任务最终状态的客观判分，不能用回复是否礼貌替代。
- 单次十任务 campaign 的结果只说明这批样本的观察，不能直接推出普遍因果；模型与用户模拟器仍有随机性，需复测与统计分析。

## 1. 建立概念：提示词不是文案，而是可执行的员工手册

**本课只回答**：怎样用单变量消融和权威任务结果，判断系统提示词或工具描述中的一部分是否真的帮助了 Agent？

**类比**：像 Android 用 Remote Config 做 A/B 实验。不能因为新版按钮文案“看起来更专业”就判定转化更好；要保持 APK、流量条件和业务流程尽量一致，只切一个配置，并读取服务端订单状态。这里系统提示词像员工 SOP，工具描述像 AIDL 接口旁的用途与参数说明，Tau-Bench 的环境 `reward` 像服务端最终订单断言。类比边界是：LLM 输出有采样随机性，同一 task id 并不保证两次自然语言轨迹逐字相同。

```text
相同模型 + 相同任务集 + 相同协议
                 |
        +--------+--------+
        |                 |
  baseline           no_tool_desc
  完整工具描述         description 置空
        |                 |
        +--------+--------+
                 |
      环境最终状态 reward + 原始轨迹
                 |
       观察差异，而非先验宣布因果
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：读取同一历史 campaign 中的 baseline 与 `no_tool_desc` 两份真实收据，核对消融配置、十个 task 的 reward 和轨迹。
- 学员会亲自运行：一个只读 baseline 收据的 PowerShell 命令，再运行只替换收据文件名的单变量对照命令。
- 会得到的产物：控制台中的配置、reward 向量、通过数；必要时继续下钻某个 task 的 `traj` 与 API receipts。
- 结束时要能回答：什么才是“工具描述有帮助”的证据，为什么一轮小样本即使出现明显差异也不能直接下普遍结论。

**确认问题**：如果两个客服 Agent 的最终回复都很礼貌，但其中一个把数据库中的航班改错了，你会优先用“回复语气”还是 Tau-Bench 的最终环境 `reward` 判断是否完成？为什么？

## 2. 实验前预测

- 任务与输入：同一历史 campaign、同一十个 airline task id；比较 baseline 与只清空工具及参数 `description` 的 arm。
- 固定条件：action model/user model 均为 `kimi-k3`，provider、任务集、协议、temperature、seed 与最大步数相同。
- 目标单变量：`remove_tool_descriptions: false → true`；工具名称、JSON schema 结构和执行实现保留。
- 观察点：`ablation_config`、每个 task 的 `reward`、成功数、任务错误、原始 `traj` 和 API receipts。
- 预测问题：你预测这十个任务中，对照组的通过数会更高、更低还是相同？理由是什么？哪些结果会让你修改解释，但仍不足以证明普遍因果？
- 学员预测：待回答。

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行；一次只给当前步骤的一个代码块，等待学员贴回真实输出后再给下一步。

**步骤 A：进入目录并设置当前 PowerShell 环境**

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\prompt-engineering'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 B：核对 CLI**（收到步骤 A 的输出后再给）

```powershell
python -m uv run --extra ch2 python run_ablation.py --help
```

**步骤 C：读取 baseline 历史收据**（完成预测后再给）

```powershell
$p = 'runs\exp2-4-kimi-k3-20260730-v7\tool-calling-kimi-k3-baseline_0730121451.json'; $r = Get-Content -Raw $p | ConvertFrom-Json; $rewards = @($r.results | ForEach-Object { [double]$_.reward }); [pscustomobject]@{ Source = $p; Config = ($r.ablation_config | ConvertTo-Json -Compress); Tasks = $rewards.Count; Successes = @($rewards | Where-Object { $_ -eq 1 }).Count; Rewards = ($rewards -join ',') }
```

**准备观察什么**：先确认读的是历史收据，再确认该 arm 的三个消融开关，最后看 reward；不要先用 README 的“预期排序”代替实测。

**真实证据**：待学员运行并贴回输出。记录完整命令、输出与文件路径；不把离线读取说成本次模型调用。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。以下区域在课堂中按真实输出补充。

| 片段 | 真实摘录 / 证据字段 | 已能证明 | 仍不能证明 | 学员解释 |
|---|---|---|---|---|
| 实验配置 | `run_config`、`ablation_config` | 两个 arm 的目标配置差异 | 实际送给每轮模型的内容逐字符合预期 | 待回答 |
| 任务判分 | `results[*].reward`、`reward_info` | 该历史任务的环境验收结果 | 差异一定由唯一变量造成 | 待回答 |
| 模型轨迹 | `results[*].traj` | 模型收到的 system/user、回复与工具调用路径 | reasoning 文本等于真实内部思维 | 待回答 |
| API 收据 | `agent_api_records`、`user_api_records` | 有真实 provider 响应与 usage 记录 | 十任务结果可推广到所有任务和模型 | 待回答 |

分别核对：目标消融是否真的作用到请求、Harness 执行了什么、工具结果与环境状态怎样、最终 reward 是什么。若两个 arm 的用户模拟消息并非逐字一致，把它记录为随机性与复测需求，不把它藏掉。

## 5. 追到关键源码

**源码锚点**：

1. `vendor/ai-agent-book/chapter2/prompt-engineering/run_ablation.py:386-412`：分别应用 wiki 随机化、语气修改、工具描述移除，然后构造同一个 `AblationAgent`。
2. `vendor/ai-agent-book/chapter2/prompt-engineering/run_ablation.py:543-550`：六个 arm 的配置矩阵；baseline 与 `no_tool_desc` 只在一个布尔值上不同。
3. `vendor/ai-agent-book/chapter2/prompt-engineering/ablation_utils.py:124-163`：递归把所有 `description` 置空，深拷贝避免修改原始 schema，名称和其他结构保留。
4. `vendor/ai-agent-book/chapter2/prompt-engineering/analyze_results.py:164-203`：从 reward 计算各 arm 统计并相对 baseline 比较。

关键片段总量约 20–40 行，按“配置矩阵 → 变量如何生效 → reward 怎样汇总”分三轮读取，不一次倾倒。

- 输入 → 处理 → 输出：环境原始 wiki/tools + arm 配置 → 仅应用指定消融 → Agent 轨迹、环境 reward 与实验收据。
- 与证据连接的关键行：`remove_tool_descriptions()` 是否只清空 `description`，以及 `ABLATION_SUITE` 是否保持其他开关一致。
- 如果发现对照组表现反而更好：先查目标变量、任务覆盖、随机轨迹、错误收据和重复次数，不改写假设迎合结果。
- 学员解释：待回答。

## 6. 单变量对照

- 唯一目标改变：读取 `no_tool_desc` arm；其配置应为 `remove_tool_descriptions=true`，另外两个消融开关仍与 baseline 相同。
- 保持不变：campaign、模型、provider、十个 task id、protocol、temperature、seed、max steps 与统计口径。
- 对照前预测：待回答。
- 对照命令（与 baseline 只替换收据文件名）：

```powershell
$p = 'runs\exp2-4-kimi-k3-20260730-v7\tool-calling-kimi-k3-no_tool_desc_0730121452.json'; $r = Get-Content -Raw $p | ConvertFrom-Json; $rewards = @($r.results | ForEach-Object { [double]$_.reward }); [pscustomobject]@{ Source = $p; Config = ($r.ablation_config | ConvertTo-Json -Compress); Tasks = $rewards.Count; Successes = @($rewards | Where-Object { $_ -eq 1 }).Count; Rewards = ($rewards -join ',') }
```

- 真实轨迹与结果：待学员运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据配置、reward 与轨迹讨论 | 用更多 seeds/trials 与置信区间检验是否稳定 |

对照若不符合预测，保留原始预测并下钻 task 级证据；不能挑选符合课本方向的一次，也不能因为单轮结果反直觉就宣布工具描述无用。

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么系统提示词更像“可执行员工手册”而不是文案？消融实验中什么事实才算行为证据？
2. **结果预测**：若只清空工具 `description`，名称和参数 schema 都保留，你预计模型可能在哪些环节先出现变化？为什么结果也可能暂时不降？
3. **故障定位**：一次十任务实验里 `no_tool_desc` 通过数高于 baseline。你会按什么顺序核对配置、轨迹、环境 reward 与统计充分性？哪些结论暂时不能下？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 提示词定义角色、规则、约束和流程；是否有效要看模型动作、Harness 执行与环境最终状态，而非只看措辞或最终回复。
2. 可能先改变工具选择、参数理解和调用顺序；名称/schema、本身模型能力、任务未覆盖描述价值、采样随机性都可能让小样本暂时不降。
3. 先确认 arm 只改目标变量且实际作用到请求，再核对同一任务的完整轨迹与权威 reward，然后排除 task/API 错误，最后检查重复次数、seed 与置信区间；一次小样本不能证明描述有害或无用。

</details>

卡住时换成“只保留 AIDL 方法名和类型、删除注释”的小例子复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选 AI 代码演示

默认不安排编程作业。本实验已有完整框架与收据；若统计口径仍抽象，只用 PowerShell 对 reward 向量做计数，不新增文件、不改 vendor。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：待学员运行；历史 campaign 路径为 `runs/exp2-4-kimi-k3-20260730-v7/`
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：未安排
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 12 日志；仅备课和开场不追加上课日志。
