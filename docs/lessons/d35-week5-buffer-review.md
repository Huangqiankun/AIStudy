# Day 35 — W5 记忆系统证据链复盘（buffer）

> 章节：W5 / ch3 buffer 复盘（`vendor/ai-agent-book/book/chapter3.md`）
> 实验：复用 Day 31 的离线评估器与示例 fixture，构造分层审计收据；不运行 Mem0、Memobase、模型或存储后端
> 备课日期 2026-11-23 ｜ 授课日期 待开展 ｜ 状态 待开展

本日是 buffer 日，不引入 Day 36 的感知工具，只复盘 Day 29–34。本文是分步教学材料，不一次性贴入对话；没有实际运行、源码讲解、单变量对照和三项理解检查时，不填完成状态。

## 1. 建立概念：绿灯属于哪一层，故障就要回到哪一层找

**本课只回答**：当记忆系统最终答错，但离线评估显示通过、日志脱敏也显示通过时，怎样沿证据链找到最早发生业务语义错误的阶段？

**类比**：Android 中可以分别拿到 Room 写入回执、DAO 查询结果、ViewModel 的 UI state、最终渲染界面、Espresso 断言和脱敏后的 Logcat。DAO 查到了两个号码，不等于 UI 把号码放进了正确标签；Espresso 若只断言“两个号码都出现”，绿灯也不证明标签关系正确；Logcat 没泄露原值，只证明隐私检查这一层。记忆系统同样要把保存、检索、请求、回答、评估和日志分层核对。

```text
会话/事件
   ↓
写入与持久化回读 ─→ 检索候选/排序 ─→ 实际回答请求 ─→ 最终回答
                                                     │
                                                     ├─→ 评估器：这条评分规则是否判通过？
                                                     └─→ 日志脱敏：原始敏感值是否仍泄露？

业务正确性、评估有效性、隐私安全是三条相关但不能互相替代的结论。
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：审计一份固定的合成收据，其中保存、检索、回答、评分和脱敏结果都可分别检查。
- 学员会亲自运行：先用教材的 `KeywordRecallEvaluator` 作为发布门；再只把发布门改成“字段—值关系检查”。
- 会得到的产物：控制台 JSON，包含输入指纹、各阶段检查、最早业务断点、关键词评分、隐私检查和 `release_allowed`。
- 结束时要能回答：为什么“评估通过”和“日志无泄露”都不能替代对写入、检索、请求与最终语义的逐层核对。
- 证据边界：银行号码来自教材 fixture，是合成数据；探针没有运行真实记忆系统、LLM 或生产日志链。

**确认问题**：如果自动化报告是绿的、脱敏检查也是绿的，能否直接推出“用户收到的记忆回答在业务语义上正确”？如果不能，至少还缺哪一种直接证据？

## 2. 实验前预测

- 任务与输入：固定使用教材题 `layer1_01_bank_account` 的同一 gold 和同一份交换字段角色后的回答。
- 固定条件：同一 readback、同一检索结果、同一回答请求、同一最终回答、同一脱敏规则、同一教材 fixture。
- 唯一变量：发布门 `D35_GATE=keyword` 或 `D35_GATE=relation`。
- 观察点：`stage_checks`、`first_business_break`、`keyword_evaluation`、`privacy_check`、`release_allowed` 和 `input_fingerprint`。
- 预测问题：只改变发布门后，哪些字段应该保持不变，哪个字段可能改变？这能说明哪一层的问题？
- 学员预测：待回答。

## 3. 运行实验

本课不运行教材的在线/端到端记忆框架。讲师备课只核实了 `metrics.py` 当前实现和 fixture 路径，没有代跑下面的目标探针。

### 步骤 A：准备环境和固定探针

- 输入/读取：教材 gold、示例回答和 `KeywordRecallEvaluator`；银行号码是教材合成 fixture。
- 处理：从示例回答只交换两个号码的字段角色；构造相同的回读、检索和请求收据；同时计算关系正确性、关键词得分和脱敏后的原值泄露检查。
- 输出/用途：本步骤只把固定程序放入 PowerShell 变量，不执行实验；后续两次运行会输出相同结构的分层 JSON。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\user-memory-evaluation'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$d35Probe = @'
import hashlib
import json
import os
import re
from pathlib import Path

from framework import UserMemoryEvaluationFramework
from metrics import KeywordRecallEvaluator, load_gold_facts

test_id = "layer1_01_bank_account"
framework = UserMemoryEvaluationFramework()
case = framework.get_test_case(test_id)
gold = load_gold_facts("fixtures/gold_facts.json")
responses = json.loads(
    Path("fixtures/system_responses.example.json").read_text(encoding="utf-8")
)

expected = {
    "checking_account": "4429853327",
    "routing_number": "123006800",
}
original = responses["json_cards"][test_id]
swap = {
    expected["checking_account"]: expected["routing_number"],
    expected["routing_number"]: expected["checking_account"],
}
answer = re.sub(
    r"4429853327|123006800",
    lambda match: swap[match.group(0)],
    original,
)

readback = dict(expected)
retrieved = dict(readback)
request_memory = dict(retrieved)
checking = re.search(r"checking account number is (\d+)", answer).group(1)
routing = re.search(r"routing number (\d+)", answer).group(1)
answer_roles_correct = (
    checking == expected["checking_account"]
    and routing == expected["routing_number"]
)

keyword = KeywordRecallEvaluator(gold).evaluate(case, answer)
raw_log = json.dumps({"request_memory": request_memory, "answer": answer})
sanitized_log = raw_log
for value in expected.values():
    sanitized_log = sanitized_log.replace(value, "<REDACTED>")
raw_value_leak = any(value in sanitized_log for value in expected.values())

stage_checks = {
    "persistent_readback": readback == expected,
    "retrieval_result": retrieved == expected,
    "answer_request": request_memory == expected,
    "answer_semantics": answer_roles_correct,
}
first_business_break = next(
    (stage for stage, passed in stage_checks.items() if not passed),
    None,
)
gate = os.environ["D35_GATE"]
release_allowed = keyword.passed if gate == "keyword" else answer_roles_correct
fingerprint_source = json.dumps({
    "expected": expected,
    "readback": readback,
    "retrieved": retrieved,
    "request_memory": request_memory,
    "answer": answer,
}, sort_keys=True)

print(json.dumps({
    "probe": "week5_evidence_chain_audit",
    "gate": gate,
    "input_fingerprint": hashlib.sha256(
        fingerprint_source.encode("utf-8")
    ).hexdigest()[:12],
    "stage_checks": stage_checks,
    "first_business_break": first_business_break,
    "keyword_evaluation": {
        "reward": keyword.reward,
        "passed": keyword.passed,
        "reasoning": keyword.reasoning,
    },
    "privacy_check": {
        "raw_value_leak_after_sanitization": raw_value_leak,
    },
    "release_allowed": release_allowed,
}, ensure_ascii=False, indent=2))
'@
```

