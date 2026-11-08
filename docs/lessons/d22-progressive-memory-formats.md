# Day 22 — 同一条用户信息，为什么不能永远只用一种记忆格式？

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 用户记忆的四种存储格式（第 88–109 行）
> 实验 `vendor/ai-agent-book/chapter3/user-memory/`
> 备课日期 2026-11-08 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：同一条用户信息，为什么不能永远只用一种记忆格式？

**类比**：Android 应用不会把所有状态都塞进一个 `String`。简单开关放 `SharedPreferences` 很省事；需要局部更新和字段约束时会用 Room；涉及“这个联系人是谁的医生、信息何时获得”时还要保存关联与来源。类比边界是：本课讨论的是 Agent 的长期用户记忆表示，不是 Android 存储 API 的性能实现。

**最小概念**：长期记忆不是聊天记录的逐句备份，而是从对话中挑出值得跨会话保留的信息，再以适合未来检索、更新和消歧的结构保存。Simple Notes、Enhanced Notes、JSON Cards、Advanced JSON Cards 是从轻量碎片到富上下文卡片的四种表示；结构越丰富，维护成本通常也越高。

```text
原始对话
   │ 后台提取候选事实
   ▼
同一语义信息 ─┬─ Simple Notes：最小事实
               ├─ Enhanced Notes：完整语境段落
               ├─ JSON Cards：类别 → 子类别 → 键值
               └─ Advanced Cards：事实 + 人物/关系/来源/时间
   │
   ▼
后续会话按需读取并使用
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：把同一个“用户本人和父亲分别有一位同名张医生”的语义场景写入四种模式，检查每种模式实际保存了哪些关联。
- 学员会亲自运行：先做 Simple Notes 基线，再只切换记忆模式做 Enhanced Notes、JSON Cards、Advanced JSON Cards 对照；不调用模型。
- 会得到的产物：控制台中的 `mode`、`manager_class`、`stored_shape`、`context_preview`，以及 `data/lesson-d22/` 下的 JSON 文件。
- 结束时要能回答：何时轻量格式已经够用，何时必须为关系、来源和局部更新支付额外结构成本。

**确认问题**：如果只把两条“张医生”信息存成互不关联的字符串，半年后用户说“帮家里的张医生预约复诊”，系统最缺的是什么？

## 2. 实验前预测

- 任务与输入：同一语义场景包含用户本人的牙科医生和父亲的心脏科医生，两人都叫“张医生”。
- 固定条件：同一教材版本、同一 `memory_manager.py`、同一用户语义事实、同一输出字段；不调用 LLM。
- 观察点：持久化文件的顶层类型、信息之间的组织关系、是否有 `person` / `relationship` / `backstory` / 时间元数据、局部更新的寻址方式。
- 预测问题：四种格式中，哪一种最容易区分两位“张医生”？为此会多保存什么？
- 学员预测：`JSON Cards` 和 `Advanced JSON Cards` 最容易区分两位张医生；需要保存名字、科室、服务对象字段。（2026-11-08，运行前）

## 3. 运行实验

讲师备课已核实：`python -m uv run --extra ch3 python main.py --help` 会在本机 Windows 因 `annoy==1.17.3` 缺少 MSVC 而同步失败；已有环境用 `--no-sync` 可启动 CLI。该失败是环境证据，不是记忆实验结果。教材 CLI 也没有原生 `ollama` provider，且 OpenRouter 地址在当前源码中固定为云端地址。因此本课第一实验只调用无需模型的现成 `MemoryManager`，不手改 `vendor/`，不使用云端 Key。

每条正式实验命令在给学员前，先做简短的概念拆解：

- 输入/读取：同一语义场景、要测试的记忆模式和教材现成的存储管理器。
- 处理/筛选：按模式把语义事实适配为该模式要求的结构，调用对应 `add_memory()`，再重新读取持久化文件。
- 输出/用途：只打印模式、实际管理器类型、保存形状和上下文预览，用来比较“组织方式”；不评价模型抽取能力。

**步骤 A：环境准备**

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\user-memory'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

**步骤 B：核对 CLI**（收到步骤 A 的输出后再给）

```powershell
python -m uv run --extra ch3 --no-sync python main.py --help
```

**步骤 C：运行四模式存储探针**（完成预测后再给；课堂中一次只给当前命令）

讲义中保留完整探针，课堂按模式逐步运行。探针不删除已有文件；每个模式使用独立教学用户 ID，重复运行前先核对 `data/lesson-d22/` 中是否已有同名产物。

```powershell
# 课堂运行时仅改变这一项：notes / enhanced_notes / json_cards / advanced_json_cards
$mode = 'notes'

