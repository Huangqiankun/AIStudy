# Day 39 — 主动工具发现

> 章节 `vendor/ai-agent-book/book/chapter4.md:133`「工具太多怎么办」与第 187 行实验 4-1
>
> 实验 `vendor/ai-agent-book/chapter4/active-tool-discovery/`
>
> 授课日期 2026-11-26 ｜ 状态 ✅ 已完成

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

确认题中，学员用 `queryIntentActivities()` 只返回候选、尚未启动 Activity 解释了发现与调用的区别。

## 2. 实验前预测

- 固定教材任务、完整工具库、本地哈希嵌入、脚本化对话替身、最大步数，每次检索的候选上限均设成 4。
- 先看预筛选。给出具体任务后请学员预测，一开始只搜一次时，任务后半段需要的工具会在什么时候进入候选，依据是什么。
- 学员预测整句任务更偏股价，前 4 名可能会挤掉新闻工具。实测前 4 名含 3 个新闻工具和 1 个股价工具。

## 3. 运行基线

命令从教材目录读取内置任务和工具库，用整句任务一次性检索候选并跑离线工具循环。先看预筛选命中和调用顺序，再用 JSON 的 `result.trace` 核查两者之间发生了什么。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\active-tool-discovery'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch4 python demo.py --offline --tasks 'finance+news' --strategies prefilter --prefilter-n 4 --top-k 4 --max-steps 10 --output 'results/lesson-d39-prefilter.json'
```

- 控制台关注预筛选命中、调用轨迹和判定。JSON 中读 `records[0].result.prefiltered`、`called`、`trace` 和 `records[0].grade`。
- 学员在 PowerShell 运行成功，实测暴露 4 个工具、工具定义计数 437 tokens，两个槽位均通过。对应证据见下表。离线对话与业务工具均为替身。

## 4. 逐段读轨迹

| 步骤 | 核对的证据 | 实测摘录与学员解释 |
|---|---|---|
| 初次暴露 | `prefiltered` | 预筛选依次命中 `get_top_headlines`、`get_news_by_source`、`search_news`、`get_stock_price`。学员用股价工具排第 4 却率先调用解释了候选排名的作用 |
| 循环内发现 | `[discover_tools]` 的 `need`、候选和累计 `discovered` | 主动发现先根据股价需求加载 `get_stock_price` 等工具，调用股价工具后再根据新闻需求加载 `search_news` 等工具 |
| 具体调用 | `[call]` 与 `called` | 两组都先调用 `get_stock_price` 再调用 `search_news`。主动发现组在这两次调用前各有一次 `discover_tools`。学员从 `called` 核对 `google_search` 只出现在发现结果中 |
| 退出与判定 | `finished` 与 `grade` | 两组 `finished=true`，`filled_slots=2`、`total_slots=2`，`precise=true`。两份轨迹的新闻实参均为 `{"query":"查询","lang":"auto"}`。槽位判分使用工具名，实参是否针对苹果需要看调用轨迹 |

`trace` 是本地摘要，模板正文和逐轮完整 `messages` 未随 JSON 导出。源码显示定义文本怎样进入下一轮的本地客户端输入。

## 5. 关键源码

课堂逐段剖析了预筛选名单初始化、主动发现后名单和消息的更新，以及普通工具的可用性检查与实现调用。

| 文件与起始行 | 片段 | 核对点 |
|---|---|---|
| `agent.py:214` | 215–216、225–227 | 用整句任务检索一次，并用命中的工具初始化可用名单 |
| `agent.py:152` | 152–159 | 将候选名加入可用集合，并把定义文本放入后续消息 |
| `agent.py:162` | 162–176 | 名单缺项时记录 `[不可用]`，通过检查后再取实现并处理返回值 |

补充锚点为 `agent.py:125` 的客户端调用，`agent.py:249` 的需求检索与定义渲染，`discovery.py:97` 的排序截断，`tools_library.py:449` 的调用名判分。

源码推演中，学员指出只追加定义消息时，尚未更新名单的工具仍会被拒绝。学员也指出 `called.append(name)` 先于实现查找，缺少实现绑定时会生成 `unknown tool` 错误。源码在该分支仍会记录 `[call]`。这些故障均为假设，实测轨迹中没有出现。

## 6. 单变量对照

只换发现策略。任务、目录、后端、每次检索上限和最大步数保持一致。主动发现可以进行多次检索，也会从候选中略过已内置的基础工具，因此两组累计暴露数无需相同。策略变化还包含其自带的起始工具清单与提示词，两组证据用于解释策略行为。

学员预测主动发现会在查完股价后才首次加载新闻工具。运行后先沿每个 `need` 查返回的候选，再查对应的 `[call]`。导出路径独立保存对照证据。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\active-tool-discovery'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch4 python demo.py --offline --tasks 'finance+news' --strategies discovery --prefilter-n 4 --top-k 4 --max-steps 10 --output 'results/lesson-d39-discovery.json'
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 预筛选组启动时已包含 `search_news`。主动发现组在调用 `get_stock_price` 后第二次发现时才出现 `search_news`。两组都命中 2/2 个槽位 | 两个策略的查询时机和输入不同，候选排名与可用名单控制可选范围，离线对话替身逐步选择动作 | 在真实模型或其他任务上，需求是否被正确提出，找出的候选是否合适 |
| 主动发现组暴露 11 个工具，工具定义计数 1019 tokens。预筛选组为 4 个工具、437 tokens | 本次主动发现有两次检索、累计加载 8 个候选，还包含起始工具定义 | 真实请求的总 token、实际计费和缓存表现 |

`injected_tokens` 是代码对工具定义文本的计数。主动发现部分按发现工具名去重计入，provider 计费和缓存命中需要各自的证据。

## 7. 理解检查

1. **概念复述**　图片压缩工具已登记在目录里，任务中途才需要。Agent 怎样提出需求，Harness 怎样让下一轮能调用？
2. **结果预测**　若第一次股价发现已经加载 `search_news`，轮到查新闻时是否仍须再次发现？
3. **故障定位**　已发现 `search_news`，结束标记为真，新闻槽位仍未通过且 `called` 中缺它。去哪儿查 Harness 是否收到过请求并因工具不可用而拒绝？

<details><summary>讲师核对要点</summary>

1. Agent 描述能力缺口。Harness 搜索匹配、将定义写入消息历史并更新可用名单，随后按请求检查和执行。
2. 定义已进入消息，工具名已进入可用名单。轮到新闻步骤时可以直接请求调用。
3. 在 `result.trace` 查 `[不可用] search_news`。如果出现，说明请求已走到名单检查并被拒绝。

</details>

## 8. 可选 AI 代码演示

本次采用教材已有 CLI 和源码。未安排编程练习。

## 9. 卡点

- 已理解目录、发现候选、运行时白名单、下一轮消息与具体工具调用的关系。
- 原预测预筛选会漏新闻工具，经候选排名和调用顺序实测修正。概念检查时一度把搜索候选归给 Agent，换成移除照片定位信息的例子后明确由 Harness 匹配。
- 下次进入 Day 40 主动工具选择。

## 10. 讲师备注

- 已核对 `demo.py --help` 和离线模块导入。CLI 说明里的实验编号与章节书稿的编号不同，本课按入口、参数和源码定位。
- 学员亲自执行基线和对照。已从实验目录的 `results/lesson-d39-prefilter.json` 与 `results/lesson-d39-discovery.json` 核对控制台摘录。两份结果均记录 `mock-offline`、`local-hash-512`、工具库 126 个。
- 理解检查全部通过。学员按可用名单和消息历史解释已加载工具可直接请求调用，并能用 `[不可用] search_news` 判断拒绝发生在可用性检查。
- 证据覆盖本地检索、脚本化调用循环和按工具名评分。真实模型、真实业务工具、provider 请求、答案质量和实际计费均未验证。
- 备课材料为 `vendor/ai-agent-book/skills/tool-discovery/SKILL.md`。
