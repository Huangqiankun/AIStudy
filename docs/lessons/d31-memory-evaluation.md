# Day 31 — 记忆评估的高分能证明什么

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 记忆能力的评估：三层次框架（第 43–70 行）
> 实验 `vendor/ai-agent-book/chapter3/user-memory-evaluation/`
> 备课日期 2026-11-19 ｜ 授课日期 2026-11-19 ｜ 状态 ✅

本文件按 `_TEMPLATE.md` 建立，为分步教学材料。学员已亲自完成离线评分基线、数字角色交换对照、评分源码剖析和三项理解检查。本课没有运行真实记忆系统的写入、检索或模型回答，也未运行 LLM 裁判。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：记忆评估得到高分，究竟证明了哪一段能力，还需要什么证据才能相信系统真的记住并正确使用了信息？

**类比**：Android 的测试报告取决于断言检查了什么。页面出现订单号、订单号属于当前用户、点击后业务状态正确，是不同的验收事实。对应 Harness 的验证环节：评估器必须检查任务需要的行为，不能只看输出里有没有某段文字。

**最小概念**：记忆评估用固定会话、问题和判定规则检查记忆的实际用途。三层能力分别是基础回忆、跨会话检索与消歧、跨会话综合与主动服务；层级说明任务要求，评分指标说明怎样判分，两者不是一回事。

```text
完整记忆系统评估：会话 → 写入/更新记忆 → 取回记忆 → 回答新问题 → 评分
本课离线实验：教材示例回答 + 关键事实标注 → 关键词评分 → 分层与单题报告
```

**实验全景预告**：

- 任务：检查教材示例回答的评分，判断分数与业务正确性之间的关系。
- 学员运行：离线基线；再固定题目、标注和评分器，只改变一个回答的事实角色对应关系。
- 产物：分层均分、实际样本数 `n`、单题分数，以及单题 `required_info_found`、`reasoning`、`passed`。
- 结束时回答：分数验证了什么；若要定位记忆写入、召回或使用错误，还应补看什么证据。

**确认问题**：如果 UI 测试只检查页面出现某个订单号，它能否同时证明这个订单属于当前用户？理由是什么？

## 2. 实验前预测

- 输入：`fixtures/system_responses.example.json`、`fixtures/gold_facts.json` 和题目 YAML。
- 固定条件：离线 `keyword-recall`，不调用模型，不配置 provider。
- 观察点：每层分数、样本数、单题回答与必需事实。
- 预测问题：标注需要两个事实，回答只包含其中一个，你预计该题怎样得分？理由是什么？
- 学员预测：认为得分约为 0.5，因为只回答了账户号，没有回答银行路由号。随后读取单题报告，实测 `simple_notes=0.500`，与预测一致。

## 3. 运行实验

备课已执行 `python -m uv run --no-sync --extra ch3 python main.py --help`，退出码 0。此命令只核验参数，不执行目标实验。复用当前已安装环境，避免同步整章依赖；本次没有新增依赖。

**基线命令用途**：读取示例回答与标注；按题目所属层级评分并汇总；输出各层均分、样本数和逐题分数，检查表格到底覆盖了哪些题。示例回答是预置输入，表中的系统名称不构成本机真实运行相应系统的证据。

