# Day 32 — 日志脱敏后，怎样确认敏感值消失而排障证据仍在

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 隐私保护：日志脱敏（第 231–239 行）
> 实验 `vendor/ai-agent-book/chapter3/log-sanitization/`
> 备课日期 2026-11-19 ｜ 授课日期 2026-11-19 ｜ 状态 ✅

本文件按 `_TEMPLATE.md` 建立，分步使用。学员已亲自完成离线规则基线、身份证诊断探针、字段名单变量对照、关键源码剖析与三项理解检查。未运行 LLM 引擎，也未接入实际生产日志链。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：日志经过脱敏函数后，怎样确认敏感值消失而排障证据仍在？

**类比**：Android 的 OkHttp Interceptor 可以在日志输出前处理请求文本。希望保留请求关联信息、错误码与执行阶段，同时替换口令；实际效果取决于识别规则、替换逻辑，以及最后写出去的是哪个变量。

**最小概念**：脱敏是识别敏感片段并用占位符替换。漏检会留下原值，过度删除会损失排障信息。命中数量不能独立证明整份日志安全。

```text
原日志 → 规则匹配与校验 → 选定片段 → 重建脱敏文本 → 输出/落盘
                           └→ findings（类别、位置、原值等）
```

**实验全景预告**：

- 任务：检查教材人工样例中哪些片段被替换、哪些上下文被保留。
- 学员运行：先运行离线规则演示，再对同一条人工日志仅改变口令字段名。
- 产物：控制台 before/after、类别汇总；对照打印原文、脱敏文本、命中项数和类别。
- 结束时回答：规则覆盖、实际替换、最终输出分别需要哪些证据。

**确认问题**：像网络日志里的 `status=401` 和登录口令，为什么不宜简单地把整条日志全部删掉？

## 2. 实验前预测

- 输入：教材 `samples.py` 的人工样例；不使用真实凭据或个人数据。
- 固定条件：`regex` 引擎，不调用模型、不使用 provider、不新增依赖。
- 观察点：原文、替换后的文本、命中类别、排障字段。
- 预测问题：日志同时包含口令和错误码，你希望脱敏后的文本留下什么、替换什么？
- 学员最初把口令原值和状态码都视为应保留的排障信息；补讲后明确保留请求 ID、状态码，替换口令原值。
- 实际运行前另问“命中 0 处能否认定没有敏感信息”：学员预测不能，可能是规则处理有问题或敏感内容超出规则范围。

## 3. 运行实验

备课已运行 `python -m uv run --no-sync --extra ch3 python main.py --help`，退出码 0。`--no-sync` 复用已安装环境。讲师没有代跑目标演示或对照，下列正式实验均由学员运行。

**命令用途**：读取内置人工样例 → 对每条调用规则引擎 → 打印原文与脱敏文本 → 汇总类别与命中数。用来核对替换位置与剩余上下文；演示会打印人工原值，不是生产日志输出模板。

