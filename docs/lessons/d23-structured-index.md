# Day 23 — 找到相关片段，为什么仍可能答不出跨片段问题？

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 超越扁平文本：知识的组织与检索（第 424–480 行）
> 实验 `vendor/ai-agent-book/chapter3/structured-index/`
> 备课日期 2026-11-08 ｜ 授课日期 2026-11-09 ｜ 状态 ▶

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：找到相关片段，为什么仍可能答不出跨片段问题？

**类比**：Android Studio 的 Find in Files 能找出包含 `ADDPS` 和 `CR4` 的文件，但搜索结果列表本身不说明两者通过哪条依赖相连。项目树能表达“模块 → 包 → 类”的层次；Call Hierarchy 或 Navigation Graph 能表达“A 调到 B、B 再到 C”的关系。类比边界是：RAPTOR 的父节点由聚类与摘要构造，GraphRAG 的边由实体关系抽取构造，不等于源码中开发者显式写好的目录和调用边。

**最小概念**：扁平检索把每个文本块当作彼此独立的候选，擅长回答“哪一段包含这个信息”。结构化索引在索引阶段额外保存知识的组织方式：RAPTOR 用“细节叶子 → 主题摘要父节点”的树表达抽象层次；GraphRAG 用“实体 — 关系 → 实体”的图表达可遍历关联。它解决的是知识组织和检索路径问题，不自动保证摘要、关系或最终答案正确。

