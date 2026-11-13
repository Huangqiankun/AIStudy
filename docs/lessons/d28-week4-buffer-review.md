# Day 28 — W4 记忆与检索证据链复盘（buffer）

> 章节：W4 / ch3 buffer 复盘（`vendor/ai-agent-book/book/chapter3.md`）
> 实验：复用 Day 22–27 的真实证据，重点审计 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k*.json`
> 备课日期 2026-11-13 ｜ 授课日期 待开展 ｜ 状态 待开展

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
- 学员预测：待回答。

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

**真实证据**：待学员运行并贴回输出。

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

**真实证据**：待学员运行并贴回输出。历史 Day 26 记录只能作为待复核材料，不能代替本次命令输出。

## 4. 逐段读真实轨迹

| 层 | 本次直接证据 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 语料/标注 | 两份收据均记录 `expected=refund_rule` | 评测认为哪个文档相关 | 原始文档内容一定忠实、标注一定正确 | 待回答 |
| 单路候选 | `methods.sparse.top` 的 ID、分数与顺序 | BM25 实际返回了什么 | 未返回文档不存在于语料 | 待回答 |
| 指标 | Recall/MRR/nDCG | 当前返回列表相对人工标注的表现 | 候选进入过 LLM 请求或被模型使用 | 待回答 |
| 融合/重排 | 本实验未启用，对应字段不存在 | 本次没有这两个后续阶段 | 它们若启用就一定能补回缺失候选 | 待回答 |
| 生成 | 本实验没有 LLM 请求/回答收据 | 不能评价生成阶段 | 最终回答会正确或错误 | 待回答 |

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
- 学员解释：待回答。

## 6. 单变量对照：`top_k=2` vs `top_k=1`

- 唯一改变：BM25 每条查询返回的候选数。
- 保持不变：查询、语料、分词、BM25 参数、人工标注、`eval_k=3`，以及 Dense/融合/重排/LLM 均禁用。
- 对照前预测：待回答。
- 对照方式：步骤 B 只读两份既有收据，不重新调用检索器。
- 真实轨迹与结果：待本次回读后填写。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待本次回读 | 预计先在 BM25 截断后的候选集合出现分歧 | 回到源码核对 `ranked[:top_k]`；若要评价普遍影响，需更多查询与配对运行 |

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

## 8. 可选 AI 代码演示

不安排 Kotlin 练习，也不新建代码。PowerShell 只读冻结 JSON，源码直接读取教材现有实现。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：完成 Day 28 后进入 Day 29 / Contextual Retrieval；未完成则续讲 Day 28。
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展。
- 实验命令、模型、证据位置与必要真实摘录：只读 `%TEMP%\aistudy-d26-retrieval\lesson-d26-bm25-k2.json` 与 `lesson-d26-bm25-k1.json`；不调用模型。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 是否采用代码演示及原因：不安排；复用收据和源码足以完成复盘。
- 本次观察、假设、待验证点：待开展。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`、`vendor/ai-agent-book/skills/rag-pipeline/SKILL.md`。
- 进度处理：授课结束或中断后追加 Day 28 日志；完成才推进 Day 29。
