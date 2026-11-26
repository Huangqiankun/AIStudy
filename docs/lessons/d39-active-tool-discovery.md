# Day 39 — 主动工具发现

> 章节 `vendor/ai-agent-book/book/chapter4.md:133`「工具太多怎么办」与第 187 行实验 4-1
>
> 实验 `vendor/ai-agent-book/chapter4/active-tool-discovery/`
>
> 开课日期 2026-11-26 ｜ 当前阶段 建立概念与实验全景

本文件是分步讲义。学员先预测，再亲自运行并贴回输出。

## 1. 概念与实验全景

**核心问题**是任务跑到一半需要新工具时，Harness 如何按需把目录中的工具变成当前可调用的工具？

用 Android 的隐式 Intent 类比。应用拿到一份 PDF 后，才用 `ACTION_VIEW` 和 MIME type 找处理它的 Activity。发出查询时描述的是能力，查到匹配项后再发起打开动作。教材示例中，`discover_tools(need)` 用自然语言找候选，Harness 加载工具定义并更新可用名单，然后 Agent 才能发起具体调用。

```text
当前步骤缺一种能力 → discover_tools(need) → 匹配候选并加载定义
                                                    ↓
                                   后续步骤按需发起具体工具调用
```

- 任务为教材的 `finance+news`，先查苹果股价，再找相关新闻。
- 学员用 PowerShell 跑两次教材离线 CLI，先用任务原文做一次预筛选，再换成运行中的主动发现。
- 控制台显示候选、发现时机、调用名单与判定，同时导出 JSON 轨迹。
- 用轨迹说明第二项能力从哪里进入可用名单，以及什么记录可以证明示例随后调用了工具。

确认题用 `queryIntentActivities()` 返回了一个能打开 PDF 的 Activity，请学员判断此时是否已经打开文件并说明理由。得到回应后再进入实验预测。

## 2. 实验前预测

- 固定教材任务、完整工具库、本地哈希嵌入、脚本化对话替身、最大步数，每次检索的候选上限均设成 4。
- 先看预筛选。给出具体任务后请学员预测，一开始只搜一次时，任务后半段需要的工具会在什么时候进入候选，依据是什么。
- 学员预测待记录。

## 3. 运行基线

命令从教材目录读取内置任务和工具库，用整句任务一次性检索候选并跑离线工具循环。先看预筛选命中和调用顺序，再用 JSON 的 `result.trace` 核查两者之间发生了什么。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\active-tool-discovery'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch4 python demo.py --offline --tasks 'finance+news' --strategies prefilter --prefilter-n 4 --top-k 4 --max-steps 10 --output 'results/lesson-d39-prefilter.json'
```

- 控制台关注预筛选命中、调用轨迹和判定。JSON 中读 `records[0].result.prefiltered`、`called`、`trace` 和 `records[0].grade`。
- 真实输出待学员运行。离线对话与业务工具均为替身，数值和新闻仅为实验数据。

## 4. 逐段读轨迹

| 步骤 | 核对的证据 | 实测摘录与学员解释 |
|---|---|---|
| 初次暴露 | `prefiltered` 中有哪些名字 | 待运行 |
| 循环内发现 | `[discover_tools]` 的 `need`、候选和累计 `discovered` | 待运行 |
| 具体调用 | `[call]`、`[不可用]` 和 `called`，其中 `called` 也可能含元工具 | 待运行 |
| 退出与判定 | `finished` 与 `grade`，逐项核对要求槽位 | 待运行 |

`trace` 是本地摘要，模板正文和逐轮完整 `messages` 未随 JSON 导出。可从源码追到脚本化客户端收到的消息。

## 5. 关键源码

下列片段共约 37 行，分开读。先讲预筛选，在对照后用下一批行定位主动发现与具体调用。

| 文件与起始行 | 片段 | 核对点 |
|---|---|---|
| `agent.py:214` | 214–219、225–230 | 用整句任务检索一次，并用命中的工具初始化可用名单 |
| `agent.py:249` | 249–256、260–263 | 按本轮 `need` 查同一个索引，渲染定义并返回候选名 |
| `agent.py:152` | 152–159 | 将候选名加入可用集合，并把定义文本放入后续消息 |
| `agent.py:172` | 172–176 | 具体工具执行点及其结果进入历史的位置 |

补充锚点为 `agent.py:125` 的客户端调用，`agent.py:162` 的可用性检查，`discovery.py:97` 的排序截断，`tools_library.py:449` 的调用名判分。按证据需要再读。

## 6. 单变量对照

只换发现策略。任务、目录、后端、每次检索上限和最大步数保持一致。主动发现可以进行多次检索，也会从候选中略过已内置的基础工具，因此两组累计暴露数无需相同。策略变化还包含其自带的起始工具清单与提示词，两组证据用于解释策略行为。

先请学员预测，第二项能力会在轨迹的哪一行首次出现。再运行命令，先沿每个 `need` 查返回候选，然后查有无相应的 `[call]`。导出路径独立保存对照证据。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\active-tool-discovery'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch4 python demo.py --offline --tasks 'finance+news' --strategies discovery --prefilter-n 4 --top-k 4 --max-steps 10 --output 'results/lesson-d39-discovery.json'
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行 | 待结合源码讨论 | 待确定 |

`injected_tokens` 是代码对工具定义文本的计数。主动发现部分按发现工具名去重计入，provider 计费和缓存命中需要各自的证据。

## 7. 理解检查

1. **概念复述**　为什么需要把工具目录、当前可用名单和具体调用分开看？
2. **结果预测**　若任务中途需要一种新能力，却一直没有发出相应的 `discover_tools` 请求，你会预测轨迹的哪些段落没有变化？
3. **故障定位**　轨迹显示 `discover_tools` 找到了新闻工具，`finished=true`，新闻槽位仍未通过。你会先查哪个字段，用什么证据判断断在候选、调用或评分？

<details><summary>讲师核对要点</summary>

1. 目录内的工具经匹配和加载才进入当前可用集合，具体业务动作由后续调用触发。
2. 在其他发现请求也未碰巧匹配到该工具的前提下，候选历史和可用集合不会因此新增该工具。最终任务表现仍要结合后续动作和评分核对。
3. 先核对 `called` 与 `trace` 的实际调用，再看名称是否落在新闻槽位的允许列表中。`finished` 对应循环接收了 `finish`。

</details>

## 8. 可选 AI 代码演示

本次采用教材已有 CLI 和源码。未安排编程练习。

## 9. 卡点

- 已讲到概念与实验全景，等待第 1 节确认题的回应。
- 学员预测、实测和理解检查均待开展。

## 10. 讲师备注

- 已核对 `demo.py --help` 和离线模块导入。CLI 说明里的实验编号与章节书稿的编号不同，本课按入口、参数和源码定位。
- 基线及对照等待学员在 PowerShell 中运行。待回填输出、源码讲解、理解检查与证据边界。
- 备课材料为 `vendor/ai-agent-book/skills/tool-discovery/SKILL.md`。
