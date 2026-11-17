# Day 29 — 上下文前缀为什么能找回“孤儿分块”

> 章节 `vendor/ai-agent-book/book/chapter3.md` § RAG 技巧：上下文感知检索（第 594–618 行）
> 实验 `vendor/ai-agent-book/chapter3/contextual-retrieval/`
> 备课日期 2026-11-13 ｜ 授课日期 2026-11-13、2026-11-17 ｜ 状态 ✅

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：分块已经切断原文背景时，在索引前补一段短前缀，为什么可能改变同一个 BM25 查询的排名；我们又怎样证明变化来自哪一层？

**类比**：把知识块看成从 Android 崩溃报告中单独截出的 `at execute()`。只看这一行，类名、模块和业务场景都丢了；若在存档前补上“支付模块 / CheckoutRepository / 提交订单阶段”，日志搜索才有稳定的身份词。类比边界是：这里的前缀会进入检索索引，不是运行时把整份原文塞给模型，也不保证前缀内容一定正确。

**最小概念**：Contextual Retrieval 在**索引期**用全文与目标分块生成短前缀，再索引“前缀 + 原文”。它给孤立分块补回文档、章节、主体或时期等定位信号。它与 Day 19 的上下文压缩相反：前者给知识块做加法，后者在运行期给会话历史做减法。

```text
同一批原始分块
      ├─ plain：原文 ───────────────→ BM25 索引 ─┐
      └─ contextual：短前缀 + 原文 ─→ BM25 索引 ─┤
同一批 query + 人工 gold ────────────────────────→ 排名 / recall@k
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：在同一批法律文本块、同一组查询和 gold 标注上，只改变 BM25 实际索引的文本字段。
- 学员会亲自运行：先跑 `plain` 基线，再把唯一实验条件切到 `contextual`；随后挑一条查询查看两个 Top-K 列表和前缀内容。
- 会得到的产物：控制台的逐查询排名与 `recall@1/3/5`，以及 `%TEMP%\aistudy-d29-contextual\` 下两份 JSON 收据。
- 结束时要能回答：排名变化是否真实、在哪个源码边界发生、前缀为什么可能有帮助，以及为什么命中提升不自动证明前缀事实可靠。

**确认问题**：如果“（四）解释法律；（五）……”被切成一个独立块，而查询写“全国人大常委会有哪些职权”，你认为 plain BM25 最缺的是哪类信息：答案内容，还是能把这段内容锚定到主体和章节的身份词？只选一个并说一句理由。

## 2. 实验前预测

- 任务与输入：22 个预先生成并留有来源的法律文本块、15 条查询、每条查询一个人工标注的 `gold_chunk_id`。
- 固定条件：同一语料、查询、gold、jieba 分词、BM25 实现与 `k=1,3,5`；完全离线，不调用生成模型。
- 唯一改变：索引 `metadata.original_text`，还是索引已有的“前缀 + 原文”。
- 观察点：每条查询的 gold 排名、`hit@k`、汇总 `recall@k`，以及代表性块的 `prefix/plain/contextual` 三个字段。
- 预测问题：你预计三个 k 上都会同幅度变化吗？至少写出一种“前缀反而无帮助或有害”的可能原因。
- 学员预测：先判断 gold 会升高，理由是前缀可能补回身份信息；在看到 q08 plain 排第 16 后进一步预测它会改善但仍在 Top-5 外。实测为第 12，方向与区间均命中。经校准后能区分“前缀可信度”与“排名幅度”：后者还取决于区分词、候选竞争和 BM25 打分。

## 3. 运行实验

备课核验：2026-11-13 已运行 `compare_retrieval.py --help`，确认 `--corpus`、`--mode`、`--k`、`--per-query`、`--query`、`--top-k` 与 `--output` 存在。标准 `--extra ch3` 同步仍因本机缺少 MSVC 14+ 无法构建 `annoy==1.17.3`，课堂继续使用 `--no-sync --with rank-bm25 --with jieba`，不修改 `vendor/`。当前 sparse checkout 没有 README 默认的 `document_store.json`，但教材随仓库保存的 canonical validation evidence 含同一批 22 个 `plain/prefix/contextual` 块；步骤 A 只把这些已记录字段重建成临时 corpus。讲师未运行目标检索实验。

每条正式实验命令在给学员前，先解释输入、处理、输出和观察意义；课堂一次只给当前步骤的一个代码块。

**步骤 A：环境与临时 corpus 准备**

- 输入/读取：教材保存的 `validation/.../evidence.json`，其中每块同时有原文、已生成前缀和拼接文本。
- 处理：按 `compare_retrieval.py` 期待的结构映射为 `content` 与 `metadata.original_text`；不重新生成前缀，不改变原始证据。
- 输出/用途：`%TEMP%\aistudy-d29-contextual\corpus.json`，让后续 plain/contextual 两次运行共享同一批块。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\contextual-retrieval'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

$receiptPath = 'validation\runs\20260729T205807Z-3_10-35531780\evidence.json'
$evidence = Get-Content $receiptPath -Raw -Encoding utf8 | ConvertFrom-Json
$store = [ordered]@{}
foreach ($chunk in $evidence.chunks) {
    $store[$chunk.chunk_id] = [ordered]@{
        content = $chunk.contextual
        metadata = [ordered]@{
            original_text = $chunk.plain
            context = $chunk.prefix
        }
    }
}
$lessonDir = Join-Path $env:TEMP 'aistudy-d29-contextual'
New-Item -ItemType Directory -Force $lessonDir | Out-Null
$corpusPath = Join-Path $lessonDir 'corpus.json'
$corpusJson = ConvertTo-Json -InputObject $store -Depth 6
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($corpusPath, $corpusJson, $utf8NoBom)
[pscustomobject]@{ chunks = $store.Count; corpus = $corpusPath }
```