### 步骤 B：关键词发布门基线

在学员完成预测后再给。命令读取步骤 A 中固定的程序，把发布判定绑定到教材关键词评估结果，然后输出分层收据。重点不是先猜“系统好坏”，而是逐项判断每个绿灯具体证明了什么。

```powershell
$env:D35_GATE = 'keyword'
$d35Probe | python -m uv run --no-sync --extra ch3 python -
```

**真实证据**：待学员运行并贴回完整 JSON；记录控制台是否回到提示符、退出状态和实际输出，不把准备好的代码当已执行。

## 4. 逐段读真实收据

每轮只读一个片段，按最早阶段向后推进：

| 阶段 | 本课证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 持久化回读 | `stage_checks.persistent_readback` | 固定事实在回读对象中是否保持字段关系 | 真实数据库已写入、跨进程可读 | 待回答 |
| 检索结果 | `stage_checks.retrieval_result` | 探针的检索对象是否保持字段关系 | 真实混合检索、时间排序或 Agent 自主查询已运行 | 待回答 |
| 回答请求 | `stage_checks.answer_request` | 探针中的正确记忆是否进入回答输入 | 模型一定采用正确 | 待回答 |
| 最终语义 | `stage_checks.answer_semantics` | 两个号码是否落在正确字段角色 | 其他未检查语义也正确 | 待回答 |
| 评估门 | `keyword_evaluation` | 当前关键词规则怎样评分 | 字段—值关系正确 | 待回答 |
| 隐私门 | `privacy_check` | 脱敏后是否还出现两项已知原值 | 生产日志链无旁路、业务回答正确 | 待回答 |

