# Day 33 — Mem0 ADD-only：保留冲突历史后，当前事实由谁决定

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 记忆框架案例（第 192–204 行）
> 实验 `vendor/ai-agent-book/chapter3/mem0/`（只读，不运行框架）
> 备课日期 2026-11-20 ｜ 授课日期 2026-11-20 ｜ 状态 ✅

本文件按 `_TEMPLATE.md` 建立，分步使用。课程路线规定 Mem0 只读不跑；课堂的可运行部分是离线概念探针，不导入 Mem0、不调用模型、不连接向量库。探针只能验证给定规则下的数据流，不能证明 Mem0 的实际排序质量。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：当旧事实和新事实同时保留时，怎样分清“写入保留历史”和“检索选出当前事实”各自负责什么？

**类比**：Android 中把 `user.city` 直接更新为“上海”，像写入时 UPDATE；把“北京→上海”的每次变化追加到 Room 事件表，再在查询时按用户、相关性和时间选择，像 ADD-only。前者读当前值简单，但误更新会覆盖旧证据；后者保留审计历史，但查询端必须判断哪条适用于“现在”。类比只说明职责迁移，不代表 Mem0 就是 Room 或只按时间排序。

**最小概念**：ADD-only 不是“原对话整段无脑追加”，而是先由模型抽取候选事实，再把抽出的新事实追加。旧、新冲突事实可以共存；“现在住哪里”由检索信号和时间信息决定。写入返回 ADD 记录，不等于后续一定检索正确，更不等于回答模型实际使用了正确记忆。

```text
对话 ─→ 事实抽取 ─→ ADD（旧事实仍保留） ─→ 长期记忆库
                                               │
问题 + user/agent 过滤 ─→ 混合检索 + 时间排序 ─┘
                         │
                         └→ 命中片段 ─→ 回答模型 ─→ 最终答案
```

**实验全景预告**（不提前给结果）：

- 任务：用“先住北京、后来搬到上海”的人工事件，拆开保存集合、检索排序和最终回答三层。
- 学员会亲自运行：离线基线只按相关性排；单变量对照在相同事件、查询和用户过滤下加入时间信号。
- 会得到的产物：两份控制台 JSON，包含 `stored_ids`、`candidate_ids`、`ranked_ids`、`top_memory`；不写文件。
- 结束时回答：ADD 回执、`get_all`、`search` 命中和最终答案分别能证明什么，不能证明什么。

**确认问题**：如果一条记忆 ADD 成功，能不能直接推出用户追问“我现在住哪”时一定答对？为什么？

**学员回答**：不能；ADD 成功不排除库内仍有历史信息，还要看系统能否区分最新事实与历史。已正确分开写入与后续检索责任。

## 2. 实验前预测

- 任务与输入：同一用户的两条带时间人工事实：“住在北京”“后来搬到上海”；另放一条其他用户的深圳事实检查过滤。
- 固定条件：相同事件、相同 query、相同 `user_id=u1`、相同相关性分数；不导入 Mem0、不调用 LLM。
- 基线条件：候选过滤后只按相关性排序；同分时沿用输入顺序。
- 观察点：存储集合是否同时含旧/新事实；其他用户是否进入候选；Top-1 是哪条；这些字段分别属于哪一层证据。
- 预测问题：旧、新两条同样相关且都保留时，基线 Top-1 能否稳定代表“当前事实”？理由是什么？
- 学员预测：Top-1 是 `m1`，因为两条同分且输入顺序为 `m1 → m2`。讲师补充：基线并未按 ID 排序；Python 稳定排序在键相同时保留原输入顺序。

## 3. 运行实验

课程路线规定 Mem0 与 Memobase 只读，不运行其 CLI、测试、模型抽取或向量存储。以下命令仅在内存中执行教材机制的缩小模型，代码和输出均不是 Mem0 的真实轨迹。

### 步骤 A：离线基线——只有用户过滤与相关性

命令数据流：

1. 读取三条人工事件，其中两条属于 `u1`、一条属于 `u2`。
2. 模拟 ADD-only：三条都保留，不执行 UPDATE/DELETE。
3. 用 `user_id=u1` 过滤候选，避免把其他用户事实混入。
4. 只按预设相关性排序，输出保存、候选、排序与 Top-1 字段。

这些字段用来回答“存了什么”和“查到什么”是否能画等号；运行前不把 Top-1 当作预期结论。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\mem0'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
@'
import json

events = [
    {"id": "m1", "user_id": "u1", "occurred_at": "2026-01-10", "memory": "用户住在北京", "relevance": 1.0},
    {"id": "m2", "user_id": "u1", "occurred_at": "2026-10-20", "memory": "用户已从北京搬到上海", "relevance": 1.0},
    {"id": "m3", "user_id": "u2", "occurred_at": "2026-11-01", "memory": "用户住在深圳", "relevance": 1.0},
]
target_user = "u1"
candidates = [event for event in events if event["user_id"] == target_user]
ranked = sorted(candidates, key=lambda event: event["relevance"], reverse=True)