学员完成预测后，才给以下当前步骤命令：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\user-memory-evaluation'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
$d31Report = Join-Path $env:TEMP 'aistudy-d31-baseline.txt'
python -m uv run --no-sync --extra ch3 python main.py --mode compare --metric keyword-recall --output $d31Report
```

报告是实验产物，保存于系统临时目录；不改教材源文件。程序如报错先排查，不将准备好的命令登记为已完成。

**真实证据（2026-11-19，学员亲自运行并贴回）**：控制台显示 `Loaded 60 test cases`，随后生成分层表并打印保存成功；PowerShell 返回提示符，无报错，未单独采集 `$LASTEXITCODE`。

- 输出文件：`$env:TEMP\aistudy-d31-baseline.txt`。用户贴回路径为 `C:\Users\ADMINI~1\AppData\Local\Temp\aistudy-d31-baseline.txt`（聊天转义已还原）；报告未纳入 Git，以下摘录保存跨设备可读证据。
- 四组顺序：`full_context / json_cards / simple_notes / no_memory`。

| 层级 | 每组题数 | full_context | json_cards | simple_notes | no_memory |
|---|---:|---:|---:|---:|---:|
| L1 | 4 | 1.000 | 1.000 | 0.417 | 0.000 |
| L2 | 2 | 1.000 | 1.000 | 0.333 | 0.000 |
| L3 | 2 | 1.000 | 1.000 | 0.125 | 0.000 |
| Overall | 8 | 1.000 | 1.000 | 0.323 | 0.000 |

这些是对教材示例输入实际计算的分数，不能当成本机运行四套真实记忆系统的结果。

## 4. 逐段读真实轨迹

每轮只读一个片段：先看层级和 `n`，再选一题对照原问题、示例回答、gold 标注、单题结果。

| 片段 | 证据 | 学员解释 |
|---|---|---|
| 输入来源 | 已说明预置 fixture；学员用 PowerShell 读取 `simple_notes` 单题回答与 gold | 后续能指出人工答案满分不能证明真实 Agent 找回记忆 |
| 分层报告 | 加载 60 题，每组实际评分 8 题（4/2/2） | 正确判断 Overall 是逐题平均；最初只用 `n=8` 作依据，补充两种算法数值核对与 `_avg` 源码后理解 |
| 单题事实命中 | 账户题回答 `Your checking account number is 4429853327.`；gold 为 `4429853327, 123006800`；四组分数为 `1.000 / 1.000 / 0.500 / 0.000` | 与原预测 0.5 对上；区分回答文本匹配与记忆是否保存 |
| 完整系统证据缺口 | 本课无实际写入/检索/模型请求轨迹 | 能指出需要真实运行记录、记忆更新、检索结果、实际请求和回答；补讲后能用持久化回读定位写入环节 |

读取单题证据的命令（学员已运行）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\user-memory-evaluation'
$d31TestId = 'layer1_01_bank_account'
$d31Responses = Get-Content -Raw -Encoding UTF8 'fixtures/system_responses.example.json' | ConvertFrom-Json
$d31Gold = Get-Content -Raw -Encoding UTF8 'fixtures/gold_facts.json' | ConvertFrom-Json
[pscustomobject]@{
    test_id = $d31TestId
    answer = $d31Responses.simple_notes.$d31TestId
    required_facts = $d31Gold.$d31TestId.required_facts -join ', '
} | Format-List
Select-String -Path (Join-Path $env:TEMP 'aistudy-d31-baseline.txt') -Pattern '^Test Case', '^layer1_01_bank_account'
```

## 5. 追到关键源码

备课核实的锚点：

- `main.py:302–305`：从回答文件加载预置输入。
- `metrics.py:30–38`：规范化文本与候选词匹配。
- `metrics.py:93–94`：选取 gold，并拼接回答和可选记忆文本。
- `metrics.py:105–124`：统计事实命中、计算 reward、产生 passed 和逐项结果。

实际课堂分段展示约 29 行核心源码：`comparison.py` 的 `_avg`（不指定层级时汇总所有实际结果），`metrics.py:30–38` 的规范化与匹配、`105–112` 的命中统计与分数计算、`118–124` 的结果输出。`main.py` 输入加载及 `metrics.py:93–94` 仅备课核实，没有在课堂逐行讲解。

**教师核验提示**：README 的通过阈值说明与当前关键词实现不同，课堂以实际调用路径为准；关键词路径当前是 `passed = recall >= 0.8`。`extracted_memory` 可被评分器拼入待匹配文本，但 compare 调用仅传题目和回答。不要把可选参数的行为误记为本次已发生。

学员能结合 `in` 说明：交换两个数字后，数字文本仍存在，因此评分器依旧会命中；也能说明只增加高分 L1 题导致 Overall 上升，不证明 L2/L3 能力提高。已补充 `passed` 只是 `recall >= 0.8`，`reasoning` 是程序拼接的统计说明，不是额外的语义裁判。

## 6. 单变量对照

- 选用 `layer1_01_bank_account`，原始题目与事实来源见 YAML 第 146–147、213–217 行。
- 基线采用该题 `json_cards` 示例回答；对照只交换回答中 checking account number 与 routing number 对应的两个数值，保持数字集合、题目、gold 和评分器不变。
- 在内存构造修改后的回答并调用教材 `KeywordRecallEvaluator`，不改 `vendor`，不新增脚本文件。学员已亲自执行以下命令；教师未代跑。
- 对照前预测：分数不会降低，因为 `in` 仍会检查到相同数字；学员能引用候选写法任一命中规则说明原因。
- 观察字段：两种回答、`reward`、`passed`、`required_info_found`、`reasoning`。

