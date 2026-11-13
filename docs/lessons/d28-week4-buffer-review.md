# Day 28 — W4 记忆与检索证据链复盘（buffer）

> 章节：W4 / ch3 buffer 复盘（`vendor/ai-agent-book/book/chapter3.md`）
> 实验：复用 Day 22–27 的真实证据，重点审计 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k*.json`
> 备课日期 2026-11-13 ｜ 授课日期 2026-11-13 ｜ 状态 ✅

本日是 buffer 日，不引入 Day 29 的 Contextual Retrieval，只复盘 W4 已学过的记忆格式、结构化索引、知识抽取、稠密/稀疏检索、检索管道与混合融合。本文是分步教学材料，不一次性贴入对话。

## 1. 建立概念：先找第一处丢失，再评价最终答案

**本课只回答**：一条正确事实已经存在于记忆或知识库中，但最终回答没有出现时，怎样沿“存储 → 表示/索引 → 单路候选 → 融合/重排 → 模型请求 → 最终回答”定位第一处丢失，而不是直接归因给 LLM？

**类比**：Android 中数据库有一行数据，不等于 DAO 查询返回它；DAO 返回它，不等于 Repository 没有过滤；Repository 保留它，不等于 ViewModel 已把它放进 UI state；UI state 有它，也不等于 Composable 实际渲染。记忆与 RAG 同样要逐层检查。类比边界是：这里审计的是数据和证据的流转，不是在讨论 Android 生命周期。

```text
原始事实
   │
   ├─→ 存储表示：Notes / Cards / 抽取字段 / 文档块
   │          “是否真的存下，内容是否忠实？”
   v
索引与检索表示：倒排词项 / 稠密向量 / 图边 / 层次摘要
   │          “是否能被当前查询路线命中？”
   v
单路候选：BM25 top-k / Dense top-k
   │          “相关项是否进入候选，排第几？”
   v
融合与重排：RRF / Weighted / Reranker
   │          “候选是否被截断、换位？”
   v
模型请求 → 最终回答
              “上下文是否真的注入？模型是否采用？”
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：审计同一查询、同一语料、同一 BM25 配置的两份冻结收据，找出相关文档在哪一层第一次消失。
- 学员会亲自运行：先确认两份 Day 26 收据存在，再用一条只读 PowerShell 命令并排比较 `top_k`、人工标注、候选 ID、候选分数和三个指标；随后读 20–40 行关键源码。
- 会得到的产物：一张基线/对照表，以及“配置 → 检索切片 → doc 级结果 → 指标”的源码链；不会调用模型，也不会修改 `vendor/`。
- 结束时要能回答：事实存在、被正确表示、进入候选、留在融合/重排池、进入模型请求、出现在最终回答为什么是六个不同事实；故障定位为什么要找第一处分歧。

**确认问题**：知识库中确实有退款规则，但 Agent 最终没答出来。仅凭这一现象，能否直接判断“LLM 没有利用好上下文”？只回答“能”或“不能”，再说第一步会查哪一层。

## 2. 实验前预测

- 任务与输入：Day 26 的同一条查询 `refund policy`、同一批 5 篇文档和同一人工相关文档 `refund_rule`。
- 固定条件：纯本地 BM25；相同分词、`k1=1.5`、`b=0.75`、查询、语料与 `eval_k=3`；不启用 Dense、融合、重排或 LLM。
- 唯一变化：检索返回候选数 `top_k=2 → 1`。
- 观察点：两份收据中的 `expected`、`methods.sparse.top`、候选分数、Recall@3、MRR、nDCG@3。
- 预测问题：如果正确文档在基线中排第 2，只把 `top_k` 从 2 改为 1，第一处可观察分歧会出现在哪里？后面的指标会怎样变化？先说机制，不背历史结果。
- 学员预测：第一处分歧会出现在候选层；`top_k=1` 会过滤排第 2 的正确文档，Recall、MRR、nDCG 都会向较差方向变化。讲师补充：MRR 是首个相关文档排名倒数，完全未返回时为 0。

