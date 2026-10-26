# Day 11 — 怎样区分两级缓存，并把缓存命中换算成成本

> 章节 `vendor/ai-agent-book/book/chapter2.md` § KV Cache 的原理与约束、KV Cache 与 Prompt Cache：两个层级的缓存（第 526–558 行）
> 实验 `vendor/ai-agent-book/chapter2/kv-cache/`
> 备课日期 2026-10-26 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 0. 证据边界

- 本课使用目录中保存的 Kimi 历史实测结果，`--report` 只离线读 JSON，不发起模型请求。
- `prompt_tokens` / `cached_tokens` 来自历史 API 响应，是历史实测观察；不是本次 Ollama 运行的结果。
- `Bill.Tok` / `Save%` 是 Harness 用实测 token 和人为给定的 `--cache-price-ratio` 算出的示意值，不是服务商账单。
- 同一份 JSON 改变计费比例，可以形成成本公式的单变量对照；不能用来证明当前任何服务商的真实价格。

## 1. 建立概念：同一份 K/V，为什么有两种缓存名字

**本课只回答**：怎样区分单次推理内的 KV Cache、跨 API 请求的 Prompt Cache，并判断表格里的哪些数是实测、哪些是估算？

**类比**：KV Cache 像 Compose 一次活跃 Composition 内的 `remember` 状态，服务于同一段持续计算；Prompt Cache 像服务端按稳定 key 管理的共享 `LruCache`，新的请求只有 key（这里是 token 前缀）匹配时才能取回复用。类比边界是：真实缓存保存的是 Transformer 各层的 K/V 张量，不是 Kotlin 对象；Prompt Cache 复用的也是既有前缀的 K/V，而不是最终回答文本。

