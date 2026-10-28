# Day 16 — Agent Skill 为什么不是一段更长的系统提示词

> 章节 `vendor/ai-agent-book/book/chapter2.md` § 动态提示词与 Agent Skills（第 765–846 行）
> 实验 `vendor/ai-agent-book/chapter2/agent-skills-ppt/`（离线同构演示；不作为实验 2-6 正式验收证据）
> 备课日期 2026-10-28 ｜ 授课日期 2026-10-28 ｜ 状态 ▶

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：先看目录，需要时再拿操作手册

**本课只回答**：Agent Skill 是什么；它如何把“能力可发现”和“完整指令加载”分开，从而避免把所有领域知识常驻在上下文里？

**类比**：把 Agent 运行时看成 Android 的 `PackageManager + dynamic feature module`。启动时的薄目录类似 Manifest 中的能力声明：只有名称和触发边界，足够做路由，但不包含整个业务实现；选中后再读取完整 `SKILL.md`，类似按需加载功能模块；子文档、模板和脚本则像模块内资源。类比边界是：Skill 首先是给模型读的指令与资源包，不等同于 APK 中可直接调用的类型安全代码。

```text
任务到来
   |
   v
薄目录：name + description  ---- 未命中 ----> 不加载正文
   |
  命中
   v
完整 SKILL.md（核心流程）
   |
   +---- 按需读取 reference.md / 模板 / 脚本源码
   |
   v
通用执行器运行捆绑脚本 ----> 产物与校验证据
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：用仓库内置论文大纲生成一份可重新打开校验的 PowerPoint。
- 学员会亲自运行：先走离线同构的三层 Skill 通道；再保持输入大纲和生成脚本不变，绕过 Skill 加载过程直接运行脚本。
- 会得到的产物：两份 `.pptx` 和控制台轨迹；轨迹用于判断加载了哪一层，PPTX 回读用于判断最终产物是否有效。
- 结束时要能回答：为什么“文件生成成功”不能单独证明 Skill 被选择或其正文进入过上下文；Skill 与捆绑脚本分别承担什么角色。

**确认问题**：如果一个 PPTX 生成脚本一直都在磁盘上，但 Agent 启动时只看到了 `pptx` 的名称和一句描述，此时你认为 Agent 已经“知道脚本的调用步骤”了吗？只回答“已经”或“还没有”，再说一句理由。

## 2. 实验前预测

- 任务与输入：固定使用 `papers/sample_outline.json`，输出 8–12 页演示文稿。
- 固定条件：同一 Python/uv 环境、同一大纲、同一 `generate_pptx.py`、不调用在线模型；只改变是否经过 `read_skill → read_skill_file → run_skill_script` 通道。
- 观察点：启动时打印的目录内容；完整 `SKILL.md` 和 `reference.md` 何时读取；脚本何时执行；PPTX 是否可回读；两条路径各自能证明什么。
- 预测问题：两条路径都可能生成有效 PPTX 时，控制台轨迹里哪一类记录只会出现在 Skill 通道中？这能证明什么、不能证明什么？
- 学员原始预测：基线会出现 Skill 通道记录；它能证明 Agent 清楚这个 Skill 什么时候调用，并且生成目标 PPTX 时 Skill 被正常加载使用过。
- 讲师校准：前半句是本次要观察的轨迹差异；但离线基线的调用序列由程序预置，不能证明模型理解了触发条件或自主选中了 Skill。若实际看到逐层加载记录，只能直接证明本次 Harness 确实经过相应加载通道；是否完成脚本执行、是否生成有效产物还要分别看后续证据。

## 3. 运行实验

讲师已运行 `python -m uv run --extra ch2 python demo.py --help` 核实真实参数；由于当前 Windows 终端的帮助文本编码显示异常，正式运行前显式设置 UTF-8。目标实验留给学员亲自运行。

### 步骤 A：环境准备

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\agent-skills-ppt'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
[pscustomobject]@{
    Location = (Get-Location).Path
    InputExists = Test-Path -LiteralPath '.\papers\sample_outline.json'
    SkillExists = Test-Path -LiteralPath '.\skills\pptx\SKILL.md'
}
```

### 步骤 B：核对 CLI（收到步骤 A 的输出后再给）