## 3. 运行实验

本课复用 Day 26 由学员亲自生成的冻结收据，不重新跑检索。课堂一次只给当前步骤的一段命令，等待真实输出后再继续。

### 步骤 A：确认冻结收据存在

**输入/读取**：定位 `%TEMP%\aistudy-d26-retrieval\` 下的 `top_k=2` 与 `top_k=1` 两份 JSON。

**处理/筛选**：只检查文件是否存在和大小，不解析候选、不调用模型。

**输出/用途**：两个文件对象；只证明本机存在收据，尚不能证明内容或历史运行结论。

```powershell
$lessonRoot = Join-Path $env:TEMP 'aistudy-d26-retrieval'
$paths = @(
    (Join-Path $lessonRoot 'lesson-d26-bm25-k2.json'),
    (Join-Path $lessonRoot 'lesson-d26-bm25-k1.json')
)
$paths | ForEach-Object {
    if (-not (Test-Path $_)) { throw "缺少冻结收据：$_" }
    Get-Item $_ | Select-Object Name, Length, LastWriteTime
}
```

**真实证据**（学员运行，2026-11-13）：两份收据均存在；`lesson-d26-bm25-k2.json` 为 1285 bytes，修改时间 `2026/11/12 11:09:06`；`lesson-d26-bm25-k1.json` 为 1175 bytes，修改时间 `2026/11/12 13:09:42`。这些字段只证明本地文件存在，尚未证明内容或历史结论。

### 步骤 B：并排审计第一处分歧

收到步骤 A 输出、完成预测后再给本步骤。

**输入/读取**：两份收据的配置、人工标注、BM25 候选明细与逐查询指标。

**处理/筛选**：

1. 分别读取基线和对照 JSON。
2. 把每份收据的 `top_k`、`eval_k`、`expected` 和候选 ID/分数压成一行。
3. 并排输出 Recall、MRR、nDCG，定位候选集合与指标谁先变化。

**输出/用途**：区分“权威文档仍在语料/标注中”“检索器是否返回它”和“指标怎样评价返回列表”。

```powershell
$lessonRoot = Join-Path $env:TEMP 'aistudy-d26-retrieval'
$runs = @(
    [pscustomobject]@{ Name = 'top_k=2'; Receipt = (Get-Content -Raw -Encoding UTF8 (Join-Path $lessonRoot 'lesson-d26-bm25-k2.json') | ConvertFrom-Json) },
    [pscustomobject]@{ Name = 'top_k=1'; Receipt = (Get-Content -Raw -Encoding UTF8 (Join-Path $lessonRoot 'lesson-d26-bm25-k1.json') | ConvertFrom-Json) }
)

