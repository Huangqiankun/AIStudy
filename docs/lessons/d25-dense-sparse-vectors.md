# Day 25 — 同一查询，为什么稠密与稀疏检索会找到不同文档？

> 章节 `vendor/ai-agent-book/book/chapter3.md`「RAG 基础：构建 Agent 的知识获取管道」§ 稠密嵌入、稀疏嵌入（第 243–381 行）
> 实验 `vendor/ai-agent-book/chapter3/dense-embedding/`、`vendor/ai-agent-book/chapter3/sparse-embedding/`
> 备课日期 2026-11-11 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：同一批文档和同一个查询，为什么稠密检索与稀疏检索可能给出不同的候选文档？

**类比**：Android/HarmonyOS 里查崩溃问题时有两种线索。搜索 `HTTP-404`、设备型号或异常类名，像在 Logcat 中按精确 token 过滤；搜索“页面资源找不到”并希望关联到没写这句话的 404 文档，像让一个懂语义的同事按含义找相似记录。前者对应稀疏检索，后者对应稠密检索。类比边界是：稠密向量不是人类理解证明，稀疏得分也不只是字符串是否完全相等；两者都经过分词、表示和排序。

**最小概念**：

- 稀疏检索把词项当坐标，多数维度为 0；倒排索引先找共享词项的候选，BM25 再按词频、词的稀有程度和文档长度排序。
- 稠密检索用嵌入模型把整段文本编码成较短的连续向量；查询和文档不共享字面词，也可能因向量方向相近而被召回。
- 两路分数来自不同标尺：余弦相似度与 BM25 分数不能直接比较；今天只比较各自的排名、命中和漏召回，不进入下一课的融合。

```text
同一查询 + 同一批文档
          │
          ├─ 稀疏：分词 → 倒排索引找共享词 → BM25 排名
          │
          └─ 稠密：嵌入模型编码 → 余弦相似度 → 排名
                         │
                         ↓
              对照每个查询的 top-k / 命中位置
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：用一份临时小语料同时放入同义改写、错误码和产品编码查询，固定标注答案，对比两条检索路线。
- 学员会亲自运行：先运行 BM25 稀疏基线，再在相同语料、标注、查询集与 `top-k` 下切换到 MiniLM 稠密检索。
- 会得到的产物：每条查询的 `recall@k / precision@k / RR`、宏平均指标，以及临时目录中的 `lesson-d25-sparse.json` 与 `lesson-d25-dense.json`。
- 结束时要能回答：某次差异发生在表示/候选召回的哪一层；为何一次结果不能推出任何查询都应固定选某一路。

**确认问题**：客服文档只写“requested resource was not found”，用户却搜 `HTTP-404`；反过来，文档只写 `kitten`，用户搜 `cat`。你直觉上会分别先信任“字面精确”还是“语义相近”的路线？为什么？

## 2. 实验前预测

- 任务与输入：同一份 6 篇英文小语料；查询集固定为 `cat`、`HTTP 404 error`、`XK9-2B4-7Q1`，每条都有相关文档标注。
- 固定条件：相同语料、标签、`top_k=3`、Python 环境和输出指标；不调用 LLM，不生成最终回答。
- 唯一改变：检索表示与候选/排序机制从 BM25 稀疏路线切换为 MiniLM 稠密路线。
- 观察点：每条查询是否命中、第一篇相关文档排名、宏平均指标；不直接比较两路原始分数。
- 预测问题：你预计哪类查询会让两路结果拉开差距？请分别给出“同义改写”和“精确错误码/产品编码”的理由。
- 学员预测：待回答

## 3. 运行实验

备课核验：标准 `python -m uv run --extra ch3 ...` 会在本机同步阶段构建 `annoy==1.17.3`，因缺少 MSVC 14+ 失败；`--no-sync` 下稀疏 CLI 的 `--help` 已成功，稠密 CLI 又因顶层导入 `annoy`、`colorlog` 不能直接启动。教材推荐的 `sentence-transformers/all-MiniLM-L6-v2` 已只做加载与缓存核验，尚未运行查询。课堂使用 `--no-sync`，并在稠密命令中临时向当前 Python 进程注入未使用的 ANN/logger 占位模块，只走 `cli.py` 的精确稠密检索分支；不修改 `vendor/`。

### 步骤 A：准备同一份临时语料和标注

输入/处理/输出：在系统临时目录建立本课输入；6 篇文档同时包含同义表达、错误码、产品编码和干扰项，3 条查询各自绑定相关 `doc_id`。本步不检索，不产生胜负结论。

```powershell
$lessonRoot = Join-Path $env:TEMP 'aistudy-d25'
New-Item -ItemType Directory -Force $lessonRoot | Out-Null
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'

