# Day 1 — Agent 三要素：大脑、眼睛、手脚

> 章节 `vendor/ai-agent-book/book/chapter1.md` § 现代 Agent = LLM + 上下文 + 工具（第 11–36 行）
> 实验 `vendor/ai-agent-book/chapter1/context/`
> Kotlin `kotlin/d01-agent-three-elements.kts`
> 日期 2026-10-13 ｜ 状态 ✅

---

## 1. 今日一问（先猜，别往下看）

> 一个 Agent 调了两次工具，两次都正常返回了，最后答案却是错的。
>
> 在你脱口而出"这模型不行"之前 —— 按你平时调安卓 bug 的直觉，**还有哪两个地方该先查？**

（先答，再往下看。你大概率能答对一半。）

## 2. 你已经会这个了

你调安卓 bug 的那套直觉，和 Agent 的三要素是一一对应的。一个界面显示错了，你从来不会只怀疑"业务逻辑写错了"，你会同时查：**它拿到的数据对不对**、**它调的服务返回值对不对**。Agent 一模一样。

| 你熟的 | 今天的 |
|---|---|
| Activity 里的业务逻辑 | **LLM**（大脑 / 学术上叫策略 Policy） |
| Activity 能读到的 Intent、Bundle、成员变量状态 | **上下文**（眼睛 / 观察与历史） |
| 权限授权后能调的系统服务 | **工具**（手脚 / 观察与行动接口） |
| 三者之外的系统、网络、文件、用户 | **Environment** —— 注意：**它不在公式里** |

代码对读：

```kotlin
// 你熟的：一个 Activity 能干活，靠的也是这三件
class PayActivity : AppCompatActivity() {          // 逻辑（大脑）
    val order = intent.getParcelableExtra<Order>()  // 能看到什么（眼睛）
    payService.charge(order)                        // 被授权能动什么（手脚）
}
```
```python
# 今天的：vendor/ai-agent-book/chapter1/context/agent.py 里真正发出去的请求体
# model      -> 大脑
# messages   -> 眼睛（system + history + tool 结果，全塞在这里）
# tools      -> 手脚（4 个：parse_pdf / convert_currency / calculate / code_interpreter）
```

## 3. 最小知识

**Agent = LLM + 上下文 + 工具。** 加号是"工程组件的组合"，不是数学。三个关键边界：

1. **公式不包含 Environment。** Agent 只能通过"观察"和"行动"两个接口跟环境打交道，环境自己不归 Agent 管。
2. **上下文 ≠ 观察空间。** 上下文是"这一刻真正塞进模型的东西"，观察空间是"理论上能塞的东西"。**没进上下文的，对模型等于不存在。**
3. 进生产后公式改写为 **Agent = Model + Harness**，其中"上下文 + 工具"是最小 Harness。这是 Day 5 的内容，今天先记住有这么回事。

```
        ┌──────────── Agent 边界 ────────────┐
        │                                    │
 用户 ──┼──► 上下文(眼睛) ──► LLM(大脑) ──┐  │
        │        ▲                        │  │
        │        │                        ▼  │
        │        └──── 工具结果 ◄── 工具(手脚)┼──► Environment
        │                                    │    (文件/网络/DB/其他Agent)
        └────────────────────────────────────┘
                    这一圈就是 ReAct 循环（Day 3）
```

书里原话在 `book/chapter1.md:11-36`，想深挖自己去看，这里不抄。

## 4. 跑一下

### A. Python 观察（只跑不改）

**cmd.exe（本机常用）** —— 环境变量已永久设好，新开窗口直接跑：

```cmd
cd /d F:\AIStudy\vendor\ai-agent-book\chapter1\context
set PYTHONUTF8=1 && set PYTHONIOENCODING=utf-8
python -m uv run --extra ch1 python main.py --mode single --provider ollama --context-mode full --task "1000 美元等于多少欧元？再换算成人民币是多少？"
```

Git Bash 版（`VAR=value cmd` 这种前缀写法**只有 bash 认，cmd 里会被当成命令名**，这是踩过的坑）：

```bash
cd /f/AIStudy/vendor/ai-agent-book/chapter1/context
PYTHONUTF8=1 PYTHONIOENCODING=utf-8 \
  python -m uv run --extra ch1 python main.py \
  --mode single --provider ollama --context-mode full \
  --task "1000 美元等于多少欧元？再换算成人民币是多少？"
```

首次跑 uv 会建虚拟环境装依赖，约 1–2 分钟；之后每次几十秒。

**应该看到**：

