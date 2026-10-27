# Day 15 — 工具定义怎样把用户意图约束成正确的工具调用

> 章节 `vendor/ai-agent-book/book/chapter2.md` § 工具定义的设计（第 700–728 行）
> 实验 `vendor/ai-agent-book/chapter2/local_llm_serving/`（直接调用本机 Ollama，只观察首轮工具请求）
> 备课日期 2026-10-27 ｜ 授课日期 2026-10-27 ｜ 状态 ▶

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：工具定义是模型看到的接口契约

**本课只回答**：工具的名称、描述和参数 schema 怎样共同帮助模型把用户意图映射为正确的工具调用；出现错选或错参时，怎样用请求与响应证据判断问题首先发生在哪一层？

**类比**：Android/HarmonyOS 中，模型像只拿到 AIDL/接口声明的客户端，工具实现像远端 Service。函数名类似方法名，`description` 类似“何时调用、不能做什么”的契约注释，参数 schema 类似可校验的 Parcel 结构。Service 即使把错误请求执行得毫无异常，也不能补救客户端一开始选错接口或填错字段；反过来，正确的 `tool_call` 也不能证明 Service 已经执行。

```text
用户意图
   |
   v
模型实际收到的 tools 字段
  name + description + parameters(JSON Schema)
   |
   v
assistant.tool_calls  -------->  Harness 校验/执行  -------->  tool 结果
  选择与参数证据                 执行证据                     回传证据
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：让本机模型在“按路径找文件”和“按正文搜内容”两个近邻工具之间选择，并为正文搜索构造参数。
- 学员会亲自运行：先发送边界、参数例子和返回值都完整的工具定义；再保持模型、用户任务、工具名和 schema 结构不变，只递归移除所有 `description`。
- 会得到的产物：两份 JSON 收据，分别保存实际发给 Ollama 的 `tools`、用户消息和模型返回的 `tool_calls`；本实验故意不执行工具，以隔离“模型如何选工具/填参数”。
- 结束时要能回答：一次工具错选或错参，应先查工具定义、模型决策、Harness 校验还是执行实现；单次对照又能和不能证明什么。

**确认问题**：如果模型选择了错误工具，但那个工具随后执行成功且返回合法 JSON，你认为首个故障点在“工具实现”还是“模型看到的接口契约与选择”这一段？只选一个，并说一句理由。

**学员回答**：选择“模型看到的接口契约与选择”；因为已知模型选错了工具，而随后执行成功并返回合法 JSON，说明首个分歧不在执行阶段。讲师校准：该证据足以定位本次链路的首个故障点，但一次成功只证明这组调用完成且返回结构合法，不能外推整个工具实现对所有输入都没有问题。

## 2. 实验前预测

- 任务与输入：`在工作区的 Python 文件正文中查找 runtime_guard；不要按文件名搜索。`
- 固定条件：同一 `qwen3:8b`、同一用户消息、同一 temperature、相同工具名、相同参数类型/required 约束、相同工具顺序；两次都只请求模型生成首轮动作，不执行工具。
- 观察点：请求里的完整 `tools`；响应是否含 `tool_calls`；所选函数名；`query`、`path_glob` 或 `pattern` 参数；是否生成纯文本而未调用工具。
- 预测问题：移除所有函数级和参数级描述后，模型的工具名与参数会不会改变？为什么？允许预测“不变”，但必须说明依据。
- 学员预测：完整定义下会选择 `search_text`；参数一为 `runtime_guard`，参数二为“目标 py 文件的路径”。讲师校准：工具选择与 `query` 语义正确；由于任务没有提供某个已知文件，`path_glob` 应描述搜索范围（如 `**/*.py`），不能虚构具体路径。

## 3. 运行实验

正式实验由学员在 PowerShell 中亲自运行。讲师已通过源码核实 Ollama 请求入口与工具 schema 形状，不在学员预测前代跑目标请求。课堂一次只给当前步骤的一段 PowerShell，等待真实输出。

### 步骤 A：进入目录并准备环境

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\local_llm_serving'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
[pscustomobject]@{
    Location = (Get-Location).Path
    NO_PROXY = $env:NO_PROXY
    PYTHONUTF8 = $env:PYTHONUTF8
    PYTHONIOENCODING = $env:PYTHONIOENCODING
}
```

### 步骤 B：在当前 PowerShell 会话准备同一个探针

这一步只把探针文本放入 `$probe`，不调用模型。探针会构造两项工具定义；运行时由 `D15_STRIP` 决定是否递归移除 `description`，并把“实际请求 + 原始首轮响应”写入对应 JSON。

**步骤 A 真实输出**：学员在 PowerShell 中确认 `Location=F:\AIStudy\vendor\ai-agent-book\chapter2\local_llm_serving`、`NO_PROXY=localhost,127.0.0.1,::1`、`PYTHONUTF8=1`、`PYTHONIOENCODING=utf-8`；环境准备通过，尚未调用模型。

**步骤 B 真实输出**：`probe_ready chars=2917`；说明同一份内存探针已装入当前 PowerShell 会话，尚未调用模型。由于探针含中文，正式运行时另行固定 PowerShell 原生管道的 `$OutputEncoding` 为 UTF-8，避免输入在到达 Python 前被改写。

```powershell
$probe = @'
import json
import os
from pathlib import Path
import ollama

task = "在工作区的 Python 文件正文中查找 runtime_guard；不要按文件名搜索。只选择工具并填写参数。"
tools = [
    {
        "type": "function",
        "function": {
            "name": "find_files",
            "description": "仅当需要按文件名或路径模式定位文件时使用；不读取文件正文，不能搜索内容。返回匹配路径列表。",
            "parameters": {
                "type": "object",
                "properties": {
                    "pattern": {
                        "type": "string",
                        "description": "文件名或路径 glob，例如 **/*.py。不要放正文关键词。",
                    }
                },
                "required": ["pattern"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_text",
            "description": "当需要在文件正文中查找字符串时使用；不能只按文件名定位。返回文件路径、行号和匹配片段。",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "要在正文中查找的原始字符串，例如 runtime_guard。",
                    },
                    "path_glob": {
                        "type": "string",
                        "description": "限制搜索文件，例如 **/*.py。",
                    },
                },
                "required": ["query", "path_glob"],
            },
        },
    },
]

