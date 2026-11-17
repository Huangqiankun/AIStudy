# Day 30 — Agentic RAG 怎样围绕证据缺口继续检索

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 智能体化 RAG（第 542–574 行）
> 实验 `vendor/ai-agent-book/chapter3/agentic-rag/`
> 备课日期 2026-11-17 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。尚未运行目标实验，未进行理解检查。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：Agent 怎样根据已有证据决定下一次检索，而我们怎样验证新检索是否补到了缺口？

**类比**：Android 排查故障时，先搜异常，再根据日志出现的模块、请求 ID 或错误码决定下一条搜索。对应 ReAct 的 `Looper.loop()` / `Handler.handleMessage`：本轮观察会影响下一轮行动。类比边界：检索器负责返回材料，Agent 负责提出查询和决定是否继续；循环次数本身不代表问题解决。

**最小概念**：固定 RAG 通常先检索再生成；Agentic RAG 把检索作为工具，允许模型看到结果后调整查询、补查或结束。模型仍可能漏查、重复查或过早停止。

```text
用户问题 → 模型提出检索 → Harness 执行检索 → 结果进入下一轮请求
                ↑                              ↓
                └────── 继续补查 ← 模型判断 ────┤
                                               └→ 回答
```

**实验全景预告**：

- 任务：在教材固定语料与标注上，比较原问题检索与预设子查询检索的证据覆盖。
- 学员运行：离线比较程序；随后保持检索次数不变，只把子查询内容改为重复原查询。
- 产物：控制台与临时目录里的 JSON，包含 `gold_articles`、`covered`、`recall`、`num_searches`。
- 结束时回答：查询内容是否影响证据覆盖，以及离线覆盖结果距离真实 Agent 自主补查的证明还缺什么。

**确认问题**：排查崩溃时，第一次日志只给出了某个模块名，你会根据什么决定下一条搜索？

## 2. 实验前预测

- 输入：`evaluation/offline_qa.json` 的 7 个案例与 `laws/` 语料；重点剖析 `hard_4`。
- 固定：同一 BM25 实现、语料、问题、gold、每次 `top_k=5`。
- 不调用 LLM；`subqueries` 来自数据文件，不能当成本次模型生成记录。
- 预测问题：原问题一次检索与预设子查询检索，证据覆盖可能怎样变化，代价是什么？
- 学员预测：待回答。

## 3. 运行实验

备课于 2026-11-17 实际执行 `python -m uv run --extra ch3 --no-sync python compare_offline.py --help`，退出码 0。核实 `--dataset`、`--corpus`、`--top-k`、`--output`。只核对 CLI，未运行目标实验。使用现有环境与 `--no-sync`，不新增依赖；离线入口无需 provider/model 参数。

每次只给当前步骤的一个代码块，先说明数据流，再等待真实输出。

