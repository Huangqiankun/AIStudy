# Day 34 — Profile 与 Event Memory 为什么不能互相替代

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 用户记忆系统：记忆框架案例（第 206–217 行）
> 实验 `vendor/ai-agent-book/chapter3/memobase/`
> 备课日期 2026-11-20 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：为什么同一段用户对话要同时提炼为 Profile 和 Event Memory，而不是只保留一种表示？

**类比**：Android 应用里，`SharedPreferences/DataStore` 适合保存“当前语言、默认单位”这类相对稳定配置；埋点或审计事件表适合保存“某时发生了什么”。Profile 像按固定 key 读取的当前用户属性，Event Memory 像带时间戳的事件流。前者便于快速取得稳定事实，后者保留时间与具体经过。类比边界是：Memobase 的 Profile/Event 都由记忆抽取流程产生，并不分别等同于 Android 的某个具体存储 API。

**最小概念**：

- Profile：主题 → 子主题 → 内容，面向相对稳定的用户属性和当前画像。
- Event Memory：时间 → 事件摘要/标签，面向“何时发生、先后顺序、具体经历”。
- 同一句对话可能同时贡献两种表示；是否真的被提取、如何更新，必须看真实框架返回，不能由字段名或示意数据代替。

```text
                         ┌─ Profile ───── topic / sub_topic / content
对话 → 缓冲区 → flush ──┤
                         └─ Event Memory ─ created_at / event_tip / tags
                                      ↓
                              按问题选择所需证据
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：对同一个“上次何时讨论预算”的问题，比较两种记忆视图各自带来的可用证据。
- 学员会亲自运行：先核对真实脚本 CLI；再运行一个不联网、不调用模型的 Profile 视图基线；最后只改变视图为 Event 做单变量对照。
- 会得到的产物：控制台 JSON，包含 `view`、`evidence_fields`、`time_evidence` 与 `answerable`；它们是确定性示意探针，不是 Memobase 实测结果。
- 结束时要能回答：为什么稳定属性与时间事件要分开表示，以及怎样区分缓冲写入、提取、读取和回答四个阶段。

**确认问题**：如果系统只留下“新项目预算是 50 万”这一条属性，却没有时间字段，它还能回答“我们上次是什么时候讨论预算的吗”？你判断的直接依据是什么？

## 2. 实验前预测

- 任务与输入：固定问题“我们上次是什么时候讨论新项目预算的？”；两种视图都描述同一次预算讨论。
- 固定条件：同一问题、同一段事实、同一判断逻辑、同一本地 Python；不连接 Memobase，不调用模型。
- 唯一变量：`D34_VIEW=profile` 或 `D34_VIEW=event`。
- 观察点：被选中记录有哪些字段，是否存在可引用的时间证据，程序是否判为可回答。
- 预测问题：仅把视图从 Profile 改成 Event 后，哪些输出字段会改变？为什么？
- 学员预测：待回答。

## 3. 运行实验

### 步骤 A：环境与真实 CLI 核对

- 输入/读取：真实 `profile_demo.py` 的参数定义。
- 处理：只让参数解析器打印帮助；不会构造客户端，不会连接服务端，也不会提取记忆。
- 输出/用途：核对 `--dry-run`、`--op profile`、`--op event` 等真实入口，确认后续源码所对应的调用边界。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\memobase'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --no-sync --extra ch3 python profile_demo.py --help
```

### 步骤 B：Profile 视图基线

在给出命令前先完成预测。命令按以下数据流工作：

1. 固定同一个时间问题和两份示意记录。
2. 根据 `D34_VIEW` 只选一个视图，不做模型推理。
3. 枚举所选记录的证据字段。
4. 只检查是否存在非空 `created_at`，输出 `time_evidence` 与 `answerable`。

