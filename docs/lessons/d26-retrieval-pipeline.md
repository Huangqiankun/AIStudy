# Day 26 — 检索管道：为什么必须逐阶段看证据

> 章节 `vendor/ai-agent-book/book/chapter3.md` § RAG 基础、混合检索（第 243–422 行）
> 实验 `vendor/ai-agent-book/chapter3/retrieval-pipeline/`
> 备课日期 2026-11-12 ｜ 授课日期 2026-11-12 ｜ 状态 已完成

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：为什么检索流水线必须逐阶段检查候选集合与排名，而不能只看最终答案或最终排名？

**类比**：把检索管道看成 Android `PagingSource` 到 `RecyclerView` 的数据流。`PagingSource.load()` 先按查询条件和 `LIMIT` 取候选；后面的合并、排序和 UI 展示只能处理已经返回的对象。若目标对象没有进入这一页，`DiffUtil` 再聪明也不能凭空把它补进列表。类比只说明候选边界，不表示 RRF 等同于 `DiffUtil`。

**最小概念**：检索不是一次“搜完”，而是一条候选逐步收缩和重排的流水线。BM25 先按字面词项召回候选；完整混合路线还会加入稠密召回、融合和神经重排。后续阶段可以改变已有候选的次序，却通常不能找回在更早阶段已被截掉的文档。因此，最终结果错误时必须先定位是“未进入候选”“候选中但排得靠后”，还是“进入最终上下文后未被利用”。

```text
文档 → 分块/建索引 → BM25 候选(top_k) → [稠密 + 融合] → [重排] → 注入上下文 → 生成
                         ↑ 今天隔离观察
                         候选缺失 ≠ 排名靠后 ≠ 生成未采用
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：用一组带人工相关标注的小语料，追踪 BM25 从查询到候选、排名和指标的证据。
- 学员会亲自运行：先跑 `top_k=2` 的纯 BM25 基线，再只把候选上限改为 `top_k=1`。
- 会得到的产物：控制台阶段表，以及 `%TEMP%\aistudy-d26-retrieval\` 中两份完整 JSON 收据。
- 结束时要能回答：候选上限变化发生在哪一层，为什么后续排序阶段不能补回未进入候选池的文档，以及该看哪些字段定位。

**确认问题**：如果某文档在 BM25 的候选集合中完全不存在，后面的重排序器还有没有机会仅靠“重新排序”把它放到第一名？先说结论和理由，不需要术语。

**学员回答（2026-11-12）**：不能仅靠重新排序把它放在第一名，因为它在候选集合中完全不存在。概念边界正确；补充区分：扩大召回数量或新增召回路线可以让文档重新进入候选，但那是改变召回阶段，不是仅靠重排序。

## 2. 实验前预测

- 任务与输入：5 篇本地英文短文、1 条查询 `refund policy`，人工相关文档标为 `refund_rule`；另有一篇包含大量相同关键词但不给出政策内容的干扰文档。
- 固定条件：同一语料、查询、人工标注、BM25 实现、分块参数、`eval_k=3`，关闭稠密检索与神经重排。
- 观察点：BM25 的候选顺序、相关文档是否进入 `top_k`、`recall@3`、MRR，以及 JSON 中 `methods.sparse.top`。
- 预测问题：基线允许 2 个候选，对照只允许 1 个候选；你预计相关文档会保留、被截掉，还是无法判断？理由应落在词项、得分和截断线上。
- 学员预测（2026-11-12）：基线 `top_k=2` 时 `refund_rule` 可能保留；对照 `top_k=1` 时可能被截掉，因为它进不了下一轮。讲师将其精确为待验证假设：关键词堆叠文档可能排在前面，若 `refund_rule` 的实测名次低于第 1，才会被 `top_k=1` 截断；不能只由参数值断言实际排名。

## 3. 运行实验

备课核验：标准 `python -m uv run --extra ch3 ...` 会在本机同步阶段构建 `annoy==1.17.3`，因缺少 MSVC 14+ 失败，尚未进入脚本；Day 25 已建立的章级 `.venv` 可用。`python -m uv run --extra ch3 --no-sync python evaluate.py --help` 已于 2026-11-12 成功，确认下列参数真实存在。课堂继续用 `--no-sync`，不修改 `vendor/`、不新增项目依赖。目标实验尚未由讲师代跑。

### 步骤 A：准备同一份临时语料和标注

输入/处理/输出：在系统临时目录创建 5 篇文档和 1 条带 `expected` 标注的查询。此步只写输入并回读 ID，不执行检索，也不产生结果判断。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\retrieval-pipeline'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

$lessonRoot = Join-Path $env:TEMP 'aistudy-d26-retrieval'
New-Item -ItemType Directory -Force -Path $lessonRoot | Out-Null
$corpus = Join-Path $lessonRoot 'corpus.json'
$queries = Join-Path $lessonRoot 'queries.json'

$corpusData = @(
  @{ doc_id = 'keyword_stuffing'; text = 'refund refund refund policy policy' }
  @{ doc_id = 'refund_rule'; text = 'Customers may request a refund within 30 days under the store policy.' }
  @{ doc_id = 'shipping_rule'; text = 'Standard shipping takes three business days.' }
  @{ doc_id = 'privacy_rule'; text = 'The privacy notice explains how account data is stored.' }
  @{ doc_id = 'support_rule'; text = 'Customer support is available by email.' }
)
$queryData = @(
  @{ query = 'refund policy'; expected = @('refund_rule'); category = 'keyword-stuffing' }
)

[System.IO.File]::WriteAllText($corpus, (ConvertTo-Json -InputObject $corpusData -Depth 5), [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($queries, (ConvertTo-Json -InputObject $queryData -Depth 5), [System.Text.UTF8Encoding]::new($false))

Get-Content -Raw -Encoding UTF8 $corpus | ConvertFrom-Json | Select-Object doc_id
Get-Content -Raw -Encoding UTF8 $queries | ConvertFrom-Json | Select-Object query, expected
```