**步骤 A 真实输出**：`Location=F:\AIStudy\vendor\ai-agent-book\chapter2\agent-skills-ppt`，`InputExists=True`，`SkillExists=True`。这只确认目录、固定输入和 Skill 入口存在；尚未加载 Skill、执行脚本或生成产物。

```powershell
python -m uv run --extra ch2 python demo.py --help
```

**步骤 B 真实输出**：CLI 显示 `--paper`、`--output/-o`、`--model`、`--max-turns` 与 `--offline`；其中 `--offline` 明确说明“不调用 OpenAI”，使用 `papers/sample_outline.json` 确定性地走完三层渐进式披露。由此确认后续命令与当前源码参数一致，也确认本基线不能产生模型自主路由证据。

### 步骤 C：运行 Skill 通道基线（完成预测后再给）

这条命令读取薄目录和内置大纲，按固定顺序经过三层加载通道，执行捆绑生成器，再重新打开 PPTX 校验页数与标题；它不调用模型，因此能证明加载器与产物链路，不能证明模型自主路由。

```powershell
python -m uv run --extra ch2 python demo.py --offline --output output/lesson-d16-skill.pptx 2>&1 |
    Tee-Object -FilePath output/lesson-d16-skill.txt
```

**准备观察什么**：薄目录、第二层/第三层加载标记、脚本生成结果、最终回读校验；不要仅凭文件存在推断前几层已发生。

**真实证据（2026-10-28，学员 PowerShell 实跑）**：命令成功进入离线模式。控制台因 Windows PowerShell 5.1 对原生命令管道的解码方式出现中文 mojibake，但 ASCII 锚点与结构字段仍可核对：薄目录约 `506` 字符；`read_skill('pptx')` 加载完整 `SKILL.md`（`242` 字符）；`read_skill_file('pptx', 'reference.md')` 加载子文档（`163` 字符）；`run_skill_script('pptx', 'generate_pptx.py')` 返回 `path=output\\lesson-d16-skill.pptx`、`num_slides=9`；校验器随后重新打开同一路径并读回 `9` 页。乱码是显示/管道编码现象，不把它误判为生成链路失败；标题语义需用正确编码另查时再查。

## 4. 逐段读真实轨迹

| 片段 | 真实摘录 / 证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 薄目录 | `约 506 字符`，列出 `pptx` 的 name/description | 启动可发现的元数据 | 完整正文已加载 | 学员指出这里只表示读取了 Skill 清单列表 |
| 第二/三层 | `read_skill('pptx')` 242 字符；`read_skill_file(..., 'reference.md')` 163 字符 | 对应文件经过加载通道 | 模型自主决定读取 | 学员正确定位 `read_skill('pptx')` 为完整 `SKILL.md` 被读取的第一条直接证据 |
| 脚本执行 | `run_skill_script(..., 'generate_pptx.py')`；返回路径与 `num_slides=9` | 生成器被调用及返回 | 指令一定被模型理解 | 待回答 |
| PPTX 回读 | 同一路径被重新打开，读回 9 页 | 产物可被库重新打开 | Skill 被模型选择或加载 | 待回答 |

离线模式的调用序列由程序预置，不把它描述为模型决策。若要证明真实模型自主选择，还需在线运行时的请求、`tool_call` 和后续上下文证据。

## 5. 追到关键源码

**源码锚点**：

- `demo.py:72-100`：只扫描 frontmatter 并构造薄目录。
- `demo.py:107-150`：按需读取完整 Skill、子文件并安全定位捆绑脚本。
- `demo.py:163-228`：三个通用工具的定义与分发。
- `demo.py:359-389`：离线模式按同一工具通道回放固定序列。
- `demo.py:400-416`：重新打开 PPTX，校验页数和标题。

关键片段总量控制在 20–40 行，课堂分段读取。