命令的数据流：加载同题与同一 gold → 读取 A → 仅交换数字生成 B → 用同一个评分器评估 → 输出两份结果。两个回答均未传入额外记忆文本。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\user-memory-evaluation'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
@'
import json
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
original = responses["json_cards"][test_id]
swap = {"4429853327": "123006800", "123006800": "4429853327"}
swapped = re.sub(
    r"4429853327|123006800",
    lambda match: swap[match.group(0)],
    original,
)
evaluator = KeywordRecallEvaluator(gold)
for label, answer in [("A_original", original), ("B_swapped", swapped)]:
    result = evaluator.evaluate(case, answer)
    print(json.dumps({
        "variant": label,
        "answer": answer,
        "reward": result.reward,
        "passed": result.passed,
        "required_info_found": result.required_info_found,
        "reasoning": result.reasoning,
    }, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

**学员贴回的实测摘录**：

- A：`Your checking account number is 4429853327. For the direct deposit setup you'll also want routing number 123006800.`
- B：`Your checking account number is 123006800. For the direct deposit setup you'll also want routing number 4429853327.`
- 两者均为 `reward=1.0`、`passed=true`、`required_info_found={"4429853327":1.0,"123006800":1.0}`、`reasoning="Recalled 2/2 gold facts."`。
- 控制台回到 PowerShell 提示符，无报错；未单独采集进程退出码。结果只打印到控制台，未另存 JSON，本讲义保存必要摘录。

| 实测观察 | 机制解释 | 证据边界与待验证点 |
|---|---|---|
| 角色交换后两份回答仍同为满分且通过 | 规范化子串匹配不核验数字属于哪个字段；通过标记只比较阈值 | 证明当前评分器不能区分这对回答，不证明关键词检查完全无用，也不证明某个真实记忆系统发生了该错误 |
| 示例回答的四组分数有差异 | 标注事实在预置回答中的覆盖不同 | 无完整系统执行链，不能归因于真实记忆格式的优劣；LLM 裁判的表现亦未验证 |

## 7. 理解检查：每次只问一题

1. **概念复述**：看到 `json_cards` 三层满分，能否推断它具备可靠的跨会话记忆和主动服务能力？实验实际证明了什么？
2. **结果预测**：正确答案用了标注未覆盖的同义表达，关键词分数可能怎样变化？为什么？
3. **故障定位**：新会话答出了旧地址，仅凭低分能否定位写入失败？你会按什么顺序核对证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 能力层级描述任务需求；评分规则决定哪些输出特征获得分数。离线示例打分不能证明现场跑过记忆系统。
2. 未覆盖的表达可能漏匹配；需检查具体 gold 变体与规范化规则，不将低分直接等同语义错误。
3. 核对新地址源证据、写入/更新产物、召回结果、实际回答请求及回答；按缺失最早发生的位置提出验证。模型裁判也需校准，不自动等于真值。

</details>

## 8. 可选 AI 代码演示

已使用 AI 准备的内存对照，学员负责预测并亲自运行、解释结果；未要求学员修改 Python，未新增依赖或工程。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：关键词覆盖不等于关系正确；预置回答评分不等于完整系统执行；汇总分需固定样本集合；能沿写入、召回、请求、回答逐段定位。
- 已补齐的卡点：最初仅凭 `n=8` 判断平均方式，已用数值与源码补证；曾误以为英文标注 `passport` 能匹配中文“护照”并得到 1 分，解释规范化不翻译后，正确回答将标注扩展为 `["passport", "护照"]` 才命中，变化来自评分标准；定位时最初遗漏持久化回读，补讲后能优先排查写入/更新。
- 当前未完成的本课必修项：无。完整记忆系统与模型裁判未实跑，是本课实验范围限制，不能在记录中声称已验证。
- 下次：按路线进入 Day 32，开课前核实进度与对应教材。
- 状态：✅。

## 10. 讲师备注

- 2026-11-19 冷启动：工作区干净，`git pull --rebase` 已同步；最新日志为 Day 30 ✅，无待续课安排。
- 已读教材 `memory-system` skill、对应章节和实验 README；已核验评分源码与 CLI。
- 备课未运行基线或对照；正式实验由学员预测后亲自运行。真实证据来源为本次贴回输出，已保留报告路径与必要摘录；没有把 README 示例表当成本次运行证据。
- 原始预测：缺一个事实约 0.5；交换两串数字分数不降。两项均经实际输出核对。
- 概念检查通过：先指出关键词达标不能证明语义正确；补充执行范围后，在人工编写 8 条答案的换例中指出还需实际运行记录、记忆更新、请求与回答。
- 预测检查通过（补讲后）：初答中文“护照”对英文 `passport` 标注得 1 分；用子串匹配解释后，换成中英两种可接受形式，答对该项得 1 分，且是评分标准变化。
- 定位检查通过（补讲后）：最初关注源对话、检索结果及模型请求桥接；补入持久化回读后，在“写入工具收到上海、回读仍只有北京”的示意中，正确优先检查持久化记录及新旧地址处理。该场景未实际运行，不作为系统故障证据。
- 实际授课日期 2026-11-19；未计时，不估算用时。实验、源码与三项检查已完成。
- Day 31 只讨论记忆评估；本周后续细节暂不扩展。
- 课程结束追加 Day 31 ✅ 日志；无待处理续课安排。不修改旧日志。