- `Agent initialized with provider: ollama, model: qwen3:8b, context mode: full`
- 若干轮 `Iteration N/10`，中间夹着 `HTTP Request: POST http://localhost:11434/v1/chat/completions "HTTP/1.1 200 OK"`
- 结尾汇总 `Terminal text response (no tool calls); stopping after iteration N`、`Tool Calls: 2`
- 中间会打印一整段 `ITERATION N - FULL RESPONSE`，里面有个 `"reasoning"` 字段 —— **这段是今天的重点，务必读它**。

#### 本机三次实跑，同一个任务、同一个模型、同一个 context mode，三条不同轨迹

| | 第 1 次 | 第 2 次 | 第 3 次（学员自己跑的） |
|---|---|---|---|
| iterations | 2 | 3 | 3 |
| 工具调用 | 2 次 | 2 次 | **3 次** |
| 关键差异 | 第 2 次传 `1000 USD→CNY` ❌ | 第 2 次传 `920 EUR→CNY` ✅ | 第 2 次传 `'${result_of_first_conversion}'` → **工具报错** → 第 3 次传 `920 EUR→CNY` ✅ |
| 最终答案 | 张冠李戴 | 正确 | 正确（**自己从报错里纠回来了**） |

第 3 次那条最值得记住：模型把一个**未被替换的模板占位符**当实参发了出去，工具返回 `could not convert string to float: '{result_of_first_conversion}'`，模型读到这条报错，下一轮就传对了 `920`。

**这就是 Day 2 `no_tool_results` 消融要摘掉的东西。** 如果把工具报错从上下文里过滤掉，第 3 次轨迹就会停在错误上 —— 纠错能力直接消失。学员在写 `kotlin/d01-*.kts` 时自己独立推出了这条结论并写成 assert（"不能把错误反馈过滤掉，否则模型无法根据它调整参数"），**这是今天最值钱的收获**。

**第 1 次那个错，才是"大脑没问题"的最好证据。** 模型在 `reasoning` 里**自己发现了错误** —— 原话大意是"用户要的是把 920 欧元换成人民币，但第二次工具调用转的是 1000 美元到人民币，Wait, there's a discrepancy here"。它甚至先写出了正确方案（`amount=920, from_currency=EUR`）。然后纠结四百多字，反复推翻自己，最后一句是 "The assistant's initial tool calls were **correct**" —— 把错误宣布为正确，然后交卷。

**所以真正的结论有三条：**

1. 模型不笨，它**看见了**问题。bug 不在大脑。工具也忠实执行了它收到的调用。信息全都在上下文里躺着（第一次结果 920 EUR、第二次调用的 `from=USD`）。坏的是 —— **没有任何机制强迫它服从自己看见的证据**。这个洞 Day 5 的 Harness（约束 + 验证 + 纠正）来补。
2. **同参数三次跑，三条轨迹。** 这不是 bug，是采样的本质。而它直接引出第 7 章那句话：*没有评估，就分不清「设计带来的提升」和「随机波动」*。你今天亲眼看到这个了 —— 以后你改了一版提示词、跑一次变好了，你**凭什么**说是提示词的功劳？记住这个疑问，W9 会回答它。
3. **纠错能力来自"看得见报错"。** 第 3 次能自愈，唯一原因是工具的错误信息进了上下文。这是 Day 2 消融实验的预测，也是 Day 3 ReAct 循环里 Observe 那一步存在的理由。

#### 顺带抠一个区分：「工具调用正确」有三层，别混着说

| 层次 | 第 1 次跑的情况 | 坏了该修哪 |
|---|---|---|
| 选对了工具吗 | ✅ 就是 `convert_currency` | 工具定义 / description（Day 15） |
| **传对了参数吗** | ❌ **只有这层坏了** | 验证器（Day 5 Harness） |
| 工具返回正确吗 | ✅ 7240 是 `1000 USD→CNY` 的真值 | 工具本身 |

工程上这不是咬文嚼字：三层坏在不同地方，修法完全不同。团队里只说"工具调用不对"，两个人会去查两个完全不同的地方。

**如果看到 `502 Bad Gateway`** → `NO_PROXY` 没生效。用户级环境变量已经永久设好了，但**只对修复之后新开的终端生效**；当前窗口就地补 `set NO_PROXY=localhost,127.0.0.1`（cmd）。不是 Ollama 坏了。
**如果看到 `EOFError: EOF when reading a line`** → 漏了 `--task`，它进交互式菜单了。
**如果看到 `ModuleNotFoundError: No module named 'pypdf'`** → 漏了 `--extra ch1`。
**如果看到 `NameError: name 'openai' is not defined`** → 这是上游 bug（`agent.py:909` 异常处理里引用了没导入的模块），它盖住了真实错误，往上翻几行看原始异常。今天你就被它盖住了一次 502。