- 输入 → 处理 → 输出：`skills/*/SKILL.md` → 提取元数据形成目录 → 命中后读取正文/子文件 → 分发执行脚本 → 返回并校验产物。
- 连接轨迹的关键点：`scan_skill_catalog()` 只保留 `name/description`；`tool_read_skill()` 才读取全文；`dispatch()` 把工具名和参数路由到具体动作。
- 若启动时直接读取所有 `SKILL.md` 正文：能力仍可用，但“只让薄目录常驻”的边界消失，上下文成本与注意力干扰会变化。
- 学员解释：虽然 `scan_skill_catalog()` 在本地读取整个 `SKILL.md`，但后续只从 `meta` 取 `name`、`description` 组装目录，因此仍可说启动时暴露给模型的只是薄目录。讲师补充边界：本地文件读取、Harness 保存/筛选和模型实际收到的上下文是三层事实；`build_system_prompt()` 最终只渲染名称与描述，才是本同构实现中“模型可见薄目录”的直接源码依据。

## 6. 单变量对照

- 唯一改变的条件：绕过 Skill 加载通道，直接运行同一捆绑生成脚本。
- 保持不变：输入大纲、生成脚本、Python 环境；输出路径不同只为保留两份证据。
- 学员对照前预测：直接执行脚本时，`read_skill → read_skill_file → run_skill_script` 三类通道证据会消失；Skill 负责先暴露薄能力目录，命中后加载操作手册和资源，生成脚本负责执行。
- 讲师校准：三类通道日志的预测成立；但生成脚本不理解或执行操作手册，它只接收已整理的大纲并确定性地产出 PPTX。Skill/Agent 流程负责何时加载、怎样组织输入、选用哪个资源及如何验收，脚本负责具体转换。
- 对照命令（收到基线输出后再给）：

```powershell
python -m uv run --extra ch2 python skills/pptx/scripts/generate_pptx.py `
    papers/sample_outline.json output/lesson-d16-direct.pptx
```

**对照真实输出（2026-10-28，学员 PowerShell 实跑）**：直接脚本返回 `path=output\\lesson-d16-direct.pptx`、`num_slides=9`，以及与基线相同的九个标题；输出中没有 `read_skill`、`read_skill_file` 或 `run_skill_script` 分发日志。当前 JSON 是生成器执行后的内部摘要，尚需独立重新打开两个磁盘文件完成同口径校验。

**独立产物校验**：学员用 `python-pptx` 在生成器之外重新打开两份磁盘文件，得到 `lesson-d16-skill.pptx: exists=True slides=9`、`lesson-d16-direct.pptx: exists=True slides=9`、`titles_equal=True`。因此两条路径的最终产物在“可打开、页数、标题”三个观察维度上一致；差异落在是否存在 Skill 渐进加载与分发轨迹，不能由最终文件反推该轨迹。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 两份文件独立回读均存在、均为 9 页且标题相同；直接路径的三类 Skill 通道日志消失 | PPTX 生成能力来自确定性脚本；Skill 通道提供按需指令/资源加载与编排证据，不是文件格式生成的唯一途径 | 若要证明模型自主选 Skill，还需捕获在线请求、激活调用、返回与后续上下文 |

对照的目的不是证明“Skill 才能生成 PPTX”，而是拆开“领域指令加载”和“脚本执行/产物生成”两条证据链。

## 7. 理解检查：每次只问一题

1. **概念复述**：Skill 的薄目录、完整 `SKILL.md`、子资源/脚本分别解决什么问题？
2. **结果预测**：如果把所有 Skill 正文都在启动时加载，能力是否消失？上下文成本和路由证据会怎样变化？
3. **故障定位**：只有一个有效 PPTX，没有任何加载轨迹时，你能确认哪些事实；还需要补查哪些证据才能断言模型自主选择了 Skill？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 元数据负责可发现与路由，正文负责核心流程，子资源/脚本按需提供细则和确定性执行能力；三者不是同一层。
2. 能力未必消失，但渐进式披露的成本优势消失；一次结果不能量化普遍影响，还需测实际请求/上下文和重复任务。
3. 只能确认产物存在且可回读；还需请求中的目录、激活调用、对应结果、正文进入后续上下文以及最终执行证据。

</details>

## 8. 可选 AI 代码演示

默认不布置编程作业。本课已有完整离线同构演示，不另建工程或修改 `vendor/`。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：待开展
- 实验命令、模型、证据位置与必要真实摘录：待运行
- 学员的原始预测与后续解释：待回答
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查
- 是否采用代码演示及原因：采用教材自带离线同构演示；它只证明机制通道，不冒充模型自主路由或实验 2-6 正式验收
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/mcp-skill-hub/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 16 日志；仅备课不追加上课日志。