### 步骤 B：核对 CLI

输入/处理/输出：只让脚本解析参数帮助，核对关闭模型阶段、候选数和 JSON 输出开关；不读取语料、不执行检索。

```powershell
python -m uv run --extra ch3 --no-sync python evaluate.py --help
```

**步骤 A 真实回读与修正（学员运行，2026-11-12）**：首次贴回仅明确显示 `query=refund policy`、`expected={refund_rule}`，随后用紧凑字符串核验得到 5 个文档 ID。首次基线又暴露单元素 `$queryData | ConvertTo-Json` 在当前 PowerShell 中被序列化为对象而非数组，`run_evaluation()` 迭代到字符串键后报 `TypeError: string indices must be integers`，尚未执行查询。准备命令已改为 `ConvertTo-Json -InputObject $queryData`，保留脚本要求的 `[{...}]` 外壳。

修复后学员回读得到 `json_first_char=[`、`query_count=1`、`query=refund policy`、`expected=refund_rule`；查询列表结构已确认，尚未产生检索结果。

### 步骤 C：运行 BM25 基线

输入/处理/输出：读取刚才的语料和人工标注；分块并建立 BM25 索引，只保留最多 2 个候选；输出候选、`recall@3`、MRR、nDCG@3 和分阶段 JSON，用于判断目标是在候选内还是已在召回边界外。

```powershell
$baselineOut = Join-Path $lessonRoot 'lesson-d26-bm25-k2.json'
python -m uv run --extra ch3 --no-sync --with rank-bm25 python evaluate.py `
  --corpus $corpus --queries $queries `
  --no-dense --no-rerank --top-k 2 --eval-k 3 `
  --output $baselineOut
```

**准备观察什么**：控制台的 BM25 汇总行；JSON 中 `per_query[0].methods.sparse.top` 的候选 ID、得分和顺序；相关文档是否出现及其首个排名。不能只看一个总分就归因。

**失败尝试（学员运行，2026-11-12）**：第一次初始化失败，报 `No module named 'rank_bm25'`；隔离诊断确认 uv `--with` 可补齐项目已声明依赖。第二次进入 `run_evaluation()` 后因单条查询 JSON 缺少数组外壳而报 `TypeError`。两次都未调用 `Pipeline.run_query()`，没有候选或指标，不能当作 BM25 结果。

**真实证据（学员运行，2026-11-12）**：第三次基线成功。控制台确认 5 篇文档、5 个 chunk、1 条查询，稠密与重排禁用，`top_k=2`、`eval_k=3`；BM25 得到 `Recall@3=1.0000`、`MRR=0.5000`、`nDCG@3=0.6309`，收据写入 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k2.json`。在单个相关文档的标注下，汇总指标支持相关文档已返回且首个相关排名为第 2；候选第 1 的身份和两者分数仍需读 JSON，不能由汇总表直接确认。