完成预测后，才给学员当前步骤的命令：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\log-sanitization'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --no-sync --extra ch3 python main.py --demo --mode regex
```

**真实证据（2026-11-19）**：学员贴回完整控制台输出，5 个样本合计命中 16 处、覆盖 13 类，返回 PowerShell 提示符，无报错；未单独采集退出码。

- 原始附件：`C:\Users\Administrator\.codex\attachments\692fd7c7-3de2-4aac-9fbb-71f511cc9ac6\已粘贴的文本.txt`。附件不是 Git 资产，以下保存跨设备可读摘录。
- 五组样本及命中数：HTTP 请求/响应 4、客服对话 3、数据库连接报错 4、CI/Git 日志 3、配置转储 2。
- 类别：口令赋值和邮箱各 2 处、手机号 2 处，API Key、信用卡、SSN、连接串凭据、AWS Key、IP、GitHub 令牌、Slack 令牌、JWT、私钥各 1 处。
- 本模式打印控制台，没有生成报告文件；`--output` 仅用于 `--input` 路径。示例打印 BEFORE 和含原值的命中明细，不能把整份控制台输出当作脱敏后的安全日志。

## 4. 逐段读真实轨迹

一次选一条样例，分别核对原文敏感片段、after 中占位符、剩余错误状态与调用上下文。命中摘要和脱敏文本是不同输出。

| 证据位置 | 实测观察 | 学员解释 |
|---|---|---|
| 客服 before/after | 手机号已替换；人工身份证字符串 `11010119900307721X` 保留，未计入 3 处命中 | 学员先查识别规则，再查后续排除条件；能判断未匹配时不会执行校验 |
| 类别与数量 | 5 样本、16 处、13 类 | 运行前已能说明零命中不等于没有敏感信息；课堂未把总命中数当作完整覆盖证明 |
| 输出去向 | 演示打印原文、脱敏文本及含原值的命中明细 | 后续预测题能说明完整 findings 会重新暴露原值；实际落盘泄露仅为定位题假设 |

### 身份证未替换的诊断（学员实跑）

先读正则和校验门，再检查完整对话匹配、孤立号码匹配及独立校验。讲师曾提出词边界可能阻断的假设；本次原始样例的匹配结果排除了该假设。

初版以中文名称筛选样例的 `next(...)` 在第 5 行抛出 `StopIteration`，尚未进入规则探针。源码名称确实包含“客服对话”；PowerShell 管道中文编码是解释假设，未独立验证。随后只将取样方式改为按同一人工号码定位，成功运行。不要把这次脚本取样失败记成脱敏引擎错误。

成功运行的命令（不修改教材）：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\log-sanitization'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
@'
import json
from samples import SAMPLES
from regex_sanitizer import _RULES

value = "11010119900307721X"
text = next(text for name, text in SAMPLES if value in text)
rule = next(rule for rule in _RULES if rule[0] == "cn_id_card")
_, _, pattern, _, validator = rule

print(json.dumps({
    "match_in_conversation": any(
        match.group(0) == value for match in pattern.finditer(text)
    ),
    "match_when_isolated": pattern.fullmatch(value) is not None,
    "checksum_ok_independent": validator(value),
}, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

学员贴回：`match_in_conversation=true`、`match_when_isolated=true`、`checksum_ok_independent=false`。返回 PowerShell 提示符，未单独采集退出码。

**证据解释**：号码可匹配，但校验不通过；结合源码 `if validator and not validator(value): continue`，解释该候选为何不进入替换。探针主动独立调用 validator，字段本身不是原始运行的调用追踪。

**教学取舍**：学员最初主张保留校验失败的身份证原值用于排障。补讲“输入不合法不等于无敏感性”，再换“手机号少一位”的例子后，能说明保留错误信息和原因、隐藏手机号。课堂提出的日志字段设计属于示意，没有实施新的脱敏策略或修改教材。

## 5. 追到关键源码

备课核实的源码锚点：

- `regex_sanitizer.py:118–125`：口令赋值规则，键名、分隔符、值和捕获组。
- `regex_sanitizer.py:195–207`：匹配、选取捕获组、可选校验。
- `regex_sanitizer.py:225–239`：按位置重建文本并返回 findings。

课堂实际分段展示约 25 行核心内容：身份证规则 `147–149`，匹配循环和校验门的省略摘录 `195–208`，重建文本 `226–233`，口令赋值正则 `120–122`，返回明细与文本 `235–239`。没有逐行通读整个函数。

- 学员正确推演：没有正则匹配就不会进入校验；替换口令后 `parts.append(text[last:])` 将末尾 `status=401` 拼回。
- 口令正则按列举字段名、分隔符、捕获值进行识别，解释第 6 节字段改名结果。

辅助定位：`main.py` 的 `demo_regex_mode` 打印 before/after；`sanitize_file` 将 `redacted` 写入输出文件。重叠候选优先级位于 `regex_sanitizer.py:217–223`，需要解释重叠时再展开。

课堂已讲解：`findings` 含原始 `value`，因此脱敏文本与整份返回对象需要分开审查；函数返回不等于整个日志链已经安全。将 findings 写入文件造成泄露属于理解检查的预测，没有另外执行生产日志写入实验。

## 6. 单变量对照

- 人工输入：`request_id=req-32 password=DemoOnly42 status=401`。
- 唯一变化：字段名 `password` 改为 `login_hint`，业务上约定其值仍是口令。
- 不变：敏感值、请求 ID、状态、引擎和其他文本。
- 预测问题：只改字段名，同一段口令还能否被当前规则识别？说明依据。
- 学员预测：如果直接按值识别，两条都可能替换；如果依赖 `password` 字段且未覆盖 `login_hint` 或该值，则 B 不会替换。随后实测与后一个条件分支一致。

命令数据流：构造 A → 只改字段名生成 B → 同一函数分别处理 → 打印原文、脱敏文本、数量与类别；不修改教材、不写新脚本。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\log-sanitization'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
@'
import json
from regex_sanitizer import sanitize

original = "request_id=req-32 password=DemoOnly42 status=401"
renamed = original.replace("password=", "login_hint=", 1)
for variant, text in [("A_password", original), ("B_login_hint", renamed)]:
    redacted, findings = sanitize(text)
    print(json.dumps({
        "variant": variant,
        "original": text,
        "redacted": redacted,
        "hit_count": len(findings),
        "categories": [item["category"] for item in findings],
    }, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

| 实测观察 | 结合源码的机制解释 | 证据边界 |
|---|---|---|
| A 的 `redacted` 为 `request_id=req-32 password=[REDACTED_SECRET] status=401`；`hit_count=1`、`categories=["secret_assignment"]` | 口令赋值规则覆盖 password，捕获并替换后面的值 | 只验证这条人工输入，不说明任何 password 格式都被覆盖 |
| B 的 `redacted` 为 `request_id=req-32 login_hint=DemoOnly42 status=401`；`hit_count=0`、`categories=[]` | 该字段名不在此规则中，此值也未被其他规则识别 | 不推出所有陌生字段都漏检；其他规则可能直接识别值的格式 |

学员贴回两组完整 JSON，控制台回到 PowerShell 提示符，无报错；未单独采集退出码。结果未另存文件，本节记录必要真实摘录。

## 7. 理解检查：每次只问一题

1. 概念（实际提问）：为什么“业务校验不通过”和“可以原样写入日志”不能画等号？
2. 预测：已替换文本没有原口令，但调用方又打印了完整 findings，会怎样？
3. 定位：函数返回文本已替换，落盘文件却仍有原值，下一步先查哪里、用什么证据验证？

<details><summary>讲师核对要点（回答后再展开）</summary>

1. 校验失败的输入仍可能包含敏感信息；保留排障原因不等于保留原值。
2. findings 中的 value 可重新暴露原值；必须核对调用方实际输出了哪些字段。
3. 追查写入参数是否为原文、其他日志出口与文件时间/路径；用回读确认，不能只看成功提示。不要直接归因模型。

</details>

**实际验收记录**：

- 概念通过：学员说明校验不通过时被校验的内容仍可能敏感，不能原样写入日志；原话“检验地址”经课堂澄清按“输入值”理解。
- 预测通过：学员指出完整 findings 含敏感值，会重新泄露；讲师补充替换函数本身仍成功，失败在整条输出链。
- 定位通过：学员提出检查写日志是否绕过脱敏逻辑、是否在脱敏前写入。讲师明确检查每个日志出口的实际参数（原文、redacted、findings）及写入顺序。本题为假设故障分析，未声称实际文件发生泄露。

## 8. 可选 AI 代码演示

第 6 节提供最小内存对照，直接调用教材函数。学员负责预测和解释，无手写作业。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：识别、校验、候选替换与日志输出边界；能解释字段改名对照、推演末尾文本保留并定位日志旁路。
- 曾有卡点：把口令原值看作必要排障信息；认为校验失败的身份证应保留。经手机号换例和概念检查已补齐。
- 尚未验证：LLM 脱敏表现、真实生产日志链、中文筛选失败的编码原因；这些不作为本课已完成证据。
- 下一步：Day 33，按进度路线进入后续课程；本课无待补验收项。
- 状态：✅。

## 10. 讲师备注

- 实际授课日期 2026-11-19，用时未计；完成基线、诊断、单变量对照、源码和三项检查。讲师未代跑目标实验。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`。
- 教材小模型效果与召回率描述不作为本课实测；本课离线规则实验不能证明模型脱敏表现。
- LLM 路径存在远端回退实现，本课不启用该路径。后续如扩展，需另核实真实数据流。
- `mem0`、`memobase` 按路线仅阅读，不运行。
- 本次在进度日志末尾追加第 32 课完成记录，保留历史行；无待续课安排需更新。