`first_business_break` 只在这份显式规则和合成收据内定位最早失败层；它不是对真实 Mem0、Memobase 或模型的诊断结果。

## 5. 追到关键源码

**源码锚点**：`vendor/ai-agent-book/chapter3/user-memory-evaluation/metrics.py:30-38,93-124`。

分两小段阅读，总量约 30 行：

1. `30–38`：规范化回答，并用 `variant in normalized_response` 判断事实文本是否出现。
2. `93–124`：取得 gold，把回答与可选记忆拼成待匹配文本，累计命中比例，并用 `recall >= 0.8` 产生 `passed`。

**输入 → 处理 → 输出**：输入是题目 gold 和回答文本；处理是子串出现检查与命中计数；输出是 reward、passed、reasoning 和逐项命中。源码没有字段角色解析，因此不能用它的绿灯替代本课的字段—值关系检查。

**连接到本课收据**：`keyword_evaluation` 来自这段教材代码；`stage_checks` 和 `privacy_check` 是本课离线探针额外构造的独立检查。三者不能互相冒充。

**学员解释**：待回答。

## 6. 单变量对照

- 唯一改变的条件：`D35_GATE: keyword → relation`。
- 保持不变：同一 gold、同一最终回答、同一回读/检索/请求收据、同一脱敏规则和同一探针代码。
- 核对方式：比较两次 `input_fingerprint`；它相同只证明探针参与指纹的输入相同，不证明真实外部系统状态相同。
- 对照前预测：待回答。

```powershell
$env:D35_GATE = 'relation'
$d35Probe | python -m uv run --no-sync --extra ch3 python -
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 待根据两份收据讨论 | 若要外推到真实系统，须采集真实写入/回读、检索、回答请求、答案、评分与生产日志链 |

对照只检验“发布门选择”这个变量；不预设结果，若指纹或其他固定字段变化，先排查实验条件而不是解释门控效果。

## 7. 理解检查：每次只问一题

1. **概念复述**：为什么“评估通过”和“脱敏通过”都不能证明记忆回答的业务语义正确？
2. **结果预测**：若检索阶段只返回账号而漏掉路由号，其他规则不变，最早业务断点应该移到哪里？还需看哪些实际证据？
3. **故障定位**：真实系统答错旧地址，但日志已脱敏且离线评分通过；你会按什么顺序核对持久化回读、检索、回答请求、最终答案和评分规则？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 评估绿灯只对当前评分规则负责；脱敏绿灯只对检查到的泄露路径和值负责；业务语义须由答案关系及其上游证据单独核对。
2. 若持久化回读完整而检索已漏项，第一处可见断点在检索；真实系统仍需核对候选、排序、截断以及实际请求，不能只看探针标签。
3. 从最早可观测边界向后：写入回执之后做持久化回读；核对检索候选/顺序/截断；核对实际回答请求；核对最终答案是否采用；最后审计评分规则为何仍通过。脱敏检查作为并行隐私链处理，不参与替代上述业务定位。

</details>

卡住时换成 Android 的 Room → DAO → ViewModel → UI → Espresso → Logcat 小例子，只补具体混淆点，再重新检查。

## 8. 可选 AI 代码演示

第 3、6 节就是 AI 准备的最小离线演示。学员只需预测、运行和解释；不修改 `vendor/`，不新增依赖或工程。它复用教材评估器和 fixture，但 write/readback/retrieval/request、关系检查与脱敏检查均为本课显式构造，不代表真实框架执行。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下次从哪一小步继续：待开展。
- 状态：待开展（授课后才选 `✅` / `▶` / `🔁`）。

## 10. 讲师备注

- Day 35 是 buffer 日，不引入 Day 36 工具章节内容。
- 实际授课日期、用时、停在哪一步：待开展。
- 实验命令、证据位置与必要真实摘录：待运行。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 本课综合 Day 29–34 的阶段边界；不重复宣称已实跑 Contextual Retrieval、Agentic RAG、完整记忆系统、Mem0、Memobase、模型评判或生产日志链。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`。
- 进度处理：授课结束或中断后在 `docs/progress.md` 末尾追加 Day 35 日志；仅备课不追加日志、不提前标完成。