**步骤 B 真实输出（学员运行，2026-11-12）**：`--help` 成功，确认 `--no-dense` 会禁用稠密检索并连带禁用融合与重排，退化为纯 BM25；`--top-k` 控制每路候选数，`--eval-k` 控制 Recall/nDCG 的指标截断位置，`--output` 保存完整结果。参数存在不代表基线已执行。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。以下区域只在实际运行后填写；自造例子必须单独标为“示意”。

| 片段 | 真实摘录 / 证据字段 | 程序执行什么 | 能证明什么 | 学员解释 |
|---|---|---|---|---|
| 输入与标注 | 5 个文档 ID；`refund policy → refund_rule` | 读取 `corpus` / `queries` | 任务与金标准 | 能区分准备输入与已执行检索 |
| BM25 候选 | `keyword_stuffing` 1.1533；`refund_rule` 0.5339 | 分词、打分、按 `top_k` 截断 | 相关文档已召回但排第 2 | 能沿源码定位打分、排序和截断 |
| 指标 | Recall@3=1.0；MRR=0.5；nDCG@3=0.6309 | 用候选与 `expected` 计算 | 相关文档已返回，首个相关排名第 2 | 经换例纠正倒数排名口径 |
| JSON 收据 | `lesson-d26-bm25-k2.json` 已写入并回读 | 保存方法级候选和 trace | 候选明细与汇总指标一致 | 能区分汇总推断与候选直接证据 |

学员首次解读把 `MRR=0.5` 误认为第 1 名；经 `RR=1/rank` 提示，正确口径应为单条查询的相关文档排第 2。学员能指出汇总表不能直接证明第 1 名是 `keyword_stuffing`。关于词频，待补清：BM25 的词频贡献会饱和、边际收益递减，但不是重复完全不影响得分；还受文档长度归一化共同作用，必须读取候选分数验证。

换例复查：给出单条查询 `MRR=0.25`，学员正确回答首个相关文档排第 4，倒数排名口径已纠正。

JSON 回读实测：`keyword_stuffing` 排第 1、分数 1.1533；`refund_rule` 排第 2、分数 0.5339；Recall=1.0、MRR=0.5、nDCG=0.6309。观察支持本次关键词堆叠文档得分更高，但两文档还同时存在长度和其他词差异，不能用该单次收据把分差唯一归因于重复词次数。

本课没有 LLM 生成阶段；JSON 只证明本地评测程序记录了什么。若日后接入生成器，仍需另查检索结果是否实际进入模型请求、模型是否利用。

## 5. 追到关键源码

课堂分小段阅读，总量控制在 20–40 行：

- `evaluate.py:147-190`：`tokenize()` 与 `BM25Retriever.search()`；输入文本/查询，构造 BM25 语料并按得分输出最多 `top_k` 个 chunk。
- `evaluate.py:417-428`：`Pipeline.run_query()` 的稀疏分支；把 chunk 级结果折叠为 doc 级结果，并写入 `out['sparse']`。
- `evaluate.py:509-548`：`run_evaluation()` 的方法级记录；从排名和 `expected` 计算 `recall@k`、MRR、nDCG，并把前五候选写入 JSON。

重点定位：`top_k` 截断发生在指标计算之前；指标只评估实际返回的排名列表。后续如启用融合或重排，其输入仍来自已经召回的候选池。

- 输入 → 处理 → 输出：查询文本与语料 → 分词/BM25 得分/候选截断 → doc 排名与指标。
- 哪一行连接了刚才的轨迹：`BM25Retriever.search()` 的 `ranked[:top_k]` 决定哪些候选还能进入后续链路。
- 如果改变这一行负责的行为，预计哪里先变化：先是 `methods.sparse.top` 的候选集合，然后才可能影响 recall/MRR；须先预测再验证。
- 学员解释：能指出 `get_scores()` 计算、`sorted(... reverse=True)` 排序、`ranked[:top_k]` 截断；能沿 `run_query()` 说明被截掉的候选不会进入 `out['sparse']`。

