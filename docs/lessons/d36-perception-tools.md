# Day 36 — 感知工具怎样避免让 Agent 把片段当全貌

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 感知工具（第 218–242 行）
> 实验 `vendor/ai-agent-book/chapter4/perception-tools/`
> 备课日期 2026-11-24 ｜ 授课日期 2026-11-24 ｜ 状态 ▶ 待开展

本文件是分步教学材料，不一次性贴入对话。没有实际运行、单变量对照、源码讲解和三项理解检查时，不记为完成。

## 1. 建立概念：只读不等于可以无限输出

**本课只回答**：感知工具读取的内容超过上下文预算时，怎样让 Agent 知道自己拿到的是片段，并据此决定是否继续读取？

**类比**：Android 的分页列表不会把整个数据库一次塞进 `RecyclerView`；当前页只是一段数据，同时还要有 `nextKey`、`endOfPaginationReached` 等状态。感知工具也不能把长文件或全部搜索结果一次塞进上下文；但如果只截断正文、不返回“尚未读完”的状态，就像 Paging 只给 20 条数据却谎称已经到底，后续判断会建立在假全貌上。

```text
外部文件
   │
   ├─ 读取全部内容
   │      ↓ max_length
   └─ 返回片段 + truncated + 文件大小
                    │
                    └─ Agent 决定：足够回答 / 继续读取 / 换搜索工具
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：通过教材 CLI 读取同一个 `src/base.py`，核对正文片段和结构化元数据是否一致。
- 学员会亲自运行：先限制 `max_length` 做基线；再只把该参数改成“不限制”做单变量对照。
- 会得到的产物：控制台 JSON，重点观察 `success`、`message.content`、`message.size_bytes`、`message.truncated` 和 `metadata.file_path`。
- 结束时要能回答：正文长度、文件字节数和截断标志各自证明什么，为什么任一字段都不能单独代表“已看到完整文件”。
- 证据边界：CLI 直接调用与 MCP 服务相同的 `read_file` 函数；它验证函数参数、读取和返回结构，不证明 MCP stdio 传输、工具发现或模型实际使用了结果。

**确认问题**：一个读取工具只返回前 120 个字符，但没有任何“已截断”字段；Agent 最容易把哪个假设误当成事实？

## 2. 实验前预测

- 任务与输入：固定读取 `src/base.py`。
- 固定条件：同一文件、编码、CLI、教材版本与 ch4 环境。
- 唯一变量：`max_length=120` 与 `max_length=-1`。
- 观察点：正文实际长度、`size_bytes`、`truncated`、`success`、路径。
- 预测问题：只改变 `max_length` 后，哪些字段应保持不变，哪些字段可能变化？
- 学员预测：待回答。

## 3. 运行实验

备课只运行了 `cli.py --help`，没有代跑以下目标实验。`run_experiment_4_2.py --help` 在导入阶段暴露环境不一致：根 `pyproject.toml` 的 ch4 extra 允许 MCP 1.x，而当前实验 `requirements.txt` 要求 `mcp>=2,<3`；本课不手改 `vendor/`，也不把 CLI 结果冒充 MCP 传输证据。

### 步骤 A：环境准备与 CLI 核对

- 输入/读取：进入感知工具实验目录，设置 UTF-8，并读取 CLI 帮助。
- 处理：只让命令行解析器列出可用子命令；不调用目标工具。
- 输出/用途：确认本机实际存在 `list / info / run / demo`，再进入预测和基线。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter4\perception-tools'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --no-sync --extra ch4 python cli.py --help
```

### 步骤 B：查看 `file_reader` 契约

- 输入/读取：工具注册表中的 `file_reader`。
- 处理：惰性导入实现并读取函数签名，不执行文件读取。
- 输出/用途：核对真实参数名、默认值和实现锚点，避免凭讲义猜命令。

```powershell
python -m uv run --no-sync --extra ch4 python cli.py info file_reader
```

### 步骤 C：截断基线

- 输入/读取：`src/base.py` 与 `max_length=120`。
- 处理：读取文件后按字符上限裁剪，并组装统一 `ActionResponse`。
- 输出/用途：观察正文片段、文件大小和截断状态能否共同描述“读到了什么”。