### B. Kotlin 手写（你自己写实现，我只给 assert）

打开 `kotlin/d01-agent-three-elements.kts`。里面已经定义好了 `Llm` / `Tool` / `Context` 三个规格，你要实现两个函数：

- `assemble(llm, ctx)` —— 把三要素装进一个请求体 Map，对照实验里打印的那个真实 JSON 结构
- `executed(requestedIds, returnedIds)` —— 判断模型"说要调"的工具是不是真的调成了

文件末尾 4 组 assert 已经写死，**不许改**。第 3 组是边界：`llm` 为 null 时必须抛 `IllegalArgumentException`，不能带病发请求。第 4 组埋了个坑：`listOf("call_1")` 和 `listOf("call_9")` 数量一样但 id 对不上，只比 size 会挂。

```bash
export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"
/c/tools/kotlinc/bin/kotlinc kotlin/d01-agent-three-elements.kts -script \
  -J-ea -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8
```

`-J-ea` 别省 —— Kotlin 的 `assert()` 默认关闭，不加它 `assert(false)` 也会静默通过。**`-J-Dstderr.encoding=UTF-8` 也别省** —— assert 失败和异常栈走的是 stderr，只给 `stdout.encoding` 的话中文报错全是乱码（本机已实测：只加 stdout 时 `AssertionError` 的中文消息变成 `STDERR ĶϢ` 这种鬼画符）。现在跑应该看到 `kotlin.NotImplementedError` 停在 `assemble` 那行、退出码 3，**这是对的**，说明自检有效。全部填完后应该打印 `Day 1 全部 assert 通过。`

> 讲师红线：我不会给实现。你敲完、跑通、再拿回来，我逐条 assert 跟你过。

## 5. 自检三问

1. 书里说"上下文是 Agent 的眼睛"。那么**观察空间**和**上下文**的区别是什么？为什么"没进上下文的信息对模型等于不存在"这句话在工程上很重要？
2. 今天这个错答案，如果只允许你改**一件**事来修（换更强的模型 / 改工具 / 改上下文），你选哪个？为什么？
3. `Environment` 不在 `Agent = LLM + 上下文 + 工具` 这个公式里。那 Harness 能不能"拥有"环境？

<details><summary>答案</summary>

1. 观察空间是**理论上能拿到**的全部信息（可读的文件、可查的接口、能看到的屏幕），上下文是**这一刻真正塞进模型**的那部分。工程上重要，是因为它把"模型不够聪明"这类模糊归因，变成了一个可操作的检查项：先确认信息到底进没进上下文。书里 `chapter1.md:39` 的原话是"没有通过观察通道进入上下文的信息，对模型来说就像不存在"。今天的 bug 恰好是反面案例 —— 信息**进了**上下文，模型也读到了，但没有机制强迫它服从，所以"进了上下文"是必要条件，不是充分条件。

2. 三件里最该动的是**工具**，但要看你想修什么。换更强的模型能降低概率却修不了根 —— 今天的模型已经"看见"了错误，是缺一道**验证**，不是缺智商。改工具（比如让 `convert_currency` 在返回里带上它实际执行的 `from`/`to`，让错配一眼可见）成本最低、收益最直接。而真正治本的是 Day 5 的 Harness：加一道"最终答案里的每个数字必须能追溯到某次工具结果"的校验。**所以答案是：短期改工具，长期补 Harness，换模型是最后选项。**

3. 不能。Harness 可以**创建、隔离或代理**一个环境（比如起一个沙盒、开一个虚拟浏览器），但环境自身的状态和转移规律不归 Harness 所有。这条边界很重要：它决定了"Agent 出问题"该往哪边查 —— 是 Harness 没把观察转成上下文，还是环境本身返回了意外结果。见 `chapter1.md:25`。

</details>

## 6. 卡点（课后学员自己填）

- 卡在：
- 下次开课前要先复习：
- 状态：`✅` / `▶` / `🔁`

## 7. 讲师备注（只给 agent 看，学员可忽略）