**步骤 B：核对 CLI**

```powershell
python -m uv run --extra ch3 --no-sync --with rank-bm25 --with jieba python compare_retrieval.py --help
```

**步骤 C：运行 plain 基线**

- 输入/读取：临时 corpus 与 15 条人工 gold 查询。
- 处理：只用原始块建 BM25 索引，逐条取得 gold 的名次，再按 `k=1,3,5` 汇总命中率。
- 输出/用途：控制台逐查询 `rank`、三项 `recall@k`，以及 `lesson-d29-plain.json`；它建立改变索引字段前的可核对基线。

```powershell
$plainOut = Join-Path $lessonDir 'lesson-d29-plain.json'
python -m uv run --extra ch3 --no-sync --with rank-bm25 --with jieba python compare_retrieval.py `
  --corpus $corpusPath --mode plain --k 1 3 5 --per-query --output $plainOut
```

**真实证据**：学员首次运行时，PowerShell `Set-Content -Encoding utf8` 生成的临时 JSON 带 BOM；`json.load(..., encoding="utf-8")` 在建立索引前报 `Unexpected UTF-8 BOM`。以 `[System.Text.UTF8Encoding]::new($false)` 重写同一 `$store` 后，首三字节为 `123 13 10`，随后基线成功。该失败不含检索结果，不能用于判断实验预测。plain 实测加载 22 块、15 查询，`recall@1/3/5 = 60.0% / 86.7% / 93.3%`；收据为 `%TEMP%\aistudy-d29-contextual\lesson-d29-plain.json`。

## 4. 逐段读真实证据

| 证据层 | 真实字段 | 能证明什么 | 不能直接证明什么 |
|---|---|---|---|
| 输入 | corpus 中同一 `chunk_id` 的 `original_text/context/content` | 两路共享原文，contextual 多了已记录前缀 | 前缀一定忠于全文 |
| 检索 | `per_query[].rank`、`hit@k` | gold 在当前 BM25 排名中的位置 | 生成模型一定会利用它 |
| 汇总 | `recall@1/3/5` | 这 15 条标注查询上的命中率 | 对其他数据集的普遍因果 |
| 来源 | validation evidence 的 provenance/receipts | 前缀来自教材记录的历史生成流程 | 本次重新调用了 LLM 或产生了新成本 |

分别标注实测观察、解释假设、待验证判断；先看排名事实，再解释前缀中的哪些 token 改变 BM25 得分。

**本次真实对照摘要**：

| 指标 | plain | contextual | 变化 |
|---|---:|---:|---:|
| recall@1 | 60.0%（9/15） | 80.0%（12/15） | +20.0pp |
| recall@3 | 86.7%（13/15） | 86.7%（13/15） | 0 |
| recall@5 | 93.3%（14/15） | 93.3%（14/15） | 0 |
| q08 gold 排名 | 16 | 12 | 上升 4 名，仍 miss@5 |
| q09 gold 排名 | 5 | 4 | 上升 1 名，仍 hit@5 |

q02、q11、q15 从第 2 升到第 1，解释了 `recall@1` 的三次新增命中。q08 和 q09 虽改善，但没有跨过 `k=5` 的命中边界，因此 `recall@5` 不变。代表性查询的 contextual Top-5 出现 `宪法_chunk_9` 与 `宪法_chunk_11` 等相邻竞争块；gold `宪法_chunk_10` 的前缀确实补入“全国人民代表大会常务委员会职权及工作机制”，但仍只排第 12。这支持“局部增益且竞争仍在”，不支持“正确前缀必然进入 Top-K”。

## 5. 追到关键源码

关键源码按数据加载、BM25 评测、前缀生成三小段阅读：

- `compare_retrieval.py:75-103`：同一条目拆出 `plain/contextual/context` 三个视图。
- `compare_retrieval.py:116-158`：指定字段分词建 BM25，计算 gold 排名与 `hit@k`。
- `compare_retrieval.py:312-340`：`--mode` 决定索引 `plain` 还是 `contextual`，并写出机器可读收据。
- 生成链补充锚点：`contextual_chunking.py:389-438` 把全文与目标块交给 LLM；`contextual_chunking.py:340-348` 拼成 `context + chunk`。本课不在线重跑该生成步骤。

**输入 → 处理 → 输出**：相同 chunk 列表与 query → 选字段、分词、BM25 排序 → chunk ID 排名、命中与汇总指标。

**学员解释**：能沿源码说明 `content` 是“前缀 + 原文”、`metadata.original_text` 是 plain 视图；发现缺失 `original_text` 时 fallback 会让两路都索引 contextual 文本，破坏严格单变量。能用 `rank_pos=12 > 1/3/5` 解释三个 `hit@k=false`；能定位本次 q08 失败发生在 BM25 Top-K 之前，而不是回答模型忽略结果。阅读生成链后说明分别保存 `text/context/contextualized_text` 便于回原文审计幻觉、复现实际索引并重建 plain 基线。

## 6. 单变量对照

- 唯一改变的条件：`--mode plain` 改为 `--mode contextual`，即 BM25 的索引文本从原文变成“已有前缀 + 同一原文”。
- 保持不变：corpus 文件、chunk ID、原文、15 条查询、gold、分词器、BM25、k 值和脚本版本。
- 对照前预测：q08 会从第 16 上升，但仍在 Top-5 外；实测第 12。

```powershell
$contextualOut = Join-Path $lessonDir 'lesson-d29-contextual.json'
python -m uv run --extra ch3 --no-sync --with rank-bm25 --with jieba python compare_retrieval.py `
  --corpus $corpusPath --mode contextual --k 1 3 5 --per-query --output $contextualOut
```

