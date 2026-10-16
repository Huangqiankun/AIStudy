# Day 3 — Agent 为什么能根据工具结果继续下一步，而不是每轮从头开始？

> 章节 `vendor/ai-agent-book/book/chapter1.md` § ReAct 循环（第 161–232 行）
> 实验 `vendor/ai-agent-book/chapter1/context/`
> 备课日期 2026-10-16 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：循环靠轨迹推进，不靠模型“记住”

**本课只回答**：一次工具调用的观察，怎样进入下一轮决策并推动 ReAct 循环前进？

**Android / HarmonyOS 类比**：把 ReAct 看成 `Looper.loop()` + `Handler.handleMessage()`。

- `Looper` 不会只处理一条消息就消失，而是反复取消息、分发、再等待下一条。
- `Handler` 每次处理时依据当前消息与已有状态决定下一步；处理结果又可能投递新消息。
- ReAct 也不是模型在一次调用里把所有事做完：Model 决定动作，Harness 执行工具，把观察追加到轨迹，再调用 Model。

类比边界：Android 消息分发规则通常是确定的；LLM 面对相同轨迹仍可能选择不同动作。`reasoning` 是模型生成的可见文本，不等于可验证的内部思维；本课以请求、工具调用、工具结果和下一轮请求为证据。

```text
                 ┌──────────── 轨迹不断追加 ────────────┐
                 │                                      │
用户任务 → Model 决定下一步 → Harness 执行工具 → Observation
             │ 无 tool_calls                          │
             └──────────────→ 最终文本，循环停止 ←─────┘

每轮 Model 实际输入 = 静态前缀（system + tools）+ 当前轨迹
```

**最小概念**：

- **Reasoning**：模型基于本轮输入形成的决策说明；可辅助读轨迹，但不能单独证明工具已执行。
- **Acting**：模型发出结构化 `tool_calls`；Harness 校验并执行，真正改变或读取环境。
- **Observation**：工具返回的事实；只有被追加为 `tool` 消息并进入下一轮请求，才会推动后续决策。
- **终止**：当前实现中，模型返回不含 `tool_calls` 的文本时正常停止；最大迭代数是防死循环的安全上限，不是“必须跑满”的目标。

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：先读取本地费用 PDF，取得日本办公室金额，再把该金额换算成美元。
- 学员会亲自运行：先跑 `full` 基线，再只把上下文模式改成 `no_history`。
- 会得到的产物：两个 JSON；重点看每轮 `api_turns[].request.messages`、响应中的 `tool_calls`、本地 `tool_calls` 记录和最终答案。
- 结束时要能回答：观察靠哪条消息进入下一轮；删掉历史后，为什么“工具确实执行了”仍不等于循环取得进展。

**确认问题**：如果第 1 轮 `parse_pdf` 已在本地成功读出金额，但第 2 轮发给模型的请求仍只有最初的用户任务，没有上轮 `assistant.tool_calls` 和 `tool` 结果，你认为模型能知道“读取已经完成”吗？为什么？

**学员回答**：没有上一轮的工具调用和结果，说明它们没有进入本轮上下文，因此模型不知道 PDF 是否读完，甚至可能认为没有读。核心判断正确。进一步校准：“没有读”不是模型已经观察到的事实；准确说法是本轮没有“已经读过”的证据，模型只能像第一次收到任务一样重新决策，可能重读、改道、猜测或直接回答，具体行为仍需实测。

## 2. 实验前预测

- 任务与输入：读取 `fixtures/pdfs/simple_expense_report.pdf`，取得日本办公室费用，再用工具换算为 USD；回答原金额与换算结果。
- 固定条件：provider `ollama`、模型 `qwen3:8b`、任务文本和工具集合不变。
- 基线条件：`--context-mode full`，每轮携带完整累计轨迹。
- 观察点：每轮模型输入角色序列；模型选择的工具；工具实际参数与结果；下一轮是否看到上轮观察；循环因何继续或停止。
- 预测问题：你预计任务至少需要哪些“决定—执行—观察—再决定”事件？不要猜固定轮数；说明下一步决策必须依赖哪一条新证据。
- 学员首次回答：认为执行顺序本身就是“模型决定 → 工具执行 → 观察回传 → 再次决定”，不清楚是否需要说明每阶段的具体事件；对第二个工具调用，提出当前上下文要有对应工具声明，且 LLM 要判断用户任务与工具是否匹配。方向抓住了循环骨架和动作接口前提，但尚未把骨架代入本次任务，也未指出第二个工具参数必须依赖第一轮 `parse_pdf` 返回的日本办公室实际金额。
- 缩小问题：只补一个空——调用 `convert_currency(amount=?, from_currency="JPY", to_currency="USD")` 时，`amount` 应从哪条消息的什么内容取得？
- 学员补充：`amount` 从上一轮 PDF 文本解析结果取得，因为目标是从 JPY 换算为 USD。判断正确；进一步精确到协议证据：数值应来自上一轮 `parse_pdf` 对应的 `role="tool"` 消息中“日本办公室”这一项的实际日元金额，而不是用户任务文本或工具声明。实验前预测通过。