```text
同一批原始知识
   ├─ 扁平块：[ADDPS] [SSE] [CR4.OSFXSR] ...
   │          只按查询相似度返回若干独立块
   ├─ RAPTOR：SIMD 综述
   │             ├─ SSE 主题
   │             └─ AVX 主题 → 具体指令叶子
   └─ GraphRAG：ADDPS ─属于→ SSE ─需要启用→ CR4.OSFXSR
                         沿关系边形成可检查的路径
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：用同一份手工整理的 Intel x86 SIMD 小知识库回答一个跨实体问题，比较独立片段排名与实体关系路径提供的证据。
- 学员会亲自运行：先以 `top_k=3` 运行扁平检索与图遍历的离线对比，再只把扁平召回数改为 `top_k=10` 做单变量对照；不调用 LLM。
- 会得到的产物：控制台中的实体名、相似度分数和多跳路径，以及 `lesson-d23-baseline.json`、`lesson-d23-top10.json`。
- 结束时要能回答：召回更多片段与显式保存片段间关系分别解决什么问题，什么时候值得为结构化索引支付构建成本。

**确认问题**：如果 Find in Files 已经把含 `ADDPS` 和 `CR4.OSFXSR` 的两个文件都搜出来，为什么程序仍未必能回答“ADDPS 依赖哪个控制位”？

## 2. 实验前预测

- 任务与输入：同一查询“运行 ADDPS 指令前，操作系统必须把哪个控制寄存器位置 1？”，同一份 10 个实体、13 条关系的离线 SIMD 知识库。
- 固定条件：教材版本、查询文本、词频余弦打分、图中的实体和关系、最大图遍历深度；不调用 LLM，也不构建真实 RAPTOR/GraphRAG 索引。
- 观察点：扁平结果的 `name` / `score`、图检索的 `graph_start` / `graph_paths`，以及路径是否能回到知识库中预先写明的三元组。
- 预测问题：在固定 `top_k=3` 时，两种组织方式会分别给出什么证据？哪一份证据足以支撑“依赖哪个控制位”的回答，为什么？
- 学员初始预测：扁平检索前三个片段更足以支撑答案，理由是“先按照相似度匹配后再通过关系检索”；这混合了两条独立检索路径。（2026-11-09，运行前）
- 缩小问题后的修正：图检索会“查询 → 识别起点实体 → 沿关系边遍历（最多 3 跳）→ 返回带关系名的路径”。（2026-11-09，运行前）

## 3. 运行实验

讲师备课核实：`python -m uv run --extra ch3 python main.py --help` 在本机 Windows 会因 `annoy==1.17.3` 缺少 MSVC 而同步失败；`--no-sync` 则因现有环境缺 `loguru` 在参数解析前失败。这是环境证据，不是结构化索引实验结果。离线演示文件只依赖 `networkx`，因此用 uv 的临时隔离环境运行现成 `run_demo()`，不修改 `vendor/`，不向项目添加依赖。已核实 Python 3.12.9 + networkx 3.4.2 可导入，但讲师未在学员预测前运行目标查询。

每条正式实验命令在给学员前，先做简短的概念拆解：

- 输入/读取：固定查询、`top_k` 和教材内置的实体、关系三元组、层次摘要示意数据。
- 处理/筛选：扁平路径按词频余弦排序并截取前 k 个；图路径先匹配查询中的起点实体，再沿关系边做最多 3 跳的广度优先遍历。
- 输出/用途：打印并保存片段排名、起点实体和关系路径，用于区分“召回了哪些块”与“块之间的连接是否被显式表达”。

**步骤 A：环境准备**

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\structured-index'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 B：核对最小运行环境**（收到步骤 A 的输出后再给）

```powershell
python -m uv run --with networkx==3.4.2 --python 3.12 --no-project python -c "import sys, networkx; print(sys.version.split()[0]); print(networkx.__version__)"
```

**步骤 C：运行基线**（收到步骤 B 的输出并完成预测后再给）

```powershell
python -m uv run --with networkx==3.4.2 --python 3.12 --no-project python -c "from structured_vs_flat_demo import run_demo; run_demo(top_k=3, custom_query='运行 ADDPS 指令前，操作系统必须把哪个控制寄存器位置 1？', output='lesson-d23-baseline.json')"
```

**准备观察什么**：不先假定答案正确；分别记录扁平检索返回了哪些实体、图检索从哪个实体出发、打印了哪些路径，以及 JSON 是否与控制台一致。内置实体和三元组是教材手工整理的示意知识库，能验证代码路径与表示差异，不能证明真实 LLM 构建的索引同样准确。

**真实证据**（2026-11-09，学员运行）：命令返回 PowerShell 提示符，生成 `chapter3/structured-index/lesson-d23-baseline.json`（620 bytes）。扁平前三名为 `CR4.OSFXSR`（0.4587）、`XMM`（0.3185）、`YMM`（0.2979）；图起点为 `ADDPS`，收据包含 `ADDPS --属于--> SSE --需要启用--> CR4.OSFXSR`。未单独打印 `$LASTEXITCODE`，不补写精确退出码。

## 4. 逐段读真实轨迹

本实验是确定性检索演示，没有模型请求、工具调用轮次或最终生成答案。仍按证据链分段核对：

| 片段 | 真实摘录 / 证据字段 | 程序读取什么 | 程序执行什么 | 学员解释 |
|---|---|---|---|---|
| 查询输入 | `运行 ADDPS…哪个控制寄存器位置 1？`，`top_k=3` | 固定查询与 `top_k` | 分词、匹配实体 | 查询同时含 `ADDPS` 与控制位语义 |
| 扁平召回 | `CR4.OSFXSR` 排名 1，score 0.4587 | 10 个独立描述块 | 余弦打分、排序、截断 | 成功召回候选，但分数只表示本算法下的文本相似度 |
| 图遍历 | `ADDPS --属于--> SSE --需要启用--> CR4.OSFXSR` | 13 条预置关系 | 从 `ADDPS` 沿边做 BFS | 显式给出两跳关系路径 |
| JSON 收据 | `flat`、`graph_start`、`graph_paths` | 两条检索路径的结果 | 序列化保存 | 与控制台关键结果一致 |

分别核对查询输入、检索执行、结果落盘和结果能否支撑答案。程序打印了路径不等于关系来自真实文档抽取；关系在本演示中是预置数据。

## 5. 追到关键源码

**源码锚点一**：`vendor/ai-agent-book/chapter3/structured-index/structured_vs_flat_demo.py:103-126` 的 `FlatRetriever`。

- 输入 → 处理 → 输出：实体描述 → 词频向量与余弦排序 → 前 `top_k` 个独立结果。
- 连接实验的关键行：118–126 行；只返回排序结果，不返回片段之间的关系。

**源码锚点二**：`vendor/ai-agent-book/chapter3/structured-index/structured_vs_flat_demo.py:133-169` 的 `build_graph()`、`multi_hop_paths()`、`match_entity()`。

- 输入 → 处理 → 输出：实体与三元组 → 有向图、起点匹配、BFS 遍历 → 若干条带关系名的路径。
- 连接实验的关键行：157–161 行把每条边加入当前路径并继续入队；这一步使跨节点证据链成为显式数据。

**编排锚点**：`vendor/ai-agent-book/chapter3/structured-index/structured_vs_flat_demo.py:236-267` 的 `run_demo()`；同一查询依次调用扁平检索和图遍历，并把两类结果写进同一个收据。

关键片段课堂中分小段读取，总量控制在 20–40 行。学员需用“输入、处理、输出”解释，不要求掌握 Python 语法细节。

## 6. 单变量对照

- 唯一改变的条件：扁平检索展示的 `top_k` 从 3 增加到 10。
- 保持不变：同一查询、同一知识库、同一相似度函数、同一图、同一最大遍历深度、同一 Python/networkx 版本。
- 对照前预测：不会；即使 `top_k` 改成足以返回全部实体，扁平结果的条数会变化，但结果结构仍不包含显式关系链。（学员，2026-11-09，运行前）
- 对照命令：

```powershell
python -m uv run --with networkx==3.4.2 --python 3.12 --no-project python -c "from structured_vs_flat_demo import run_demo; run_demo(top_k=10, custom_query='运行 ADDPS 指令前，操作系统必须把哪个控制寄存器位置 1？', output='lesson-d23-top10.json')"
```

- 真实对照（2026-11-09，学员运行）：生成 `lesson-d23-top10.json`（1142 bytes）。`flat` 从 3 项增加到 10 项，前三名及分数未变，`ADDPS` 进入第 8 名；`graph_start` 仍为 `ADDPS`，5 条 `graph_paths` 与基线相同。
- 学员解释：增加 `top_k` 不会改变扁平结果的结构；但本实验没有调用下游 LLM，不能证明模型收到全部片段后永远答不对。需另查片段正文是否实际进入模型请求及模型的最终回答。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 扁平结果 3→10，仍无关系字段；图路径不变 | `top_k` 只截断扁平排序列表，图遍历不读取该参数 | 可接入下游 LLM，记录实际请求与回答；也可更换查询或构建真实索引复测 |

这个对照只验证本地示意知识库中“召回数量”与“显式关系结构”的区别。若扁平结果中出现目标控制位，也不能直接声称扁平检索永远无法回答；需检查是文本本身已包含关系、模型后续拼接成功，还是索引显式提供了路径。

## 6A. 追加实验：RAPTOR 式层次摘要

学员指出原基线实际只比较了扁平检索与 GraphRAG，横幅中的“层次树 1 棵”不能作为 RAPTOR 已实验的证据。因此在同一 Day 内追加层次摘要对照。

- 核心查询：`概述一下 x86 的 SIMD 指令集`。
- 扁平侧：仍从 10 个独立描述中按相似度返回前 3 个。
- 层次侧：返回教材预置的 `SIMD 指令集综述` 父节点摘要及其子节点标识。
- 证据边界：这是确定性的“RAPTOR 式输出形态”演示；父摘要与子节点列表是教材手工预置的，不是本次用聚类和 LLM 动态构建，不能据此评价真实摘要质量。
- 运行前预测（学员，2026-11-09）：扁平侧会返回带 `name` / `type` / `desc` / `score` 的相似片段；层次摘要更容易支撑概述，因为它会逐层提炼。补充边界：本次层次侧打印父摘要文本与子节点提示，不是整棵树状图；扁平片段不是绝对不能被下游模型综合，只是固定 `top_k=3` 时可能覆盖不全。

```powershell
if (Test-Path '.\lesson-d23-raptor.txt') {
    throw 'lesson-d23-raptor.txt 已存在，请先停下并告诉讲师。'
}