def strip_descriptions(value):
    if isinstance(value, dict):
        return {k: strip_descriptions(v) for k, v in value.items() if k != "description"}
    if isinstance(value, list):
        return [strip_descriptions(v) for v in value]
    return value

stripped = os.environ.get("D15_STRIP") == "1"
sent_tools = strip_descriptions(tools) if stripped else tools
response = ollama.chat(
    model="qwen3:8b",
    messages=[{"role": "user", "content": task}],
    tools=sent_tools,
    stream=False,
    options={"temperature": 0},
)
message = response["message"]
message_data = message.model_dump(exclude_none=True) if hasattr(message, "model_dump") else dict(message)
variant = "no-description" if stripped else "described"
receipt = {
    "variant": variant,
    "request": {
        "model": "qwen3:8b",
        "messages": [{"role": "user", "content": task}],
        "tools": sent_tools,
        "options": {"temperature": 0},
    },
    "response_message": message_data,
    "executed": False,
}
path = Path(f"lesson-d15-{variant}.json")
path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({
    "variant": variant,
    "description_count": json.dumps(sent_tools, ensure_ascii=False).count("description"),
    "tool_calls": message_data.get("tool_calls", []),
    "content": message_data.get("content", ""),
    "executed": False,
    "receipt": str(path.resolve()),
}, ensure_ascii=False, indent=2))
'@
"probe_ready chars=$($probe.Length)"
```

### 步骤 C：完整定义基线

- 输入/读取：同一任务和两个工具的完整定义。
- 处理/筛选：Ollama 把消息与工具 schema 送给 `qwen3:8b`；脚本只抽取首轮 `tool_calls`，不执行函数。
- 输出/用途：`description_count` 先证明本次请求确实保留描述；`tool_calls` 用于判断模型选了谁、参数是什么；`executed=false` 防止把请求误当执行。

```powershell
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:D15_STRIP = '0'
$probe | python -m uv run --extra ch2 python -
```

**真实证据**：学员运行基线后得到 `variant=described`、`description_count=5`；模型返回 `search_text(query="runtime_guard", path_glob="**/*.py")`，`content` 为空，`executed=false`。收据 `lesson-d15-described.json` 复核到请求含 2 个函数级描述和 3 个参数级描述，模型、任务、工具名与参数均与摘要一致。该证据证明本轮实际请求携带完整定义且模型生成了预测调用，不证明工具已执行或正文中真实存在匹配。

## 4. 逐段读真实轨迹

| 片段 | 真实摘录 / 证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 请求 | `request.tools`、`request.messages` | 模型本轮实际获得哪些契约信息 | 模型一定正确理解 | 能结合 5 处描述确认完整契约进入请求 |
| 模型动作 | `response_message.tool_calls` | 基线选择 `search_text`，参数为 `runtime_guard` 与 `**/*.py` | Harness 已执行、搜索有结果 | 能把正确调用请求与真实执行分开 |
| 执行层 | `executed=false` | 本探针刻意没有执行 | 任何真实文件命中或副作用 | 判断不能证明已搜索或找到代码；最初同时引用 `content=""`，经校准后指出空文本只证明没有普通文本输出，未执行的直接证据是 `executed=false` |
| 最终答案 | 本探针无第二轮 | 不把文本正确性混进选择实验 | 完整 Agent 任务成功率 | 待回答 |

## 5. 追到关键源码

**源码锚点**（当前版本已核实）：

- `vendor/ai-agent-book/chapter2/local_llm_serving/tools.py:111-123`：把注册表中的 name、description、parameters 组装为 OpenAI/Ollama 兼容 schema。
- `vendor/ai-agent-book/chapter2/local_llm_serving/ollama_native.py:125-140`：先把用户消息追加到历史，再转换工具并把 `messages` 与 `tools` 一起送入模型请求。原备课锚点误写为 119–128；学员实读暴露偏移后已按当前源码修正。
- `vendor/ai-agent-book/chapter2/prompt-engineering/ablation_utils.py:154-160`：深复制后递归移除描述；这正是对照变量的教材实现。

- 输入 → 处理 → 输出：注册的工具元数据 → schema 列表 → 模型首轮 `tool_calls`。
- 关键边界：`get_tool_schemas()` 只建立模型可见契约；执行发生在后续 `_execute_tool_calls()`，两者不能互证。
- 真实源码阅读：学员已亲自读取 `tools.py:111-123`，确认第 114 行遍历 `self.tools`，第 115–121 行把 `name`、`description`、`parameters` 放入 `type=function` 的列表元素，第 123 行返回 schema 列表。学员指出第 119–120 行直接取原工具的描述与参数，不会理解实现或自动补全含糊说明；讲师补充第 118 行的工具名也同样直接转发。随后按讲义旧锚点读取 `ollama_native.py:119-128`，实际只看到流式分支和第 125–128 行追加用户消息，未看到工具请求；据真实输出重新定位并读取第 125–140 行：第 131 行按 `use_tools` 转换工具，第 135–140 行发起模型调用，其中第 137、138 行分别传入消息历史和工具列表。学员正确区分第 131 行的本地准备与第 138 行的请求参数传递，但最初称为“把 tools 加入上下文轨迹”；经校准为 `tools` 是与 `messages` 并列的顶层请求参数，属于模型可见上下文但不是消息轨迹元素。
- 换例复查：学员判断统计 `messages` 角色时不能把顶层 `tools` 算作第四条消息，因为它是工具声明而不是消息角色。通过。
- 学员已读取 `ablation_utils.py:154-163`：第 154 行逐工具处理，第 156 行深复制，第 158–159 行递归删除所有描述，第 161–163 行收集并返回新列表。至此课堂实际阅读关键源码共 39 行（13 + 16 + 10），未通读无关实现。学员解释深复制会产生独立数据，修改副本不影响原结构；若直接删除原对象的描述，对照将失去意义。讲师补充其具体风险是基线被原地污染、结果依赖运行顺序。

## 6. 单变量对照

- 唯一改变的条件：`D15_STRIP: 0 -> 1`，递归删除工具级与参数级全部 `description`。
- 保持不变：模型、用户消息、temperature、工具名、工具顺序、参数名、类型和 required；仍不执行工具。
- 对照前预测：可能选错工具，甚至不调用工具；理由是缺少 `description` 后，模型失去了工具何时调用、能做什么和返回什么的信息。讲师校准：描述确实移除了明确触发条件、禁止边界、参数例子和返回说明，但工具名、参数名、类型与 required 仍可提供语义和结构线索，因此结果也可能与基线相同。

```powershell
$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$env:D15_STRIP = '1'
$probe | python -m uv run --extra ch2 python -
```

产物 `lesson-d15-no-description.json`。收到输出后再用一条只读命令核对两份收据中除描述外是否还有非预期差异。

**真实对照证据**：学员运行得到 `description_count=0`，证明描述消融生效；模型仍选择 `search_text`，`query` 仍为 `runtime_guard`，但 `path_glob` 从基线的 `**/*.py` 变为 `*.py`。两次均为 `content=""`、`executed=false`。讲师只读归一化两份收据后得到 `RequestEqualAfterRemovingDescriptions=True`，说明请求在删除描述后其余模型、消息、工具名、参数 schema、顺序与 options 相同；参数对象的键顺序不同不作为语义差异。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 工具选择和 `query` 未变；`path_glob` 从 `**/*.py` 变为 `*.py`；请求归一化后确认唯一输入变化是描述 | 工具名与参数名足以支持本次选对工具；描述中的具体例子可能影响了搜索范围写法 | 工具未执行，尚不能断言 `*.py` 在具体实现中必然漏掉子目录或任务失败；可核对实现契约、执行结果、重复运行或换边界任务 |

学员判断需要结合工具执行记录才能确认是否失败。讲师补充：还需把实际参数和返回对照工具实现的 glob 语义，并用工作区文件分布作为权威状态；本探针刻意没有实现或执行 `search_text`，因此不补造搜索结论，`*.py` 是否漏目录保持为待验证判断。

## 7. 理解检查：每次只问一题

1. **概念复述**：name、description、JSON Schema 各自主要提供什么约束？为什么 schema 类型正确仍可能语义错？
2. **结果预测**：若把 `path_glob` 的例子从 `**/*.py` 改成 `*.py`，哪些参数行为可能变化？一次没变化能否证明例子无用？
3. **故障定位**：收据显示模型发出 `find_files(pattern="runtime_guard")`，而 Harness 成功返回空列表。你先查哪一层？需要哪些证据区分“定义含糊”“模型错选”和“执行实现错误”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. name 提供短标签，description 提供触发条件、边界、例子、返回与代价，schema 提供结构、类型、必填与有限枚举；schema 通常不能表达“正文关键词不能放进文件名模式”这类完整语义。
2. 可能影响递归范围与参数写法；单次不变只能说明这次模型仍由其他线索完成调用，不能证明示例普遍无效。
3. 先核对实际 `request.tools` 和首轮 `tool_calls`：若模型已拿到清晰边界却错选，重点在模型决策；若边界缺失/含糊，先修定义；只有调用本身正确而工具返回与权威文件不符，才进入执行实现与搜索范围诊断。

</details>

卡住时记录具体缺口，换小例子补讲，再检查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选 AI 代码演示

本课不新增工程或依赖。PowerShell 中的内存探针由讲师提供，学员负责预测、运行和解释；两份 JSON 产物由实验正常生成在被忽略的 `vendor/` 目录。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：▶（已开课，尚未完成实验、源码、对照和三项检查）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-10-27；已进入概念建立，等待首个确认问题。
- 实验命令、模型、证据位置与必要真实摘录：使用 `qwen3:8b`；步骤 A 已确认目录、代理绕过与 UTF-8 环境变量，步骤 B 得到 `probe_ready chars=2917`。步骤 C 基线收据为 `local_llm_serving/lesson-d15-described.json`：实际请求含 5 处描述，响应调用 `search_text(query="runtime_guard", path_glob="**/*.py")`，且 `executed=false`。对照收据 `lesson-d15-no-description.json` 的描述为 0，工具与 query 不变，`path_glob="*.py"`；归一化核对确认请求侧除描述外一致。
- 学员的原始预测与后续解释：概念确认时准确把首个故障点定位到模型可见契约与选择层；最初把一次执行成功概括为“工具实现没有问题”，经校准后保留为本次调用层面的证据，不外推全部输入。基线预测选择 `search_text`，`query=runtime_guard`；最初把 `path_glob` 表述为目标文件路径，经校准为任务未给定文件时应使用 Python 文件范围模式。基线后能判断收据不能证明搜索或命中；经提示进一步区分 `content=""` 只是无普通文本，`executed=false` 才是未执行的直接证据。对照前预测删掉描述后可能错选或不调用；经校准后补充工具名、参数名和 schema 仍保留线索，不能预设必然失败。对照后提出需结合工具执行记录判断 `*.py` 是否导致失败，并接受实现语义与权威文件状态也是必要证据。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 是否采用代码演示及原因：采用内存探针，避免改 `vendor/` 源文件，并把模型选择与真实工具执行隔离。
- 本次观察、假设、待验证点：不预设移除描述必然导致失败；有无差异都先核对实际请求与单变量条件。
- 对应备课 skill：`vendor/ai-agent-book/skills/tool-design/SKILL.md`。
- 进度处理：尚未追加完成日志；完成或明确中断后按规则追加 Day 15。
