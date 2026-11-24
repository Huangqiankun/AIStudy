# Day 36 — 感知工具怎样避免让 Agent 把片段当全貌

> 章节 `vendor/ai-agent-book/book/chapter4.md` § 感知工具（第 218–242 行）
> 实验 `vendor/ai-agent-book/chapter4/perception-tools/`
> 备课日期 2026-11-24 ｜ 授课日期 2026-11-24 ｜ 状态 ✅

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
- 学员预测：最初预测 `file_path` 不变，`success/content/size_bytes/truncated` 都可能改变；在区分源文件字节数、返回字符数和调用状态后，修正为路径、`size_bytes`、`success` 应保持不变，`content` 与 `truncated` 随上限改变。

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

**真实证据（2026-11-24，学员亲自运行并贴回）**：CLI 接口查询显示 `file_reader` 映射到 `src/filesystem_tools.py :: read_file()`，参数为 `file_path / encoding='utf-8' / max_length=50000`，退出码 0。基线读取 `src/base.py` 得到：

```json
{
  "success": true,
  "message": {
    "size_bytes": 4840,
    "truncated": true,
    "encoding": "utf-8"
  }
}
```

`content` 是文件开头 120 个字符，路径解析到同一 `src/base.py`，CLI 退出码为 0。完整正文未复制进讲义；控制台结果不证明模型收到或使用了它。

## 4. 逐段读真实结果

| 片段 | 真实字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 调用输入 | `file_path=src/base.py, max_length=120` | CLI 实际传入同一文件与字符上限 | MCP 或模型已参与 | 能区分路径、字符上限和文件字节数 |
| 工具执行 | `success=true`, `EXIT_CODE=0` | CLI 与工具函数均正常完成 | 已读取完整内容或结果已进入模型请求 | 初次把 `content` 与 `truncated` 选为“成功但不完整”的直接证明；经失败响应换例后修正为 `success + truncated` |
| 正文与元数据 | 120 字符片段，`size_bytes=4840`, `truncated=true` | 返回的是片段，源文件大小另计 | Agent 已继续读取或答案正确 | 能说明 `content` 是支持性证据，`size_bytes` 不是返回字符数 |

## 5. 追到关键源码

**源码锚点**：

- `vendor/ai-agent-book/chapter4/perception-tools/cli.py:302-325`：解析参数、调用异步工具、打印 JSON，并按 `success` 选择退出码。
- `vendor/ai-agent-book/chapter4/perception-tools/src/filesystem_tools.py:315-364`：读取全文、处理 `max_length`、计算 `truncated`、组装 `ActionResponse`。
- `vendor/ai-agent-book/chapter4/perception-tools/src/base.py:16-21`：统一返回结构的 `success / message / metadata`。

**输入 → 处理 → 输出**：CLI 把 `file_path` 和 `max_length` 转为函数参数；`read_file` 先读取完整文本，再判断长度并裁剪；最后把正文、文件大小、编码与截断布尔值装入统一响应。

**当前实现的边界**：它显式给出 `truncated`，但没有 `offset`、总字符数或下一段读取方式；因此能防止“静默截断”，还不是教材所说的完整分页接口。`size_bytes` 是文件字节数，不等于字符总数。

**学员解释**：能从 `truncated` 必须在裁剪前计算说明“先裁剪再比较”会错误得到 `false`；能从 `path.stat().st_size` 说明改变 `max_length` 不应改变源文件字节数；能指出缺少 `offset/cursor` 会让 Agent 不知道从哪里续读，只能扩大上限并重复读取前缀。

## 6. 单变量对照

- 唯一改变：`max_length: 120 → -1`。
- 保持不变：文件、编码、CLI、Python 环境和工作目录。
- 对照前预测：路径与 `size_bytes=4840` 不变，`success` 仍为真，正文扩大，`truncated` 改变；依据是同一文件未被修改，字符上限只控制返回内容。