python -m uv run --with networkx==3.4.2 --python 3.12 --no-project python -c "from structured_vs_flat_demo import ENTITIES, FlatRetriever, demo_hierarchical; demo_hierarchical(FlatRetriever(ENTITIES), '概述一下 x86 的 SIMD 指令集', 3)" 2>&1 | Tee-Object -FilePath '.\lesson-d23-raptor.txt'
```

**离线演示源码锚点**：`structured_vs_flat_demo.py:74-80` 的预置 `TREE_SUMMARY`，以及 `structured_vs_flat_demo.py:223-233` 的 `demo_hierarchical()`。

**真实 RAPTOR 建树源码锚点**：`raptor_indexer.py:100-123` 的嵌入聚类、`raptor_indexer.py:125-178` 的父节点摘要与父子连接、`raptor_indexer.py:180-220` 的叶节点创建和递归建层。课堂在实跑后分段阅读，严格区分“示意树输出”与“真实建树机制”。

**真实证据与解释**：待学员运行。

## 7. 理解检查：每次只问一题

1. **概念复述**：RAPTOR 树、GraphRAG 图与扁平块分别保存了什么额外信息？
2. **结果预测**：若把 `top_k` 从 3 增到 10，哪些输出字段一定会受参数影响，哪些不应受影响？为什么？
3. **故障定位**：若 JSON 里出现 `ADDPS → SSE → CR4.OSFXSR`，你会看哪里判断它是从真实文档抽取的，还是教材预先写好的关系？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 扁平块保存独立内容与分数；RAPTOR 保存抽象层次与父子关系；GraphRAG 保存实体和带类型的关系边。结构不保证内容正确。
2. 扁平结果数量/列表可能变化；图的起点和路径不应因 `top_k` 改变，因为图遍历不读取该参数。具体排名仍以实测为准。
3. 回到 `TRIPLES`、构图代码、真实构建阶段的抽取产物和原文证据；本离线演示只能证明路径由预置三元组生成，不能证明 LLM 抽取质量。

</details>

卡住时记录具体缺口，换 Android 模块依赖或家庭关系的小例子补讲，再检查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课已有教材内置的最小离线演示，不另建工程或增加项目依赖。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 环境核验：ch3 全量同步因 `annoy==1.17.3` 缺 MSVC 失败；`--no-sync` 缺 `loguru`；临时 Python 3.12.9 + networkx 3.4.2 导入成功。
- 实验命令、证据位置与必要真实摘录：待运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用教材自带离线演示；隔离变量且无需模型/API Key。
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 23 日志；仅备课不追加上课日志。