@'
[
  {"doc_id":"doc_kitten","title":"Playful kitten","text":"A playful kitten chased a ball of yarn across the room."},
  {"doc_id":"doc_feline","title":"Feline hunter","text":"A feline predator moved silently through the grass."},
  {"doc_id":"doc_404","title":"Missing resource","text":"The HTTP 404 error means the requested resource was not found on the web server."},
  {"doc_id":"doc_code","title":"Storage fault","text":"Error code XK9-2B4-7Q1 indicates a hardware fault in the storage controller."},
  {"doc_id":"doc_dog","title":"Playful dog","text":"A dog played fetch with a tennis ball in the park."},
  {"doc_id":"doc_training","title":"Neural training","text":"Gradient descent updates model parameters during neural network training."}
]
'@ | Set-Content (Join-Path $lessonRoot 'corpus.json') -Encoding UTF8

@'
{
  "cat": ["doc_kitten", "doc_feline"],
  "HTTP 404 error": ["doc_404"],
  "XK9-2B4-7Q1": ["doc_code"]
}
'@ | Set-Content (Join-Path $lessonRoot 'labels.json') -Encoding UTF8

Get-ChildItem $lessonRoot | Select-Object Name, Length
```

### 步骤 B：核对稀疏 CLI

输入/处理/输出：只解析真实参数帮助，核对 `--corpus`、`--labels`、`--eval`、`--top-k` 和 `--output` 是否存在；不会读取语料或执行检索。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\sparse-embedding'
python -m uv run --extra ch3 --no-sync python cli.py --help
```

### 步骤 C：运行稀疏基线

给命令前先完成预测。数据流：

1. 读取临时语料与三条标注查询。
2. 对文档和查询分词，按查询词从倒排索引找候选。
3. 用 BM25 对候选排序，取每条查询前 3 个。
4. 输出逐查询指标和宏平均，并写入稀疏收据。

```powershell
$corpus = Join-Path $lessonRoot 'corpus.json'
$labels = Join-Path $lessonRoot 'labels.json'
$sparseOut = Join-Path $lessonRoot 'lesson-d25-sparse.json'
python -m uv run --extra ch3 --no-sync python cli.py `
  --corpus $corpus --labels $labels --eval --top-k 3 --output $sparseOut
```

**准备观察什么**：每条查询的 `recall@3`、`precision@3`、`RR`，以及宏平均；没有返回候选与候选排得靠后是不同故障。

### 步骤 D：运行稠密单变量对照

数据流与基线相同，只把“分词 + 倒排候选 + BM25”换成“MiniLM 编码 + 余弦相似度排名”。临时占位模块只是绕过未走到的 ANN/logger 顶层导入，不实现检索，也不伪造结果。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\dense-embedding'
$env:D25_CORPUS = $corpus
$env:D25_LABELS = $labels
$env:D25_DENSE_OUT = Join-Path $lessonRoot 'lesson-d25-dense.json'

python -m uv run --extra ch3 --no-sync python -c @'
import os, runpy, sys, types
sys.modules["annoy"] = types.ModuleType("annoy")
sys.modules["hnswlib"] = types.ModuleType("hnswlib")
logger_stub = types.ModuleType("logger")
logger_stub.VectorSearchLogger = type("VectorSearchLogger", (), {})
sys.modules["logger"] = logger_stub
sys.argv = [
    "cli.py", "--corpus", os.environ["D25_CORPUS"],
    "--labels", os.environ["D25_LABELS"], "--eval", "--top-k", "3",
    "--embedding-model", "sentence-transformers/all-MiniLM-L6-v2",
    "--output", os.environ["D25_DENSE_OUT"],
]
runpy.run_path("cli.py", run_name="__main__")
'@
```

**真实证据**：待学员运行并贴回输出。记录完整命令、退出状态、模型名和两个 JSON 路径。若模型加载或输入解析失败，先定位环境/输入，不把失败当检索结果。

## 4. 逐段读真实轨迹

本课没有模型对话轨迹，改按检索数据流逐层核对；每轮只读一层。