课堂判断（2026-11-12）：学员选择 B，并解释 BM25 应先形成可比较分数，再排序、按 `top_k` 截断；指标计算不应承担候选删除。随后准确指出 `evaluate.py:184` 计算全体分数、185 排序、186 的 `ranked[:top_k]` 截断。补充细节：正分过滤发生在切片之后，因此输出最多为 `top_k` 个，不保证凑满。结合基线预测 `top_k=1` 只保留 `keyword_stuffing`，已由对照实跑核验。

继续阅读 `evaluate.py:417-425` 后，学员正确判断 `top_k=1` 时只有 `keyword_stuffing` 能进入 `out['sparse']`，并定位 `refund_rule` 在 `self.bm25.search(query, top_k)` 内部被挡；精确位置仍是 `BM25Retriever.search()` 的 `ranked[:top_k]`。

首次只给 `recall_at_k()` / `reciprocal_rank()` 公式片段时，学员指出代码太少、无法判断；缺失的确是 `gold`、`ranked_ids` 的来源和调用位置。随后补读 `evaluate.py:509-537`，把查询 `expected`、`pipeline.run_query()`、候选 ID 列表与两项指标调用接成完整链路。

补齐调用链后，学员正确解释 `gold` 是当前查询的相关文档标注，并算出基线 RR=0.5、对照 RR=0。讲师补全集合交集：基线 `{keyword_stuffing, refund_rule} ∩ {refund_rule}={refund_rule}`，对照 `{keyword_stuffing} ∩ {refund_rule}={}`；同时区分 CLI 检索候选上限 `top_k` 与 `recall_at_k()` 内由 `eval_k` 形成的局部集合变量 `topk`。

## 6. 单变量对照

- 唯一改变的条件：BM25 候选上限从 `top_k=2` 改为 `top_k=1`。
- 保持不变：语料、查询、人工标注、BM25 实现、分块、关闭稠密与重排、`eval_k=3`。
- 对照前预测：学员预测基线可能保留 `refund_rule`、`top_k=1` 时可能截掉；读完基线候选与源码后进一步预测对照只保留 `keyword_stuffing`。
- 对照命令：收到基线真实输出、完成候选解释并预测后再给学员运行。

```powershell
$comparisonOut = Join-Path $lessonRoot 'lesson-d26-bm25-k1.json'
python -m uv run --extra ch3 --no-sync --with rank-bm25 python evaluate.py `
  --corpus $corpus --queries $queries `
  --no-dense --no-rerank --top-k 1 --eval-k 3 `
  --output $comparisonOut
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 基线候选为 `keyword_stuffing:1.1533, refund_rule:0.5339`；对照只剩 `keyword_stuffing:1.1533`；Recall/MRR 从 1.0/0.5 变为 0/0 | 固定输入与实现下，`top_k=1` 在排序后截掉第 2 名相关文档；第一名分数未变 | 可增加候选、改召回方法或改语料做复测；不能由一次小语料推出 `top_k=1` 普遍失败 |

先核对 JSON 中实际配置和候选集合，再解释指标变化。即使指标没有变化，也保留真实结果并检查目标原本排第几，不挑选符合预期的一次。

**对照真实输出（学员运行，2026-11-12）**：5 篇文档、5 个 chunk、同一查询，稠密和重排仍禁用；唯一实验变量显示为 `top_k=1`，`eval_k=3` 不变。BM25 的 Recall@3、MRR、nDCG@3 均为 0，收据写入 `lesson-d26-bm25-k1.json`。该汇总支持实际返回列表未命中人工相关文档，但候选身份仍需回读 JSON。

边界解释：学员正确区分 `top_k` 决定程序实际保留的候选数，`eval_k` 决定指标最多查看现有列表的前几名。补充先后关系：`top_k=1` 先截断，`eval_k=3` 不会返回截断前补取第 2、3 名。