```text
同一次模型推理
[已有 token 的 K/V] --KV Cache--> 生成下一个 token，继续追加 K/V

请求 A 的稳定前缀 --首次计算并保存 K/V--┐
                                      ├-- Prompt Cache（跨请求匹配前缀）
请求 B 的相同前缀 --直接读取既有 K/V-----┘

API usage 中的 prompt_tokens / cached_tokens
                 |
                 v
Harness + 计费比例假设 alpha --> Bill.Tok / Save%（示意，不是账单）
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：用一份已保存的真实缓存收据生成离线成本报告，再只改计费比例生成对照报告。
- 学员会亲自运行：一次 `--report --cache-price-ratio 0.1` 基线，以及一次只把比例改为 `0.5` 的对照。
- 会得到的产物：两张控制台表；表中同时有实测 token、缓存比例和派生成本列，不会新增模型调用产物。
- 结束时要能回答：两级缓存分别跨越什么边界，`cached_tokens` 的证据来自哪里，改价格假设时哪些列应保持不变。

**确认问题**：如果请求 A 已经结束，请求 B 仍复用了 A 的相同前缀，这次复用应归到 KV Cache 机制本身，还是跨请求的 Prompt Cache 层？请说明你判断所依据的“生命周期边界”。

## 2. 实验前预测

- 任务与输入：读取 `result_correct_20260718_kimi_k2_6.json`，不发模型请求。
- 固定条件：同一 JSON、同一报告代码、同一工作目录；只改变计费比例。
- 观察点：`Prompt`、`Cached`、`Hit%`、`Cache%`、`Bill.Tok`、`Save%`。
- 预测问题：把 `--cache-price-ratio` 从 `0.1` 改为 `0.5` 后，哪些列应不变，哪些列应变化？为什么？
- 学员预测：待回答

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行；一次只给当前步骤的一个代码块，等待学员贴回真实输出后再给下一步。

**步骤 A：进入目录并设置当前 PowerShell 环境**

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\kv-cache'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 B：核对 CLI**（收到步骤 A 的输出后再给）

```powershell
python -m uv run --extra ch2 python main.py --help
```

**步骤 C：运行离线基线**（完成预测后再给）

```powershell
python -m uv run --extra ch2 python main.py --report --input result_correct_20260718_kimi_k2_6.json --cache-price-ratio 0.1
```

**准备观察什么**：先区分数据源行与指标表，再把表中列分成 API 历史实测、由实测值直接求比率、由价格假设派生三组。

**真实证据**：待学员运行并贴回输出。记录完整命令、退出状态与数据来源；不把历史收据表述为本次模型调用。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 已能证明 | 仍不能证明 | 学员解释 |
|---|---|---|---|---|
| JSON 收据 | 待运行后定位 | 历史 API 响应记录了哪些 token 指标 | 本次机器刚发生了缓存读取 | 待回答 |
| 离线读取 | 待运行后定位 | `--report` 的数据来源与文件数 | 服务商当前价格 | 待回答 |
| 指标表 | 待运行后定位 | 各字段经过何种计算 | 单次 TTFT 差异必由缓存导致 | 待回答 |
| 成本列 | 待运行后定位 | 给定 alpha 下的透明估算 | 真实账单金额 | 待回答 |

## 5. 追到关键源码

**源码锚点**：

1. `vendor/ai-agent-book/chapter2/kv-cache/agent.py:699-726`：从 API `response.usage` 累加 `prompt_tokens`、`completion_tokens`、`cached_tokens`，并记录 hit / miss。
2. `vendor/ai-agent-book/chapter2/kv-cache/main.py:81-92`：`_billable_tokens()` 用实测 prompt/cached 与外部给定比例计算示意性的可计费 token。
3. `vendor/ai-agent-book/chapter2/kv-cache/main.py:95-118`：`print_comparison_table()` 分别计算 `Cache%`、`Bill.Tok` 与 `Save%` 并注明非真实报价。
4. `vendor/ai-agent-book/chapter2/kv-cache/main.py:147-179`：`run_report()` 只加载保存的结果文件并打印报告，不请求模型。

分小段阅读，总量控制在约 20–40 行。

- 输入 → 处理 → 输出：API usage 或历史 JSON → 指标归一化与公式计算 → 控制台对比表。
- 与证据连接的关键行：`cached_tokens` 的采集点与 `_billable_tokens()` 的公式必须分开看。
- 如果只改变价格比例：API 历史实测列不应改变，派生成本列应按同一公式重新计算。
- 学员解释：待回答

## 6. 单变量对照

- 唯一改变的条件：`--cache-price-ratio` 从 `0.1` 改为 `0.5`。
- 保持不变：输入 JSON、报告代码、模式、历史 token 数据与工作目录。
- 对照前预测：待回答
- 对照命令：

```powershell
python -m uv run --extra ch2 python main.py --report --input result_correct_20260718_kimi_k2_6.json --cache-price-ratio 0.5
```

- 真实结果：待运行

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据公式与源码讨论 | 若要映射为真实金额，需查当前服务商的价格、缓存政策和计价单位 |

## 7. 理解检查：每次只问一题

1. **概念复述**：KV Cache 与 Prompt Cache 分别解决哪一段重复计算，生命周期边界在哪里？
2. **结果预测**：若 `prompt_tokens` 与 `cached_tokens` 不变，只把缓存读取单价比例调高，`Cache%`、`Bill.Tok`、`Save%` 各会怎样？
3. **故障定位**：报告显示 `Save%` 很高，但账单没有下降。你先核对哪三层证据，为什么不能直接怪缓存未命中？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. KV Cache 是单次推理内对既有 token K/V 的复用；Prompt Cache 是推理引擎跨 API 请求匹配稳定前缀并复用既有 KV。两者都依赖前缀不变，但跨越的请求边界不同。
2. `Cache% = cached/prompt` 不变；缓存读取变贵时 `Bill.Tok` 增大、`Save%` 减小。应说明这是给定公式下的条件性预测。
3. 先核对 API 是否真实返回 `cached_tokens`，再核对 Harness 是否正确采集与汇总，最后核对当前服务商实际计费政策、价格与账单口径；报告中的成本列只是估算。

</details>

## 8. 可选 AI 代码演示

默认不安排代码练习。若公式仍抽象，只用纸面数值手算一次 `_billable_tokens`，不新增文件、不改 vendor。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、历史模型、证据位置与必要真实摘录：待运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：未安排
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/kv-cache-design/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 11 日志；仅备课与 `--help` 核对不追加上课日志。
