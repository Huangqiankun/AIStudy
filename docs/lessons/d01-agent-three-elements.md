# Day 1 — Agent 三要素：大脑、眼睛、手脚

> 章节 `vendor/ai-agent-book/book/chapter1.md` § 现代 Agent = LLM + 上下文 + 工具（第 11–36 行）
> 实验 `vendor/ai-agent-book/chapter1/context/`
> Kotlin `kotlin/d01-agent-three-elements.kts`
> 日期 2026-10-12 ｜ 状态 ⬜

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

```bash
cd /f/AIStudy/vendor/ai-agent-book/chapter1/context
NO_PROXY=localhost,127.0.0.1 PYTHONUTF8=1 PYTHONIOENCODING=utf-8 \
  python -m uv run --extra ch1 python main.py \
  --mode single --provider ollama --context-mode full \
  --task "1000 美元等于多少欧元？再换算成人民币是多少？"
```

首次跑 uv 会建虚拟环境装依赖，约 1–2 分钟；之后每次几十秒。

**应该看到**：

- `Agent initialized with provider: ollama, model: qwen3:8b, context mode: full`
- `Iteration 1/10` → 模型发出 **2 个 tool_calls** → `Iteration 2` → `Terminal text response (no tool calls); stopping after iteration 2`
- 结尾汇总：`Iterations: 2`、`Tool Calls: 2`、`Final Answer: 1000 美元等于 920 欧元，再换算成人民币约为 7240 元。`
- 中间会打印一整段 `ITERATION 2 - FULL RESPONSE`，里面有个 `"reasoning"` 字段 —— **这段是今天的重点，务必读它**。

**本机已实测到的现象（这才是今天的教材）**：模型在 `reasoning` 里**自己发现了错误** —— 它写的原话大意是"用户要的是把 920 欧元换成人民币，但第二次工具调用转的是 1000 美元到人民币，这里有个 discrepancy"。然后它纠结了整整四百多字，反复推翻自己，最后**说服自己接受了那个错答案**：把 `1000 USD→CNY = 7240` 当成了 `920 EUR→CNY`。

回到第 1 节那个问题：模型不笨，它**看见了**问题。所以 bug 不在大脑，在另外两件：

- **工具**返回的确实是 `7240`，没错 —— 但它是**另一个问题**的答案。工具忠实地执行了一个错误的调用。
- **上下文**里躺着正确的证据（第一次结果 920 EUR、第二次调用参数 `from=USD`），模型读到了、也推理出来了，**却没有任何机制强迫它相信工具结果而不是自己的圆场**。

这个"没有机制强迫"，就是 Day 5 的 Harness（约束 + 验证 + 纠正）要补的洞。今天先把三要素认清。

**如果看到 `502 Bad Gateway`** → 漏了 `NO_PROXY=localhost,127.0.0.1`，本地 Ollama 被塞进代理了，不是 Ollama 坏了。
**如果看到 `EOFError: EOF when reading a line`** → 漏了 `--task`，它进交互式菜单了。
**如果看到 `ModuleNotFoundError: No module named 'pypdf'`** → 漏了 `--extra ch1`。
**如果看到 `NameError: name 'openai' is not defined`** → 这是上游 bug（`agent.py:909` 异常处理里引用了没导入的模块），它盖住了真实错误，往上翻几行看原始异常。

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

- 实际用时：__ 分（超 30 就砍明天的内容）
- 偏离计划：无。今天是环境首次打通，`--task` 必填、`NO_PROXY` 必填、`--extra ch1` 必填三个坑都已写进 `AGENTS.md`。
- 本周依赖这个点的：**Day 2**（消融实验就是逐个摘掉三要素里的某一件，`--context-mode` 的 5 个取值 `full/no_history/no_reasoning/no_tool_calls/no_tool_results` 正好对应）、**Day 3**（ReAct 循环 = 今天这张图转起来）、**Day 5**（Harness 补的正是今天暴露的"没有机制强迫模型相信工具结果"这个洞）。
- **今天这个"模型自己发现错误却说服自己接受"的案例非常值钱**，Day 2 和 Day 5 都要回来引用它。别让它过去了。
- 对应备课 skill：`vendor/ai-agent-book/skills/context-engineering/SKILL.md`、`vendor/ai-agent-book/skills/loop-engineering/SKILL.md`
- 本机 ollama 默认模型是 `qwen3:8b`（`agentbook/providers/registry.py:112-120`，`requires_key=False`）。另有 `qwen3.5:9b` 可用 `--model` 指定。