```powershell
$env:D34_VIEW = 'profile'
@'
import json
import os

query = "我们上次是什么时候讨论新项目预算的？"
views = {
    "profile": [
        {"topic": "work", "sub_topic": "project_budget", "content": "新项目预算是 50 万"}
    ],
    "event": [
        {"created_at": "2026-11-13T09:30:00+08:00", "event_tip": "讨论并确定新项目预算为 50 万", "event_tags": {"topic": "budget"}}
    ],
}

view = os.environ["D34_VIEW"]
records = views[view]
time_evidence = [r["created_at"] for r in records if r.get("created_at")]
print(json.dumps({
    "probe": "same_question_different_view",
    "query": query,
    "view": view,
    "evidence_fields": sorted({key for record in records for key in record}),
    "time_evidence": time_evidence,
    "answerable": bool(time_evidence),
}, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

**真实证据**：待学员运行并贴回输出。该探针的记录是明确的示意输入，只验证“表示中有没有时间证据”，不代表 Memobase 会把这段对话提取成同样字段和值。

## 4. 逐段读真实轨迹

真实 Memobase 演示的阶段按下面顺序核对；只有接入服务端并真实执行后，才能把对应结果记为实测。本课路线只读不跑，故不伪造服务端轨迹。

| 阶段 | 本课核对的证据 | 能证明什么 | 不能证明什么 |
|---|---|---|---|
| `insert` | blob id / 调用源码 | 对话进入用户缓冲区 | 已完成画像或事件提取 |
| `flush` | 返回值与服务端日志 | 提取流程被触发并返回状态 | 每条事实都提取正确 |
| `profile/event` | SDK 返回对象及渲染字段 | 已整理结果可被读取 | 结果已进入回答模型请求 |
| `context`/回答请求 | 实际上下文与请求体 | 哪些记忆被送给模型 | 模型一定正确采用 |
| 最终答案 | 回答文本 | 用户实际收到什么 | 前面每个阶段都正确 |

## 5. 追到关键源码

总阅读量控制在约 35 行，分三小段进行：

1. `profile_demo.py:104-129`：`render_profiles` 把 SDK 对象变成 `topic/sub_topic/content`，`render_events` 保留 `created_at/event_tip/event_tags`。
2. `profile_demo.py:132-166`：`op_insert` 只调用 `user.insert(...)`；`op_flush` 才调用 `user.flush(sync=True)`；`op_profile` 与 `op_event` 分别读取两种结果。
3. `profile_demo.py:176-186`：端到端顺序是 insert → flush → profile → event → context，结果才被组合成一个字典。

**输入 → 处理 → 输出**：对话输入先进入缓冲；服务端在 flush 时提取；查询侧把画像、事件或 context 读取并渲染。源码中的 `print` 文案不是服务端提取正确的证据，真实返回对象才是。

**学员解释**：待回答。

## 6. 单变量对照

- 唯一改变的条件：`D34_VIEW: profile → event`。
- 保持不变：问题、两份预置记录、程序逻辑、Python 环境和输出字段。
- 对照前预测：待回答。

```powershell
$env:D34_VIEW = 'event'
@'
import json
import os

query = "我们上次是什么时候讨论新项目预算的？"
views = {
    "profile": [
        {"topic": "work", "sub_topic": "project_budget", "content": "新项目预算是 50 万"}
    ],
    "event": [
        {"created_at": "2026-11-13T09:30:00+08:00", "event_tip": "讨论并确定新项目预算为 50 万", "event_tags": {"topic": "budget"}}
    ],
}

view = os.environ["D34_VIEW"]
records = views[view]
time_evidence = [r["created_at"] for r in records if r.get("created_at")]
print(json.dumps({
    "probe": "same_question_different_view",
    "query": query,
    "view": view,
    "evidence_fields": sorted({key for record in records for key in record}),
    "time_evidence": time_evidence,
    "answerable": bool(time_evidence),
}, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待学员运行后填写 | 由视图字段差异解释，不外推到框架抽取质量 | 真实 Memobase 是否生成相同画像/事件，须接服务端采集 insert、flush、profile/event 和回答请求 |

## 7. 理解检查：每次只问一题

1. **概念复述**：Profile 与 Event Memory 各自保留什么？为什么只留其中一种会损失一类问题所需证据？
2. **结果预测**：如果 `insert` 成功但尚未 `flush`，立即读取 `profile()`，能否凭 blob id 断言新画像已出现？为什么？
3. **故障定位**：`event()` 返回了正确时间，但最终回答仍说“不知道”；你会按什么顺序核对 context、实际回答请求和最终答案？

<details><summary>讲师核对要点（回答后再展开）</summary>

1. Profile 面向相对稳定的结构化属性；Event 保留带时间的具体经历。字段结构服务于不同查询，二者不是互斥真相。
2. 不能。insert 只证明进入缓冲；须核对 flush 是否触发、返回/服务端日志，以及随后 profile 的真实结果。
3. 先看 event 结果是否被组装进 context，再看该 context 是否实际进入回答模型请求，最后判断模型是否采用；不能用 event 正确替代后两层证据。

</details>

## 8. 可选 AI 代码演示

第 3、6 节是 AI 准备的最小确定性探针。它不导入 Memobase SDK、不调用模型、不连接服务端，也不写入 vendor；学员只需预测、运行和解释。真实框架行为只通过第 5 节源码阅读建立边界。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：Day 35 为 buffer 日，只处理欠账、复习或休息，不上新课。
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）。

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展。
- 路线约束：Mem0 / Memobase 只读不跑；本课不启动真实服务、模型或存储后端。
- `profile_demo.py --help` 仅核对参数；正式实验是独立的离线示意探针，不代表 Memobase 性能或输出。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`。
- 进度处理：授课结束或中断后追加 Day 34 日志；仅备课不追加上课日志。