## 3. 运行实验

讲师已于 2026-10-16 运行 `--help`，核实当前 CLI 支持 `single`、`ollama`、`full`、`no_history` 与 `--output`。没有代跑本课目标实验。

**步骤 A：环境与 CLI 核对**（完成概念确认后提供；运行后等待学员贴回输出）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter1\context'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --extra ch1 python main.py --help
```

**步骤 A 真实证据**：2026-10-16 学员在 `F:\AIStudy\vendor\ai-agent-book\chapter1\context` 的 PowerShell 中实跑成功。帮助输出确认 `--mode single`、`--provider ollama`、`--context-mode full/no_history`、`--model` 与 `--output` 均存在。帮助末尾的示例使用 Bash 反斜杠换行，正式 PowerShell 实验改用单行命令。

**步骤 B：运行基线**（收到步骤 A 的输出并完成预测后再给）：

```powershell
python -m uv run --extra ch1 python main.py --mode single --provider ollama --model qwen3:8b --context-mode full --task '请读取 F:\AIStudy\vendor\ai-agent-book\chapter1\context\fixtures\pdfs\simple_expense_report.pdf，找到日本办公室的费用金额；然后必须使用 convert_currency 把这个实际金额从 JPY 换算成 USD。最后同时回答原始金额和工具返回的美元金额。若任一步没有真实结果，请明确说明，不要猜测。' --output lesson-d03-baseline.json
```

**准备观察什么**：不预设模型会采用固定轮数或一次成功。分别核对工具是否执行、结果是否进入下一轮请求、下一步是否使用了先前观察，以及最终文本出现时是否没有新的 `tool_calls`。

**基线真实证据**：2026-10-16 21:12–21:13，学员在 PowerShell 中使用 `ollama / qwen3:8b / full` 实跑成功，产物为实验目录下的 `lesson-d03-baseline.json`。共 3 次模型调用、2 次工具调用：第 1 轮模型请求 `parse_pdf(call_t12nr5lz)`，工具成功解析 1 页；第 2 轮请求中出现与该 ID 配对的 `tool` 消息，正文含 `Japan Office: ¥380,000,000 JPY`，模型随后请求 `convert_currency(call_61inq4cz, amount=380000000, JPY→USD)`；第 3 轮请求含换算工具结果 `converted_amount=2541806.02`，模型返回无 `tool_calls` 的最终文本，Harness 记录 `stopping after iteration 3`。`completed=True` 只证明收到终止文本；本次答案的数值依据还需分别与两条 `tool.content` 核对。

PDF 解析期间出现 `incorrect startxref pointer(1)` 与 `parsing for Object Streams` warning，但随后明确记录 `Successfully parsed PDF with 1 pages`，且真实正文进入下一轮请求，因此不能仅凭 warning 判定失败。第 1 轮可见 reasoning 曾举出假设值 `5,000,000 JPY`；它没有被当作工具参数，下一轮实际动作使用的是工具正文中的 `380,000,000 JPY`，可用于区分“模型生成的推演文字”和“环境观察证据”。

## 4. 逐段读真实轨迹

每次只展示一个片段，等学员解释后继续。以下区域只在实际运行后填写；演示输入输出必须标为“示意”。

| 片段 | 真实摘录 / 证据字段 | 本轮 Model 看见什么或决定什么 | Harness / Environment 做什么 | 学员解释 |
|---|---|---|---|---|
| 首轮请求 | `api_turns[0].request.messages` 角色为 `system,user`；顶层声明 4 个工具 | 只看见任务、系统指令和可选工具，尚未看见 PDF 正文 | 尚未执行本轮工具 | 学员已能根据后续请求判断首轮尚无正文；与下一行一起解释。 |
| 首轮响应与执行 | 响应为 `parse_pdf(call_t12nr5lz)`；日志随后记录成功解析 1 页 | 决定读取用户给出的路径 | Harness 执行 `parse_pdf` 并取得正文 | 学员用调用 ID 与工具结果配对，核心正确；字段名校准为上一轮 `assistant.tool_calls[0].id`，不是两边都叫 `tool_call_id`。 |
| 下一轮请求 | `api_turns[1].request.messages` 角色为 `system,user,assistant,tool`；`tool_call_id=call_t12nr5lz` | 首次看见包含日本办公室金额的真实正文 | Harness 将首轮 assistant 决策和 tool 观察组装进请求 | 学员指出 `assistant.tool_calls[0].id == tool.tool_call_id == call_t12nr5lz` 证明配对，且 `role=tool` 的 `content` JSON 内页文本包含 PDF 正文。解释通过。 |
| 后续动作 | 第 2 轮响应请求 `convert_currency(call_61inq4cz)`，参数为 `380000000, JPY, USD`；工具返回 `2541806.02` | 从 PDF 观察提取金额并决定换算 | Harness 执行换算并追加结果 | 学员用 `convert_currency` 及其参数和上一条 `role=tool` 正文中的 `380000000 JPY` 建立联系，方向正确。校准：顶层工具声明只证明接口可用；数据流证据是前一条 `tool.content` 的实际金额与随后 `assistant.tool_calls[].function.arguments.amount` 相等。 |
| 终止响应 | 第 3 轮请求角色为 `system,user,assistant,tool,assistant,tool`；响应 `finish_reason=stop`、无 `tool_calls` | 看见两次真实工具结果后生成最终文本 | Harness 因响应无工具调用而在第 3 轮退出 | 学员根据日志认为 `finish_reason=stop` 触发退出，而前两轮 `finish_reason=tool_calls` 表示仍需继续。现象关联判断合理，但当前源码真正分支条件是 `has_tool_calls = bool(message.tool_calls)`；本次 `finish_reason` 与它一致。`FINAL ANSWER:` 不是正常文本终止的必要条件，待源码段复查。 |

必须分开核对：模型提出动作、Harness 实际执行、观察进入下一轮、模型基于观察作出新决策、循环正常终止。任一项成立都不能自动推出后面各项成立。

## 5. 追到关键源码：三小段

行号按 2026-10-16 本地 checkout 核实；教材更新后重新定位，不手改 `vendor/`。

| 源码位置（`chapter1/context/agent.py`） | 输入 → 处理 → 输出 | 课堂问题 |
|---|---|---|
| `747-767`：循环与请求组装 | 当前累计消息 → `_prepare_messages_for_api()` → 本轮 API 请求 | 哪个变量是真正发给模型的本轮上下文？ |
| `810-841`：继续还是终止 | 模型响应 → 检查 `tool_calls` → 无工具则停止，有工具则把 assistant 消息追加到轨迹 | 谁决定动作，谁决定如何退出循环？ |
| `842-893`：执行与观察回传 | 工具名和参数 → `_execute_tool()` → `tool` 消息追加到 `messages` | 哪一行执行工具，哪一行让结果有机会被下一轮模型看到？ |

单变量对照后再读 `660-692`：`no_history` 只保留 system 与最新 user，丢弃此前 assistant 决策和 tool 观察。

关键片段总量控制在约 20–40 行，课堂分小段读取；Python 只解释循环、条件、字典和 `append` 到足以理解行为。

**终止分支源码解释**：学员定位到 `821 if not has_tool_calls` 进入终止分支、`837 break` 真正退出循环；当 `has_tool_calls=True` 时绕过该分支，进入 `839` 开始的继续路径。并结合第 3 轮请求说明上一轮 `call_61inq4cz` 与本轮同 ID 的 `tool` 消息配对，换算结果已进入上下文。进一步校准因果边界：`811` 不读取工具内容并自行判断任务完成，它只检查当前模型响应是否还有新 `tool_calls`；观察内容影响 Model 的下一步决定，Harness 根据该响应选择继续或停止。该段解释通过。

**执行与回传源码解释**：学员准确指出 `868 result = self._execute_tool(...)` 才真正执行工具并取得返回值；`875 self.trajectory.tool_calls.append(...)` 只是保存本地审计记录；`893 messages.append(tool_msg)` 才把包含角色、调用 ID 和结果内容的消息加入累计轨迹。若缺少 `893`，即使本地工具和审计均成功，下一轮 Model 仍看不到结果。该段解释通过。

**`no_history` 源码解释**：学员根据 `660-692` 判断每轮请求保留 `system/user`，并指出不会保留前轮的助手决策、工具调用和工具结果。补全角色表述：被丢弃的是 `assistant` 与 `tool` 两类历史消息；前者承载决策和 `tool_calls`，后者承载观察结果。因此即使 Harness 本地执行成功，下一轮请求仍像首次接到任务。源码注释中的“倾向于重复首个动作”是待实测的解释假设，不是必然结果。该段解释通过。

## 6. 单变量对照：移除上一轮轨迹

先问：**保持工具、模型与任务都不变，只把 `full` 改为 `no_history`；Harness 本地仍会执行工具，但下一轮模型输入会少什么？你预计循环可能怎样表现？** 等学员回答后再运行。

```powershell
python -m uv run --extra ch1 python main.py --mode single --provider ollama --model qwen3:8b --context-mode no_history --task '请读取 F:\AIStudy\vendor\ai-agent-book\chapter1\context\fixtures\pdfs\simple_expense_report.pdf，找到日本办公室的费用金额；然后必须使用 convert_currency 把这个实际金额从 JPY 换算成 USD。最后同时回答原始金额和工具返回的美元金额。若任一步没有真实结果，请明确说明，不要猜测。' --output lesson-d03-comparison.json
```

- 唯一计划改变：`--context-mode full` → `no_history`；任务、provider、模型和工具定义不变。
- 先检查干预生效：对照的后续每轮请求是否只含静态 system 与最新 user；不能只看最终答案或本地工具日志。
- 可能表现包括重复第一步、换一种首步动作、提前文本回答或达到安全上限；不预设哪一种必然发生。
- 即使某次答案正确，也要查它是否得到前轮观察支持，不能把参数记忆或猜测当成闭环证据。
- 同一模型也有采样差异；一次对照只记录本次观察，不推出普遍必然因果。

**学员对照前预测**：可能出错而不给出答案，也可能模型自行生成答案；因为 `no_history` 丢掉此前所有 `assistant` 与 `tool`，即使 Harness 已解析 PDF，结果也不会进入下一轮请求。预测有效并保留了不确定性。补充可能表现还包括重复首个动作、改道或耗尽安全上限；本次具体行为以真实轨迹为准。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| `lesson-d03-comparison.json` 显示 10 轮请求均只有 `system,user`，每轮都请求并成功执行 `parse_pdf`，共 10 次工具调用；没有 `convert_currency`，没有最终文本，`completed=False`。 | 与 `no_history` 删除 `assistant/tool` 历史、使每轮模型都像首次接到任务相容；本地执行成功没有变成模型可见的观察。 | 本次恰好重复首个动作直到上限，不代表所有模型或采样必然如此；可重复同条件或换模型/任务，检查是否仍表现为重复、改道或提前文本回答。 |

**对照真实证据**：2026-10-16 22:08–22:09，学员实跑 `ollama / qwen3:8b / no_history`，产物为实验目录下的 `lesson-d03-comparison.json`。10 个 `api_turns[].request.messages` 均为 2 条、角色序列 `system,user`；10 个响应均为 `finish_reason=tool_calls` 且工具名均为 `parse_pdf`，参数路径相同；本地日志每轮均记录 `Successfully parsed PDF with 1 pages`。运行达到 `Iteration 10/10` 后结束，汇总为 `Iterations: 10 / Tool Calls: 10 / Terminal response completed: False`。这证明本次失败点不是 PDF 解析器未执行，而是观察未进入下一轮模型请求，循环无法从“读取”推进到“换算”。

**学员对照解释**：判断失败点是执行结果没有进入下一轮模型请求。证据一是请求/响应 JSON 只能看到每轮重新声明并申请 `parse_pdf`，无法从后续请求看到工具结果；证据二是本地日志明确记录 `Reading local PDF...` 与 `Successfully parsed PDF with 1 pages`，证明工具实际执行成功。能够用两类证据排除解析器失败并定位观察回传边界，解释通过。

**日志来源追问**：学员询问上述解析日志是否由 `self.trajectory.tool_calls.append(tool_call_record)` 产生。源码核实后答案是否定的：`agent.py:103` 与 `135` 的 `logger.info(...)` 直接输出 `Reading...` 和 `Successfully parsed...`；`agent.py:875` 只把结构化 `ToolCall` 加入本地轨迹，不打印这些日志。该轨迹随后由 `main.py:653-660` 序列化进输出 JSON 的 `tool_calls` 数组。warning 来自 PDF 解析库，不由轨迹 append 产生。

## 7. 理解检查：每次只问一题

1. **概念复述**：ReAct 的“想—做—看”分别由谁完成？轨迹为什么能让下一轮不是从头开始？
2. **结果预测**：工具在本地执行成功，但它的 `tool` 消息没有进入下一轮请求，循环可能怎样表现？哪些表现不能仅凭一次运行断言？
3. **故障定位**：日志显示 `convert_currency` 被重复调用多次。你先比较哪两轮请求的哪些字段，才能区分“模型忽略了结果”和“Harness 根本没把结果发给模型”？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. Model 产生决策/工具请求，Harness 执行，Environment 返回观察；Harness 把 assistant 决策与 tool 结果追加到轨迹并在下一轮组装进上下文。
2. 可能重复、改道、猜测、提前停止或耗尽上限；不能由单次采样断言必然重复或必然失败。应查下一轮真实请求，而非只看本地日志。
3. 对比相邻 `api_turns[].request.messages`：上一轮响应的 `assistant.tool_calls[].id` 是否与下一轮 `tool.tool_call_id` 配对，`tool.content` 是否包含真实结果。若已传入但仍重复，才把问题进一步定位到模型策略；若未传入，先定位 Harness/模式干预。

</details>

卡住时缩小到“读取 PDF 后是否能看到金额”这一轮，再换成网络请求失败重试的小例子复查。实验、对照、源码和三项检查全部完成才记 `✅`。

**理解检查 1 首次回答**：学员认为第 1 轮 `finish_reason=tool_calls` 表示使用 `parse_pdf` 返回结果，第 2 轮请求的 `role=tool` 传入解析结果并声明 `convert_currency`，Model 根据任务决定换算；并将职责概括为 Model 判断调用工具或回答、Harness 调用工具、轨迹保存全局对话记录。主线基本正确，但有两处需校准：`finish_reason=tool_calls` 表示模型响应提出工具调用请求，不表示结果已经返回；Harness 执行后追加的 `role=tool` 才承载结果。轨迹是动态消息历史，不等于每轮完整上下文；本课基本式为“静态前缀（system + tools）+ 轨迹”。待换例复查。

**理解检查 1 换例复查**：在天气工具例子中，学员正确判断 `assistant.tool_calls=get_weather(...)` 由 Model 产生、属于 Action；`role=tool, content=...` 是下一轮 Model 请求中的 Observation。经追问后明确由 Harness 构造并 append `role=tool` 消息。Model / Harness / Environment 边界及“请求动作不等于返回结果”已校准，概念复述通过。

**理解检查 2**：面对已进入下一轮请求的 `Unsupported currency: JPX` 工具错误，学员预测 Model 可能停止并报告错误，也可能把 JPX 识别为笔误并推测 JPY；并指出不同模型可能采取不同策略，不能断言必然纠正。核心通过。校准：没有重复采样或统计证据时不应称某分支“大概率”；可能行为还包括修正参数重试、请求用户澄清或选择其他动作。错误反馈进入上下文只提供纠错机会，不保证纠错成功。结果预测通过。

## 8. 可选 AI 代码演示

默认不安排 Kotlin 或 Python 编程作业。真实 JSON 与 `agent.py` 的循环足以展示机制；若学员需要，再在本讲义内补最小示意，不增加依赖或另建工程。

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
- 是否采用代码演示及原因：未安排
- 本次观察、假设、待验证点：待开展
- 对应备课 skill：`vendor/ai-agent-book/skills/loop-engineering/SKILL.md`（仅取循环终止与防失控原则；本课核心机制仍以第 1 章源码为准）
- 备课核验：`context/main.py --help` 与上述源码行号已于 2026-10-16 核实；目标实验未代跑。
- 进度处理：课程中断或完成后再追加 Day 3 日志；备课本身不追加进度。