print(json.dumps({
    "probe": "relevance_only",
    "stored_ids": [event["id"] for event in events],
    "candidate_ids": [event["id"] for event in candidates],
    "ranked_ids": [event["id"] for event in ranked],
    "top_memory": ranked[0]["memory"],
}, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

**真实证据（2026-11-20，学员实跑）**：`stored_ids=[m1,m2,m3]`、`candidate_ids=[m1,m2]`、`ranked_ids=[m1,m2]`；`top_memory` 显示为六个问号。控制台返回 PowerShell 提示符，无报错，未单独采集退出码。

- 排名 ID 足以确认基线 Top-1 为 `m1`；乱码不影响本轮排序证据。
- 中文问号被判断为 PowerShell 原生管道输入编码问题的解释假设；对照命令加入 `$OutputEncoding` 后中文正常，支持该解释，但未另做独立编码实验。
- 本次未生成文件，真实证据来自学员贴回的控制台 JSON；讲义保存必要摘录。

## 4. 逐段读证据

| 证据字段 | 它能证明什么 | 它不能证明什么 | 学员解释 |
|---|---|---|---|
| `stored_ids` | 缩小模型保留了哪些人工事件 | Mem0 实际抽取了什么、写入是否成功 | 学员指出 `m3` 已被保存 |
| `candidate_ids` | 给定用户过滤后的候选范围 | 排名正确或当前事实正确 | 学员结合前一字段判断 `m3` 是写入后被用户过滤排除，不是写入失败 |
| `ranked_ids` / `top_memory` | 给定排序规则下的返回顺序 | 回答模型收到、采用或答对 | 学员正确解释同分基线与时间对照的顺序变化 |
| 最终答案 | 本探针不生成 | 不能从 Top-1 代替真实回答证据 | 学员在定位题中继续检查命中是否进入回答请求 |

始终区分：请求写入 → 后端执行与存储 → 写入回执 → 查询过滤与排序 → 命中进入回答请求 → 最终答案。概念探针只覆盖中间的确定性缩小模型。

## 5. 追到关键源码

课堂分三小段阅读，总量控制在约 30 行：

1. `agent.py:43–59`：把框架返回归一为新增事实列表；构造 `user_id` / 可选 `agent_id` 过滤条件。
2. `agent.py:305–338`：`add_memory` 委托 `memory.add`，`search_memory` 委托 `memory.search`，并传入 query、filters、top_k。
3. `quickstart.py:125–145`：追加“搬到上海”，随后分别调用 `get_all_memories` 与 `search_memory`。

关键边界：

- 伴随代码展示了写入与查询是两个不同 API，以及过滤参数如何传递。
- 混合信号、时间排序和事实抽取由 Mem0 依赖实现，不在这几段包装代码中；本地包装源码不能单独证明其真实排序算法或效果。
- `quickstart.py` 的提示文字与演示意图不是本次实跑结果；课程路线不运行该在线演示。
- `main.py:255–277` 分别构造 `added_memories` 与 `memories` 输出，再次说明 ADD 回执和 search 命中是不同收据。

**学员解释**：若 `m2` 已出现在 ADD 回执和 `get_all`，却没有出现在 search 命中，应先查检索路径中的筛选、排序与截断。阅读 `quickstart.py` 后，学员说明 `get_all_memories` 核对是否保存新数据，`search_memory` 核对能否检索到适用的新数据；讲师进一步限定 `get_all` 也用于确认旧事实是否仍保留。

## 6. 单变量对照

- 唯一改变：排序键从“相关性”改成“相关性 + 时间”；事件、顺序、用户过滤、查询含义和相关性分数不变。
- 保持不变：ADD-only 保存集合不变，`u2` 仍应被过滤掉。
- 对照前预测：学员预测 `stored_ids`、`candidate_ids` 保持不变，`ranked_ids`、`top_memory` 可能变化；理由是时间只参与候选后的排序，不改变存储或用户过滤。
- 证据边界：即使结果符合预测，也只证明这个明确写出的确定性排序规则；不证明 Mem0 真正的融合权重、抽取质量或回答准确率。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\mem0'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
@'
import json

events = [
    {"id": "m1", "user_id": "u1", "occurred_at": "2026-01-10", "memory": "用户住在北京", "relevance": 1.0},
    {"id": "m2", "user_id": "u1", "occurred_at": "2026-10-20", "memory": "用户已从北京搬到上海", "relevance": 1.0},
    {"id": "m3", "user_id": "u2", "occurred_at": "2026-11-01", "memory": "用户住在深圳", "relevance": 1.0},
]
target_user = "u1"
candidates = [event for event in events if event["user_id"] == target_user]
ranked = sorted(
    candidates,
    key=lambda event: (event["relevance"], event["occurred_at"]),
    reverse=True,
)

print(json.dumps({
    "probe": "relevance_plus_time",
    "stored_ids": [event["id"] for event in events],
    "candidate_ids": [event["id"] for event in candidates],
    "ranked_ids": [event["id"] for event in ranked],
    "top_memory": ranked[0]["memory"],
}, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| `stored_ids=[m1,m2,m3]`、`candidate_ids=[m1,m2]` 未变；`ranked_ids` 变为 `[m2,m1]`；`top_memory` 为“用户已从北京搬到上海” | 两条相关性同分时，确定性探针新增的时间键使较新的 `m2` 排在前面 | 如需验证框架本身，须另获授权并采集真实写入、get-all、search、回答请求与最终答案 |

对照由学员于 2026-11-20 实跑，中文显示正常，控制台返回 PowerShell 提示符，无报错，未单独采集退出码。`$OutputEncoding` 只修复展示管道，不参与排序；实验机制的唯一变量仍是排序键。

## 7. 理解检查：每次只问一题

1. **概念复述**：ADD-only 把哪项责任从写入侧移到了查询侧？它换来了什么，又新增了什么风险？
2. **结果预测**：若新事实没有时间信息，只增加相关性 + 时间排序，能否保证答出当前状态？说明条件和不确定性。
3. **故障定位**：ADD 回执有“搬到上海”，`get_all` 也有北京与上海，但最终仍答北京；你会按什么顺序查 search 命中、回答请求和最终答案？

<details><summary>讲师核对要点（回答后再展开）</summary>

1. v2 风格在写入时 UPDATE/DELETE/NOOP；v3 ADD-only 保留历史，把冲突消解更多交给检索。换来可追溯和避免误删，但检索与时间理解承担更高责任。
2. 不能保证；需要时间字段可靠、问题确实问当前状态、排序规则正确，还可能有语义/实体/BM25 信号竞争。
3. 先核对 search 的过滤、候选和顺序；再核对正确命中是否实际进入回答模型请求；最后核对模型是否采用。ADD 与 get-all 不能替代后两层证据。

</details>

**实际验收记录**：

1. 概念通过：学员初答把“同时保留的责任”说成移到查询侧；补讲后用“问 2025 年住哪里”的换例，正确说明查询要按问题相关性和时间选择适用事实，不能机械取最新。能说明 ADD-only 保留历史，检索承担冲突消解，代价是旧事实干扰和排序错误风险。
2. 预测通过：学员说明新事实缺少可靠时间字段时，增加时间排序仍不能保证找出当前居住地；没有时间依据就无法可靠判断新旧。
3. 定位通过：在 ADD、get-all、search 都正确但回答请求只含旧事实的假设轨迹中，学员把故障定位到“检索结果进入上下文/下一轮模型请求”的桥接，并提出对照 search 收据与实际请求参数。

## 8. 可选 AI 代码演示

第 3、6 节就是 AI 准备的最小离线演示，学员只需预测、运行和解释。它不修改 `vendor/`，不新增依赖或工程，也不作为 Mem0 性能评估。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分 ADD 回执、存储集合、候选过滤、排序结果、回答请求与最终答案；能解释 ADD-only 把冲突消解更多交给查询侧。
- 曾有卡点：把“新旧同时保留的责任”误说成从写入移到查询；经 2025 年居住地换例后已修正。
- 尚未验证：真实 Mem0 的事实抽取、混合检索权重、时间排序、向量库持久化和回答模型使用；路线规定本课不运行框架。
- 下次从哪一小步继续：Day 34，进入 Memobase 的 Profile 与 Event Memory（只读不跑）。
- 状态：✅。

## 10. 讲师备注

- 课程路线要求 Mem0 / Memobase 只读，本课不运行 `main.py`、`quickstart.py`、测试、模型调用或存储后端。
- 教材所述 LoCoMo / LongMemEval 数字与 Mem0 v3 算法不是本课本机实测，不作为本课实验结论。
- 备课核实了 `README.md` 的 CLI 说明与当前包装源码；不执行 `--help`，因为导入路径本身属于 Mem0 项目运行。
- 学员实跑离线基线与时间单变量对照；课堂阅读 `agent.py` 的 add/search 包装和 `quickstart.py` 的 get-all/search 调用，未执行其中代码。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`。
- 进度处理：在 `docs/progress.md` 日志表末尾追加 Day 33 完成记录，保留全部历史行。