$probe = @'
import json
import sys
from pathlib import Path

from config import Config, MemoryMode
from memory_manager import create_memory_manager

mode = MemoryMode(sys.argv[1])
Config.MEMORY_STORAGE_DIR = "data/lesson-d22"
user_id = f"lesson_d22_{mode.value}"
manager = create_memory_manager(user_id, mode)
manager.clear_all_memories()

if mode is MemoryMode.NOTES:
    manager.add_memory("我的牙科医生是张医生。", "same-scene", ["doctor"])
    manager.add_memory("父亲的心脏科医生也是张医生。", "same-scene", ["doctor"])
elif mode is MemoryMode.ENHANCED_NOTES:
    manager.add_memory(
        "用户本人由牙科张医生诊疗；用户父亲由另一位同名的心脏科张医生诊疗。两人不是同一位医生。",
        "same-scene",
        ["doctor", "relationship"],
    )
elif mode is MemoryMode.JSON_CARDS:
    manager.add_memory({"category": "health", "subcategory": "self", "key": "dentist", "value": "张医生"}, "same-scene")
    manager.add_memory({"category": "health", "subcategory": "father", "key": "cardiologist", "value": "张医生"}, "same-scene")
else:
    manager.add_memory({
        "category": "healthcare",
        "card_key": "self_dentist_zhang",
        "card": {
            "fact": "张医生是用户本人的牙科医生",
            "person": "用户本人",
            "relationship": "self",
            "backstory": "用户在同一场景中主动说明",
        },
    }, "same-scene")
    manager.add_memory({
        "category": "healthcare",
        "card_key": "father_cardiologist_zhang",
        "card": {
            "fact": "另一位同名张医生是用户父亲的心脏科医生",
            "person": "用户父亲",
            "relationship": "father",
            "backstory": "用户在同一场景中主动说明",
        },
    }, "same-scene")

path = Path(manager.memory_file)
stored = json.loads(path.read_text(encoding="utf-8"))
print(json.dumps({
    "mode": mode.value,
    "manager_class": type(manager).__name__,
    "memory_file": str(path),
    "stored_shape": list(stored.keys()),
    "context_preview": manager.get_context_string(),
}, ensure_ascii=False, indent=2))
'@