随后用代表性查询解释机制，不把单次命中扩大成普遍结论：

```powershell
python -m uv run --extra ch3 --no-sync --with rank-bm25 --with jieba python compare_retrieval.py `
  --corpus $corpusPath --query '全国人民代表大会常务委员会有哪些职权？' --mode both --top-k 5
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| contextual 的 `recall@1` 从 60.0% 到 80.0%，`@3/@5` 不变；q08 16→12 | 前缀为部分块补入区分词，但相邻候选也得到相似身份词；正确前缀不保证跨过 Top-K 阈值 | 用更多查询、不同分词或重新生成前缀复测；另行审核前缀与全文的事实一致性 |

## 7. 理解检查：每次只问一题

1. **概念复述**：上下文感知检索在什么时机、对什么对象做了什么？它与上下文压缩有什么本质区别？
2. **结果预测**：如果前缀给每个块都机械地加入同一句文档标题，哪些查询可能不变，为什么？
3. **故障定位**：`contextual` 的 recall 提升了，但检查发现某个前缀写入了原文没有的主体。你会把它记为成功吗？下一步查哪三处证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 索引期对知识块做加法；不是运行期裁剪会话，也不是把全文直接给最终回答模型。
2. 所有候选都增加相同常量词时区分度可能不变；应看查询词、IDF、长度归一化与具体排名，不预设结果。
3. 检索命中与事实忠实是两项验收；核对原始全文、目标块、生成前缀，再查错误前缀如何改变排名，必要时重生成或降权/拒收。