```powershell
python -m uv run --no-sync --extra ch4 python cli.py run file_reader file_path=src/base.py max_length=120
```

**真实证据**：待学员运行并原样贴回。需记录命令、退出状态和上述五个关键字段；控制台输出不等于模型已收到该结果。

## 4. 逐段读真实结果

| 片段 | 真实字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 调用输入 | 待运行 | CLI 实际传入的文件与上限 | MCP 或模型已参与 | 待回答 |
| 工具执行 | 待运行 | 文件读取是否成功 | 结果已进入模型请求 | 待回答 |
| 正文与元数据 | 待运行 | 返回了哪段内容、是否标记截断 | Agent 已继续读取或答案正确 | 待回答 |

## 5. 追到关键源码

**源码锚点**：

- `vendor/ai-agent-book/chapter4/perception-tools/cli.py:302-325`：解析参数、调用异步工具、打印 JSON，并按 `success` 选择退出码。
- `vendor/ai-agent-book/chapter4/perception-tools/src/filesystem_tools.py:315-364`：读取全文、处理 `max_length`、计算 `truncated`、组装 `ActionResponse`。
- `vendor/ai-agent-book/chapter4/perception-tools/src/base.py:16-21`：统一返回结构的 `success / message / metadata`。

**输入 → 处理 → 输出**：CLI 把 `file_path` 和 `max_length` 转为函数参数；`read_file` 先读取完整文本，再判断长度并裁剪；最后把正文、文件大小、编码与截断布尔值装入统一响应。

**当前实现的边界**：它显式给出 `truncated`，但没有 `offset`、总字符数或下一段读取方式；因此能防止“静默截断”，还不是教材所说的完整分页接口。`size_bytes` 是文件字节数，不等于字符总数。

## 6. 单变量对照

- 唯一改变：`max_length: 120 → -1`。
- 保持不变：文件、编码、CLI、Python 环境和工作目录。
- 对照前预测：待回答。

```powershell
python -m uv run --no-sync --extra ch4 python cli.py run file_reader file_path=src/base.py max_length=-1
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据源码与字段讨论 | 若文件在两次调用间变化，需用哈希或快照固定输入；本课不预设字段一定怎样变化 |

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么感知工具需要显式标记截断，而不只是把内容变短？
2. **结果预测**：同一 UTF-8 文件中有大量中文时，`size_bytes` 与 `len(content)` 能否直接视为同一个长度？改变字符上限后它们可能怎样变化？
3. **故障定位**：最终答案漏掉文件后半段，但工具调用显示成功；你会依次核对调用参数、`truncated`、返回正文、后续模型请求中的哪几项？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 截断标志把“工具只返回片段”变成可观察事实，使 Agent 能决定继续读取、搜索定位或承认信息不足；成功只说明调用完成。
2. 字节数与字符数口径不同，UTF-8 中文通常占多个字节；字符裁剪会改变返回正文长度和截断状态，但源文件字节数在输入不变时应保持。
3. 先确认 `max_length` 等调用参数，再看执行成功与截断状态、实际返回正文，随后核对该工具结果是否进入下一轮模型请求，最后才检查模型是否正确使用；不能由 `success=true` 直接推出看过全文。

</details>

## 8. 可选 AI 代码演示

本课不新增代码工程；直接使用教材 CLI 与现有源码。学员负责预测、运行和解释，不要求修改 Python。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：先回答第 1 节确认问题。
- 状态：▶ 待开展。

## 10. 讲师备注

- 备课时已运行 `cli.py --help`，退出码 0；没有运行 `file_reader` 基线或对照。
- `run_experiment_4_2.py --help` 在脚本导入阶段失败：`ImportError: cannot import name 'Client' from 'mcp'`。这是当前根 ch4 extra 与实验 MCP v2 要求不一致的环境证据，不是感知工具业务实验结果。
- 本课用 CLI 直接调用同一实现，明确限制证据范围；若后续要验证 MCP stdio，需按实验 README 用 MCP v2 隔离环境另行核实，不修改 `vendor/`。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`。
- 进度处理：本次已正式开课但尚未完成；会话中断时在 `docs/progress.md` 追加 Day 36 `▶`，实验、对照、源码与三项检查通过后才记 `✅`。