$probe | python -m uv run --extra ch3 --no-sync python - $mode
```

**准备观察什么**：不预设哪种格式“总是最好”。先核对条件确实生效，再看保存结构能否回答当前消歧任务，以及多出来的字段和维护成本是否与任务相称。

**真实证据**：

- 2026-11-08 第一次 `notes` 尝试：退出成功，`manager_class=NotesMemoryManager`，产物为 `data/lesson-d22/lesson_d22_notes_memory.json`；但两条 `content` 和 `context_preview` 中的中文均已变成 `?`。
- 证据判断：该次运行只能证明管理器选择和文件写入发生，不能用于比较记忆内容。当前解释假设是 PowerShell here-string 经原生管道传给 Python 时发生编码损坏；须显式设置 `$OutputEncoding` 后复跑验证。
- 2026-11-08 显式设置 `$OutputEncoding = [System.Text.UTF8Encoding]::new($false)` 后复跑：两条中文在 `stored.notes[].content` 和 `context_preview` 中完整保留，支持“首次损坏发生在 PowerShell 原生管道编码环节”的解释。有效基线显示顶层为 `user_id/type/updated_at/notes`，两条事实各自带独立 ID、相同 `doctor` 标签和同一 session；服务对象与科室仍写在自然语言 `content` 内。
- 输出仍只属于存储层证据，不证明模型以后一定检索到、读到或正确使用这些内容。

## 4. 逐段读真实轨迹

| 片段 | 真实摘录 / 证据字段 | 程序输入什么 | 程序执行什么 | 学员解释 |
|---|---|---|---|---|
| 工厂选择 | 首次 `notes` 得到 `NotesMemoryManager` | `MemoryMode.NOTES` | 选择 Notes 管理器 | 待回答 |
| 写入 | Notes 生成两条独立 note | 两条自然语言事实 + 相同 `doctor` 标签 | `add_memory()` | 待回答 |
| 持久化 | `stored.notes` 保留中文、ID、session、时间和标签 | 管理器内存状态 | 写入 JSON | 待回答 |
| 上下文呈现 | 两条事实完整出现在 `context_preview` | 已保存记忆 | `get_context_string()` | 待回答 |

分别核对“程序保存了什么”“以后是否检索到”“是否进入模型请求”“答案是否正确”。本探针只直接证明第一项。

## 5. 追到关键源码

**源码锚点**：

- `vendor/ai-agent-book/chapter3/user-memory/memory_manager.py:120-190`：Notes 的写入与保存。
- `vendor/ai-agent-book/chapter3/user-memory/memory_manager.py:365-438`：JSON Cards 的三级寻址。
- `vendor/ai-agent-book/chapter3/user-memory/memory_manager.py:544-620`：Advanced Cards 的关系字段与元数据入口。
- `vendor/ai-agent-book/chapter3/user-memory/memory_manager.py:737-759`：`create_memory_manager()` 如何按模式选实现。

关键片段总量约 20–40 行，课堂分小段读取。

- 输入 → 处理 → 输出：模式和内容结构 → 工厂分派及模式专属写入 → JSON 文件与上下文字符串。
- 轨迹连接点：探针输出的 `manager_class` 对应工厂返回值，`memory_file` 对应各实现的 `save_memory()`。
- 改变行为后的预测：若只改模式，最先变化应是管理器类型和持久化形状；是否更会回答问题还需要检索与模型请求证据。
- 学员解释：待回答

## 6. 单变量对照

- 唯一改变的条件：`$mode` 的值。
- 保持不变：语义场景、教材版本、用户事实、输出字段、无 LLM 调用。
- 对照前预测：待回答
- 对照命令：复用步骤 C，只把 `$mode` 依次改为另外三种值；每次运行后贴回输出再继续。
- 真实轨迹与结果：待运行

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据持久化结构讨论 | 后续再用检索与请求证据判断模型是否真正用到 |

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么“记住了两句话”不等于“以后能正确区分两位同名医生”？
2. **结果预测**：若用户把父亲的心脏科医生改为李医生，哪种格式最容易做局部更新？依据是什么？
3. **故障定位**：保存文件中关系正确，但回答仍把两位医生混为一谈；下一步先查哪一层、看什么证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 存储、检索、进入请求和正确使用是不同阶段；裸字符串可能缺少人物、关系和来源。
2. JSON Cards / Advanced Cards 有稳定路径或卡片键可定点更新；Enhanced Notes 可能需要重写段落。仍须结合实际更新实现，不能只凭名字。
3. 先核对检索结果，再核对相关记忆是否进入下一轮真实模型请求；若已进入再查提示结构和最终使用，不能从保存成功直接归因模型。

</details>

## 8. 可选 AI 代码演示

本课探针已是最小离线演示，不另建 Python 文件，不布置 Kotlin 作业。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：第一次 `notes` 运行中文成为 `?`，判为无效内容基线；显式设置 UTF-8 `$OutputEncoding` 后复跑成功。有效证据在 `data/lesson-d22/lesson_d22_notes_memory.json`：两条独立 Notes 完整保留中文，服务对象与科室位于自然语言 `content`，两条均只有通用 `doctor` 标签。未调用模型。
- 学员的原始预测与后续解释：运行前预测 `JSON Cards` 和 `Advanced JSON Cards` 最容易区分；关键字段为名字、科室、服务对象。后续解释待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用无需模型的存储层探针，隔离表示差异与模型抽取随机性
- 本次观察、假设、待验证点：首次问号问题经显式 UTF-8 管道复跑消失，支持管道编码解释。有效 Notes 基线只证明内容被保存并可格式化为上下文；是否能稳定消歧仍待与结构化模式对照，是否被模型正确使用不在本探针证明范围内。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`
- 环境备注：`ch3` 首次同步因 Windows 缺少 MSVC 无法构建 `annoy==1.17.3`；`--no-sync` 可复用现有环境启动本课无需新增依赖的代码。后续若进入向量检索实验，再单独处理编译依赖，不把它混入 Day 22。
- 进度处理：授课结束或中断后追加 Day 22 日志；仅备课不追加上课日志。