对照 JSON 回读确认：基线候选数 2、对照候选数 1；`keyword_stuffing` 的 1.1533 分数保持不变，`refund_rule:0.5339` 被移出候选；对应 Recall/MRR 从 1.0/0.5 变成 0/0。候选变化与源码切片位置一致。

## 7. 理解检查：每次只问一题

1. **概念复述**：候选召回、候选排序和最终生成分别解决什么问题？为什么必须逐阶段查？
2. **结果预测**：若相关文档在 BM25 第 4 名，而 `top_k` 从 5 改为 3，融合或重排能否仅凭现有候选把它升到第 1？依据是什么？
3. **故障定位**：最终答案错误，但 JSON 显示相关文档在 BM25 第 2、RRF 第 1、重排第 3；你先检查哪一段真实请求或候选，怎样区分“检索排序问题”和“生成未采用”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 召回决定候选是否存在，排序决定候选先后，生成决定如何使用已注入信息；最终错误不能反推具体阶段。
2. 在给定流水线中不能；第 4 名已在 top-3 截断线外，后续只能重排已有候选。可通过扩大候选或另一召回路重新引入，但这不是“仅靠重排”。
3. 先确认重排 top-k 是否仍包含相关文档及哪份列表实际注入模型，再查最终模型请求和回答；阶段排名记录不等于已注入或已采用。

</details>

卡住时记录具体缺口，换小例子补讲，再检查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

**理解检查进度（2026-11-12）**：第 1 题通过。学员能说明召回让可能相关文档进入候选池，重排序只排列候选池中的文档，评测查看检索实际留下的列表；并指出要让缺失文档重新出现需回到召回阶段扩大范围或重新检索。第 2 题通过：相关文档排第 4 而检索 `top_k=3` 时会先被截掉，后续仅靠现有候选不能升到第 1；补充边界是另一召回路线若独立带回它，融合仍可能保留。第 3 题经缩小问题后通过：除检查重排分数、排序方向、内容完整性和最终截断范围，还要核对模型真实请求中是否注入完整正确文档，再结合模型响应区分“未收到”与“收到但未采用”。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell 只创建临时 JSON 输入与收据；学员负责预测、运行和解释，不要求改写 Python，不修改 `vendor/`，不新增依赖。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分检索 `top_k` 与评测 `eval_k`，沿“打分→排序→截断→指标”解释真实候选变化；能定位缺失文档要回到召回阶段，不能靠重排序或指标恢复。
- 尚未理解及证据：本课核心问题无遗留。曾误把 MRR=0.5 当第 1，经公式与换例纠正；故障定位初答漏掉模型请求桥接证据，经缩小问题后补齐。
- 下次从哪一小步继续：Day 27 按 `docs/progress.md` 路线另开；不把本课局部 BM25 结论外推为混合检索的一般结论。
- 状态：✅ 已完成

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-12 完成概念、基线、收据剖析、源码、单变量对照和三项理解检查。
- 实验命令、模型、证据位置与必要真实摘录：纯本地 BM25，无模型。收据位于 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k2.json` 与 `lesson-d26-bm25-k1.json`。基线候选 `keyword_stuffing:1.1533, refund_rule:0.5339`，Recall/MRR=1.0/0.5；对照只剩 `keyword_stuffing:1.1533`，Recall/MRR=0/0。
- 学员的原始预测与后续解释：预测基线可能保留、对照可能截掉，实测支持；能用 `BM25Retriever.search()` 的打分、排序、`ranked[:top_k]` 和指标调用链解释结果。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过（定位题经一次提示补齐模型真实请求证据）
- 是否采用代码演示及原因：未安排；现有 CLI 足够展示机制。
- 本次观察、假设、待验证点：标准 ch3 同步因 `annoy` 缺少 MSVC 失败，`--no-sync --with rank-bm25` 补齐已声明依赖；单元素 PowerShell JSON 序列化曾丢数组外壳，已改用 `ConvertTo-Json -InputObject`。本次局部对照支持候选截断机制，不证明 `top_k=1` 普遍失败，也未运行稠密、融合、重排或生成阶段。
- 对应备课 skill：`vendor/ai-agent-book/skills/rag-pipeline/SKILL.md`
- 进度处理：Day 26 已满足实验、对照、源码与三项检查，追加 `✅` 日志。