```powershell
python -m uv run --no-sync --extra ch4 python cli.py run file_reader file_path=src/base.py max_length=-1
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 两次 `success=true`、路径相同、`size_bytes=4840`、退出码 0；`content` 从 120 字符片段变为完整文本，`truncated` 从 `true` 变为 `false` | `max_length=-1` 被源码改为完整内容字符数，因此没有按上限裁剪；源文件本身未变 | 本次没有采集哈希；若文件在两次调用间变化，仍需用哈希或快照固定输入。CLI 结果不证明 MCP stdio 或模型使用 |

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么感知工具需要显式标记截断，而不只是把内容变短？
2. **结果预测**：同一 UTF-8 文件中有大量中文时，`size_bytes` 与 `len(content)` 能否直接视为同一个长度？改变字符上限后它们可能怎样变化？
3. **故障定位**：最终答案漏掉文件后半段，但工具调用显示成功；你会依次核对调用参数、`truncated`、返回正文、后续模型请求中的哪几项？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 截断标志把“工具只返回片段”变成可观察事实，使 Agent 能决定继续读取、搜索定位或承认信息不足；成功只说明调用完成。
2. 字节数与字符数口径不同，UTF-8 中文通常占多个字节；字符裁剪会改变返回正文长度和截断状态，但源文件字节数在输入不变时应保持。
3. 先确认 `max_length` 等调用参数，再看执行成功与截断状态、实际返回正文，随后核对该工具结果是否进入下一轮模型请求，最后才检查模型是否正确使用；不能由 `success=true` 直接推出看过全文。

</details>

**实际验收记录**：

1. 概念通过：能分别说明 `success` 回答调用是否成功，`truncated` 回答返回正文是否因上限被裁剪；缺任一字段都会留下不同盲区。
2. 预测通过：在 `max_length=200` 换例中，正确预测路径、`size_bytes=4840` 与成功状态保持，返回 200 个字符且仍截断，并明确 200 是字符而非字节。
3. 定位通过：起初把“忽略截断后答错”笼统定位为模型回答，并建议重复相同参数或换模型；经分层后定位到结果使用/后续规划阶段，知道应先检查是否追加读取或 `grep`、结果是否进入下一轮请求。换例中工具命中且进入请求而最终仍否认时，正确定位为模型未采用工具结果。

## 8. 可选 AI 代码演示

本课不新增代码工程；直接使用教材 CLI 与现有源码。学员负责预测、运行和解释，不要求修改 Python。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分调用成功、返回完整、进入模型请求、模型采用和最终正确；能用显式截断状态避免把片段当全貌，并识别当前接口缺少续读位置。
- 曾有卡点：把 `content` 当作成功的直接证明；把 `size_bytes` 误认为可能随返回上限变化；故障定位时先建议相同参数重试或换模型。
- 尚未验证：MCP v2 stdio、工具发现、真实模型请求和模型对完整结果的采用；本课只验证教材 CLI 到 `read_file` 的函数路径。
- 下次从哪一小步继续：Day 37，进入执行工具；先区分感知工具的信息风险与执行工具的副作用风险。
- 状态：✅。

## 10. 讲师备注

- 学员于 2026-11-24 亲自运行 CLI 接口查询、`max_length=120` 基线和 `max_length=-1` 对照，必要真实字段已记录在第 3、6 节。
- `run_experiment_4_2.py --help` 在脚本导入阶段失败：`ImportError: cannot import name 'Client' from 'mcp'`。这是当前根 ch4 extra 与实验 MCP v2 要求不一致的环境证据，不是感知工具业务实验结果。
- 本课用 CLI 直接调用同一实现，明确限制证据范围；若后续要验证 MCP stdio，需按实验 README 用 MCP v2 隔离环境另行核实，不修改 `vendor/`。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过。
- 进度处理：基线、单变量对照、关键源码与三项理解检查均完成，追加 Day 36 `✅` 日志。