</details>

**本次作答结果**：

1. 概念通过：能说明索引期对知识块做“前缀 + 原文”的加法，并与运行期对会话/轨迹做摘要、筛选或截断的减法区分。
2. 预测通过：判断所有块获得同一通用前缀时 gold 不一定上升，因为没有增加候选间区分度，仍需看原文、IDF、词频、长度归一化与具体排名。
3. 定位通过：面对 recall 提升但前缀含原文不支持的主体，不记为知识质量成功；分别核对原文证据、去除错误前缀后的检索证据、以及 LLM 前缀生成证据。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课已有最小离线 BM25 对照，无需 Kotlin 移植。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能解释索引期加前缀的机制，预测排名与阈值指标变化，沿语料加载、BM25 排序、hit 计算和前缀生成源码定位证据边界。
- 尚未理解及证据：无遗留缺口。过程中曾把 raw 块缺少总领信息继续当作 contextual 排名不足的原因，并提出检查“模型是否采用”；经前缀实查与无 LLM 的 BM25 链路复查后修正。
- 下次从哪一小步继续：Day 30 / Agentic RAG。
- 状态：✅

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-13 开始，2026-11-17 完成；完成实验、源码与三项检查。
- 实验命令、模型、证据位置与必要真实摘录：本课主实验无生成模型，使用教材 canonical validation 中已生成前缀；实际收据为 `%TEMP%\aistudy-d29-contextual\lesson-d29-plain.json` 与 `lesson-d29-contextual.json`。plain/contextual 的 recall@1 分别为 60.0%/80.0%，q08 为 16/12。
- 学员的原始预测与后续解释：先预测排名升高；看到基线后预测 q08 改善但仍出 Top-5，实测吻合。能解释阈值未跨越不改变 recall，并把前缀正确性与检索排名分开验收。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过
- 是否采用代码演示及原因：未安排，教材已有可控离线实验。
- 本次观察、假设、待验证点：标准依赖同步仍受 `annoy`/MSVC 阻断；README 默认 corpus 未随当前 sparse checkout 出现，故从仓库内 validation evidence 确定性重建临时 corpus。PowerShell UTF-8 BOM 曾在索引前阻断 JSON 解析，已将讲义改为无 BOM 写入。当前结果只支持这 22 块、15 查询、已有前缀、jieba+BM25 的局部结论；未重新生成前缀、未运行 dense/hybrid、未验证最终回答模型。
- 对应备课 skill：`vendor/ai-agent-book/skills/knowledge-org/SKILL.md`
- 进度处理：已追加 Day 29 `✅` 日志；下一课 Day 30。