**步骤 A：准备环境**。指定实验工作目录、UTF-8 和本地代理绕过；建立临时收据目录，不改教材文件。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\agentic-rag'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$d30Dir = Join-Path $env:TEMP 'aistudy-d30-agentic-rag'
New-Item -ItemType Directory -Force -Path $d30Dir | Out-Null
```

**步骤 B：基线**。读取固定语料和问题，分别执行原查询与预设子查询检索，按 chunk ID 去重，按条号计算覆盖。观察各题的 `covered/recall/num_searches`，不把打印的固定结论文案当作证据。

```powershell
python -m uv run --extra ch3 --no-sync python compare_offline.py --dataset evaluation/offline_qa.json --corpus laws --top-k 5 --output (Join-Path $d30Dir 'baseline.json')
```

**真实证据**：待学员运行并贴回输出；完整命令、退出状态与实际产物待记录。

## 4. 逐段读真实轨迹

先看一条问题及其原查询、子查询、gold，再看这条的覆盖与次数；必要时用讲义内最小只读探针打印 `search()` 返回的 `chunk_id/text/score` 和来源。默认收据没有保存全部命中块，不能仅凭 `covered` 判定证据身份正确。

| 片段 | 证据 | 学员解释 |
|---|---|---|
| 数据集查询 | 待展示 | 待回答 |
| 检索结果与覆盖 | 待运行 | 待回答 |
| 真实 Agent 请求与回传 | 本课先核对源码，非本次实跑 | 待回答 |

证据边界：`_covered` 只检查块首的法条编号，没有联合核对法律名称；不同文档相同条号可能被计作命中。教材标注是本实验目标，不能直接视为现实案件所需材料全集。此处只学检索机制，不提供法律判断。

## 5. 追到关键源码

关键源码总量控制在约 40 行，分轮展示：

- `compare_offline.py:34–38`：条号覆盖的判定及局限。
- `compare_offline.py:45–58`：原查询、读取预设子查询、逐次检索、按 chunk ID 去重。
- `agent.py:261–270`：带 `messages/tools` 调用模型。
- `agent.py:324–336`：过滤 `all_results`，组装工具消息，追加后继续循环。

最后一段展示前重新核对精确行号，避免把日志中的全部检索结果当作实际回传正文。离线分解程序与真实 Agent 是两个入口；源码展示不等于真实模型运行证据。

## 6. 单变量对照

唯一改变：`subqueries` 的文本。保持每题子查询数量不变，全部替换为该题的 `naive_query`；问题、gold、语料、top-k、去重逻辑不变。原始比较同时涉及查询内容和检索次数，不能把差异全部归因于次数。

**对照前预测**：检索次数相同，重复原查询与预设子查询的覆盖会相同吗？为什么？待回答。

先生成临时数据集：读取原数据，保留子查询数量，仅替换文本，再输出 UTF-8 JSON。此步骤不检索。

```powershell
$d30Data = Get-Content 'evaluation/offline_qa.json' -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($case in $d30Data.cases) {
    $queryCount = @($case.subqueries).Count
    $originalQuery = $case.naive_query
    $case.subqueries = @(for ($i = 0; $i -lt $queryCount; $i++) { $originalQuery })
}
$repeatPath = Join-Path $d30Dir 'repeat-query.json'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($repeatPath, ($d30Data | ConvertTo-Json -Depth 20), $utf8NoBom)
```

再运行对照，除输入数据集和保存位置外使用相同命令参数；比较重点是两份收据的 `agentic` 字段。

```powershell
python -m uv run --extra ch3 --no-sync python compare_offline.py --dataset (Join-Path $d30Dir 'repeat-query.json') --corpus laws --top-k 5 --output (Join-Path $d30Dir 'repeat-result.json')
```

实测观察、解释假设、待验证判断：均待运行后填写。查询改写质量与候选并集大小仍需按真实结果分析，不能推广为所有 Agent 必然更好。

## 7. 理解检查：每次只问一题

1. 概念：固定 RAG 与 Agentic RAG 的下一次检索分别由谁决定？本课离线程序属于哪种证据？
2. 预测：固定检索器、语料和 top-k，只重复同一个查询，为什么不能以调用次数判断证据增加？
3. 定位：日志显示找到目标段落，模型却说资料不足，接下来先核对哪些字段？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 模型可在观察后选择继续或结束；离线程序只执行预设子查询，不能证明模型自主分解能力。
2. 固定确定性检索可能返回相同结果，按 chunk ID 去重后未必增加覆盖；应核对查询与命中块。
3. 先核对工具返回、过滤后的正文、下一次实际请求，再核对生成使用情况。日志与模型实际收到的内容分别取证。

</details>

## 8. 可选 AI 代码演示

必要时在本讲义提供命中块检查探针，不新增工程，不安排 Kotlin 作业。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下一步：开课三行确认后，建立概念与实验全景，再提一个确认问题。
- 状态：待开展。

## 10. 讲师备注

- 2026-11-17 用户明确要求开始 Day 30；已同步仓库。进度日志最新为 Day 28 完成，Day 29 讲义标记待开展；本次不推定 Day 29 完成，也不自动添加跳课日志。
- 仅核实源码、数据集和 CLI，未代跑目标实验。
- 学员预测、解释与三项理解检查：待开展。
- 备课 skill：`vendor/ai-agent-book/skills/rag-pipeline/SKILL.md`。
- 进度处理：仅备课不追加上课日志；授课结束或中断时按真实情况记录 Day 30。