$runs | ForEach-Object {
    $q = $_.Receipt.per_query[0]
    $top = $q.methods.sparse.top
    [pscustomobject]@{
        Run = $_.Name
        TopK = $_.Receipt.config.top_k
        EvalK = $_.Receipt.config.eval_k
        Expected = ($q.expected -join ',')
        Candidates = (($top | ForEach-Object { '{0}:{1:N4}' -f $_.doc_id, $_.score }) -join ' | ')
        Recall = $q.methods.sparse.'recall@k'
        MRR = $q.methods.sparse.mrr
        NDCG = $q.methods.sparse.'ndcg@k'
    }
} | Format-Table -Wrap -AutoSize
```

**真实证据**（学员回读，2026-11-13）：两份收据的 `Expected` 均为 `refund_rule`，`eval_k` 均为 3。`top_k=2` 返回 `keyword_stuffing:1.1533 | refund_rule:0.5339`，Recall/MRR/nDCG 为 `1.0/0.5/0.6309`；`top_k=1` 只返回 `keyword_stuffing:1.1533`，三个指标均为 0。第一处有害差异位于 BM25 截断后的候选集合，指标变化是下游表现。

## 4. 逐段读真实轨迹

| 层 | 本次直接证据 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 语料/标注 | 两份收据均记录 `expected=refund_rule` | 评测认为哪个文档相关 | 原始文档内容一定忠实、标注一定正确 | 能把人工标注与实际候选分开 |
| 单路候选 | `methods.sparse.top` 的 ID、分数与顺序 | BM25 实际返回了什么 | 未返回文档不存在于语料 | 指出基线已返回 `refund_rule`，对照只是把第 2 名截掉 |
| 指标 | Recall/MRR/nDCG | 当前返回列表相对人工标注的表现 | 候选进入过 LLM 请求或被模型使用 | 能说明 `eval_k` 只评价已有列表，不会取回候选 |
| 融合/重排 | 本实验未启用，对应字段不存在 | 本次没有这两个后续阶段 | 它们若启用就一定能补回缺失候选 | 明确不能归因本次未运行的阶段 |
| 生成 | 本实验没有 LLM 请求/回答收据 | 不能评价生成阶段 | 最终回答会正确或错误 | 明确不能由检索指标倒推 LLM 请求或使用情况 |

本课统一使用六层证据链：事实/原文 → 存储表示 → 索引/单路候选 → 融合/重排候选 → 模型请求 → 最终回答。某层没有收据时必须明确写“未观察”，不能用下游指标倒推。

## 5. 追到关键源码

**源码锚点**（当前 checkout 已核实，课堂仍以文件为准）：

- `evaluate.py:182-186`：`BM25Retriever.search()` 计算分数、排序并用 `ranked[:top_k]` 截断。
- `evaluate.py:417-425`：`Pipeline.run_query()` 把 CLI `top_k` 交给 BM25，再形成 doc 级结果。
- `evaluate.py:509-538`：`run_evaluation()` 读取人工 `expected`，把返回列表交给 Recall/MRR/nDCG。

分三小段阅读，总量约 35 行：

- 输入 → 处理 → 输出：查询和 `top_k` → BM25 排序并截断 → doc 级候选列表。
- 候选 → 评价：候选 ID 与人工 `expected` → 三个指标；指标只消费已返回列表，不能补回被截断文档。
- W4 迁移：Day 22–24 决定信息怎样存与怎样组织，Day 25–27 决定查询怎样把它变成候选与排名；无论哪一层，都要看该层自己的直接收据。
- 学员解释：准确定位 `evaluate.py:186` 的 `ranked[:top_k]` 为候选截断；说明第 422 行只能把已有 chunk 候选转换为 doc 级结果，不能恢复已被第 186 行截掉的 `refund_rule`；并能依据第 530、535–537 行说明指标只消费现有 `ranked_ids`，`eval_k=10` 也不会改变 `top_k=1` 的返回列表。

## 6. 单变量对照：`top_k=2` vs `top_k=1`

- 唯一改变：BM25 每条查询返回的候选数。
- 保持不变：查询、语料、分词、BM25 参数、人工标注、`eval_k=3`，以及 Dense/融合/重排/LLM 均禁用。
- 对照前预测：正确文档在完整排序第 2 时，`top_k=1` 会先在候选层过滤它，三个指标随之下降。
- 对照方式：步骤 B 只读两份既有收据，不重新调用检索器。
- 真实轨迹与结果：基线包含 `refund_rule:0.5339` 且三个指标为 `1.0/0.5/0.6309`；对照保留相同第 1 名与分数，但不再包含第 2 名，三个指标均为 0。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 唯一改变 `top_k=2→1` 后，第 2 名相关文档从候选消失，指标随之归零 | `ranked[:top_k]` 在指标计算前改变了候选集合 | 源码已核对该切片；若要评价普遍影响，仍需更多查询与配对运行 |

本对照只能说明这一个查询、这两份收据中候选截断如何传播到指标；不能证明增大 `top_k` 总能改善最终回答，也不能证明所有检索失败都由 `top_k` 导致。

## 7. 理解检查：每次只问一题

1. **概念复述**：事实存在、进入单路候选、进入融合/重排池、进入模型请求和出现在最终回答，为什么必须分别验证？
2. **结果预测**：若相关文档在 Dense 第 4、BM25 未命中，而 RRF 只保留前 3，它还能进入后续重排吗？需要看哪些直接证据？
3. **故障定位**：最终答案漏掉退款期限；收据显示文档存在、BM25 第 2、融合第 6、重排池只取前 5。第一处有害丢失在哪里，下一步怎样验证？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 各层是不同程序动作和不同收据；后一层成功不能倒推前一层，前一层存在也不能保证传到后一层。
2. 不会仅凭 Dense 第 4 就自动进入重排；先查两路实际候选、RRF 结果和重排输入池，确认是否在融合后前 3。若不在池中，重排器看不到它。
3. 第一处有害丢失是融合结果到重排输入池的截断（第 6 被前 5 排除）；先核对融合明细和重排池构造，再考虑扩大候选池的受控对照。扩大只验证截断，不保证重排后或最终回答正确。

</details>

卡住时换成 Room → Repository → ViewModel → Compose 的小例子，只复查具体缺口。三项通过且只读实验、源码和对照均完成，才记 `✅`。

**理解检查实录（2026-11-13）**：

- 概念复述：通过。学员先查实际 LLM 请求是否含退款规则；若没有，再回溯候选层，并接受“进入请求不等于模型采用”的边界。
- 结果预测：通过。能预测 `top_k` 截断导致候选与三个指标下降，并说明增大 `eval_k` 不能补回未返回候选。
- 故障定位：经补讲和换例后通过。最初把“RRF 排到第 6”误判为融合阶段已丢失，并优先建议扩大 `rerank_pool`、调查 Dense；经区分“排名变差”和“候选消失”，在 Repository 6 条、`list.take(5)` 的换例中准确定位截断，并提出比较截断前后的两个列表。

## 8. 可选 AI 代码演示

不安排 Kotlin 练习，也不新建代码。PowerShell 只读冻结 JSON，源码直接读取教材现有实现。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能沿“事实/原文 → 存储表示 → 单路候选 → 融合/重排池 → 模型请求 → 最终回答”逐层核对直接证据；能区分 `top_k` 的候选截断与 `eval_k` 的指标评价。
- 尚未理解及证据：无遗留缺口。曾把 RRF 第 6 名误判为融合阶段已经丢失；经 `list.take(5)` 换例复查后，能定位为 RRF 输出到 rerank 输入池的截断。
- 下次从哪一小步继续：Day 29 / Contextual Retrieval。
- 状态：✅。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-13；完成概念、冻结收据回读、源码链、单变量对照和三项理解检查。
- 实验命令、模型、证据位置与必要真实摘录：只读 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k2.json` 与 `lesson-d26-bm25-k1.json`；不调用模型。基线候选含 `refund_rule:0.5339`，对照仅保留 `keyword_stuffing:1.1533`。
- 学员的原始预测与后续解释：预测 `top_k=1` 会过滤第 2 名正确文档并使三个指标下降；实测后能区分语料存在、候选返回和指标评价。故障定位首次混淆 RRF 排名与 rerank 池截断，经 Android 列表换例修正。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 经补讲换例后通过。
- 是否采用代码演示及原因：不安排；复用收据和源码足以完成复盘。
- 本次观察、假设、待验证点：本次只支持同一查询的冻结收据中 `top_k` 截断如何传播到指标；不能证明增大候选数普遍改善最终回答，也没有生成阶段证据。若评价泛化，需更多查询、配对运行与真实模型请求收据。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`、`vendor/ai-agent-book/skills/rag-pipeline/SKILL.md`。
- 进度处理：2026-11-13 追加 Day 28 `✅`；下一课为 Day 29。