- 实际用时：**远超 30 分**（含环境搭建）。纯教学部分约 40 分，其中 **assert 返工占了三轮**。Day 2 要砍掉一半容量。
- 偏离计划：环境首次打通花了额外时间，四个坑已写进 `AGENTS.md`（`--task` 必填、`NO_PROXY`、`--extra ch1`、Kotlin 的 `-J-ea`）。其中 `NO_PROXY` 已做成用户级永久变量，学员在 cmd 里用 bash 前缀语法踩了一次坑，讲义 §4A 已改成 cmd 语法为主。
- **学员对第 1 节的回答（记下来，Day 5 要回收）**：答了「先查工具是否调用正确」+「指令是否有歧义让智能体误解」。两处都对，且**没有甩锅给模型** —— 这说明三要素的框架已经装进去了。缺的是把「工具调用正确」拆成选工具/传参数/工具返回三层，已当场补上（见 §4A 的三层表）。

### 今天真正的教学产出不在计划里：**写断言的能力**

学员自己往"不许改"的自检区加了 7 条 assert，于是这一课变成了三轮断言返工。完整轨迹，**Day 2 之前值得重读**：

| 轮 | 他写成什么 | 病 | 我怎么让他看见的 |
|---|---|---|---|
| 1 | `contains(mapOf("role" to "tool", "content" to x))` | **过度指定** —— 锁死整个 Map 的键集，Day 9 加 `tool_call_id` 必炸 | 讲道理没用，直接说"你的测试会阻止你做正确的事" |
| 2 | `none { role == "system" }` | `none` 是 `any` 的反义词，且报错文案说"必须进入"、逻辑要求"必须不进入" | 跑给他看：`AssertionError: 系统提示词必须实际进入 messages` —— **消息和逻辑互相打脸** |
| 3 | `any { role == "system" }` | **指定不足** —— 只查 role 不查 content | `/tmp` 变异：把系统提示词内容换成 `""`，全绿 |
| 4 | `any { role == ... && content == ... }`，并用 `.all{text->}` 泛化到多条消息 | 对了，而且比我要求的更好 | 四条变异电池全过，**含"加 `tool_call_id` 应该仍然绿"** |

**有效的教法是"跑给他看"，不是讲道理。** 变异测试（在 `/tmp` 复制、注入一处坏、验完删）是唯一让他自己承认断言无用的手段。第二轮他自己看出第 102 行 `"user"` 该是 `"tool"`，也正是因为上一轮刚演示过变异。

**变异电池要固定包含"正当修改应该仍然绿"这一条**（今天的变异 C）。只测"坏了会红"不测"改了不炸"，等于只防了一半。这条以后每课的 assert review 都要跑。

- **AI 代写问题**：学员承认 ③「AI 写完我改的」，起因是"不知道怎么写，让 AI 写个示范"。已定规则（见 `AGENTS.md`「双轨制」）：AI 可查语法/看示范，但看完关掉自己敲；讲师随机指一行问"为什么在这"，答不上记 `🔁`。**第 3–4 轮的断言是他自己写的**（`.all{text->}` 泛化不是我提的），所以这个规则对他可行，不是空谈。
- **学员的短板画像：不是 Kotlin，是"写契约"。** 语言层面他零障碍（`as? Map<*,*>` 安全转换、`all/any` 嵌套、`buildList` 都用得很顺），卡的全是"这条断言该锁什么"。Day 2 起的 Kotlin 作业应该继续以**契约设计**为难点，别在语法上浪费他的时间。
- 本周依赖这个点的：**Day 2**（消融实验就是逐个摘掉三要素里的某一件，`--context-mode` 的 5 个取值 `full/no_history/no_reasoning/no_tool_calls/no_tool_results` 正好对应）、**Day 3**（ReAct 循环 = 今天这张图转起来）、**Day 5**（Harness 补的正是今天暴露的"没有机制强迫模型相信工具结果"这个洞）。
- **今天攒下三个值钱的案例，后面必须回收，别让它过去**：
  1. 「模型自己发现错误、写出正确方案、再说服自己接受错误」—— Day 2（`no_tool_results` 消融）和 Day 5（验证器）都要引用。
  2. 「同参数三次跑，三条不同轨迹」—— **随机波动 vs 真实提升**的活教材，W9 第 7 章评估开篇直接拿它当引子。
  3. 「工具报错原样进上下文 → 模型下一轮自愈；占位符 `'${result_of_first_conversion}'` 被回显」—— Day 2 消融的预测、Day 3 Observe 那一步存在的理由、W5 `log-sanitization` 的引子（**脱敏 ≠ 提炼**，学员今天答错过一次：他说"提炼后再进上下文"，那恰好会毁掉自愈）。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`、`vendor/ai-agent-book/skills/loop-engineering/SKILL.md`
- 本机 ollama 默认模型是 `qwen3:8b`（`agentbook/providers/registry.py:112-120`，`requires_key=False`）。完整模型清单和按章节的选用建议在 `AGENTS.md`。