| 阶段 | 稀疏路线证据 | 稠密路线证据 | 能证明什么 | 不能直接证明什么 |
|---|---|---|---|---|
| 输入 | 同一 corpus / labels / query / top-k | 同左 | 对照输入固定 | 两路内部表示等价 |
| 表示 | 查询分词、posting list | 查询/文档向量、模型名 | 各自怎样表示输入 | 表示一定符合业务相关性 |
| 候选与排序 | 倒排候选、BM25 排名 | 全量余弦相似度排名 | 相关文档在哪一步进入或丢失 | 下游 LLM 会不会使用结果 |
| 评测 | recall/precision/RR | 同口径指标 | 当前小语料上的命中与排名 | 普遍因果或生产总体质量 |

## 5. 追到关键源码

课堂分小段阅读，总量控制在 20–40 行：

- 稠密表示：`dense-embedding/cli.py:103-118`，`DenseEncoder.encode()`；输入文本批次，经模型、池化、L2 归一化，输出二维向量矩阵。
- 稠密排序：`dense-embedding/cli.py:175-185`，`dense_rank()` / `run_search()`；输入查询向量和文档矩阵，以点积计算已归一化向量的余弦相似度并降序排列。
- 稀疏候选：`sparse-embedding/bm25_engine.py:341-378`，`BM25.search()` 前半；输入查询，经分词和 posting list 产生“至少共享一个词”的候选集合。
- 稀疏打分：`sparse-embedding/bm25_engine.py:303-324`，`calculate_term_score()`；输入词项与候选文档，结合 TF、IDF、文档长度和平均长度输出分项分数。

学员用“输入 → 处理 → 输出”解释。重点定位：若同义词文档根本没有进入稀疏候选，调 BM25 排序参数能否挽救；若稠密最近邻本身与业务标注不符，问题是否已经发生在最终答案之前。

## 6. 单变量对照

- 唯一改变：检索机制从 BM25 稀疏路线切换为 MiniLM 稠密路线。
- 保持不变：同一语料、标注、查询集、`top_k=3`、指标口径；分别保存输出。
- 对照前预测：待回答。
- 真实轨迹与结果：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据候选与排序源码讨论 | 换查询、扩语料或重复评测验证；不从 3 条查询推广到所有场景 |

先核对条件确实固定，再解释差异。稠密与稀疏的原始得分量纲不同，不做数值相减；一次小语料评测只支持本次记录。

## 7. 理解检查：每次只问一题

1. **概念复述**：稠密和稀疏分别把什么当作“相似”的证据？为什么两路可能返回不同文档？
2. **结果预测**：把查询从 `resource not found` 改成精确的 `HTTP-404`，两路排名可能怎样变化？哪些只是合理预测、仍需实跑？
3. **故障定位**：相关文档没有进入 top-3，你会怎样区分“分词/候选没召回”“BM25 排序靠后”“嵌入表示偏离标注”和“评测标签错误”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 稀疏主要依赖共享词项及其统计权重，稠密依赖嵌入空间中的向量接近；二者表示与候选机制不同，排名自然可能不同。
2. 精确错误码通常为稀疏路线提供强字面信号；稠密模型对罕见编码的表示未必稳定。但具体排名受分词、语料、模型与 top-k 影响，必须实测。
3. 先核对输入与标签，再看查询分词/posting list 或向量最近邻；候选中有相关文档但 top-k 外才定位排序，候选根本没有则定位表示/召回。没有 LLM 生成步骤，不能把检索失败归因给最终回答。

</details>

卡住时换 Android 小例子复查：文档只写 `ActivityNotFoundException`，查询分别是完整异常类名和“找不到可处理 Intent 的页面”。

## 8. 可选 AI 代码演示

默认不布置编程作业。PowerShell 创建的临时 JSON 与稠密导入隔离代码是讲师提供的实验胶水，学员负责预测、运行和解释，不要求改写；不修改 `vendor/`，不新增项目依赖。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：待运行；计划产物在 `$env:TEMP\aistudy-d25\`
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用临时输入与进程内导入隔离；避免 Windows `annoy` 构建问题干扰检索机制对照
- 本次观察、假设、待验证点：备课只核验 CLI、依赖限制与 MiniLM 可加载，未运行目标查询
- 对应备课 skill：`vendor/ai-agent-book/skills/rag-pipeline/SKILL.md`
- 进度处理：开课后若中断，追加 Day 25 `▶` 日志；完成实验、对照、源码和三项检查后才记 `✅`。
