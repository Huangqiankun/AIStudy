# Day 24 — 同一段原始文本，为什么要先发现字段，再按字段抽取？

> 章节 `vendor/ai-agent-book/book/chapter3.md`「从结构化数据中提取隐性知识」
> 实验 `vendor/ai-agent-book/chapter3/structured-knowledge-extraction/`
> 备课日期 2026-11-10 ｜ 授课日期 2026-11-10 ｜ 状态 ✅

本文件是分步教学材料，不一次性贴入对话。45 分钟为参考，可跨会话；没有实际运行和理解检查时，不填完成状态。

## 1. 建立概念：是什么，为什么需要

**本课只回答**：同一段原始文本，为什么要先发现字段，再按字段抽取？

**类比**：Android/HarmonyOS 中，服务端返回一大段自由 JSON，直接让每个页面自己读，像是“看懂文本后临场发挥”；先从大量真实响应归纳出 `data class` / ArkTS interface，再把每条响应解析到同一组字段，才便于排序、统计和比较。但这里的 schema 不是编译器或法律真理：它由模型从样本归纳，可能漏字段、合并错同义项；抽取器也可能把“未提及”误写成 `false`，或把暗示当成事实。

**最小概念**：

- 字段发现回答“哪些因素值得在所有案例中反复比较”，产物是 `core + extensions` 的模块化 schema。
- 结构化抽取回答“这一个案例在这些既定字段上的值是什么”，产物是字段稳定的 JSON；文本没说的应为 `null`，而不是自动猜成 `false`。
- 只有统一字段后，后续的向量化、聚类和统计比较才有共同坐标；但 JSON 结构正确不等于每个值都有原文证据。

```text
多条原始案例文本
       │
       ├─ ① 分批自由发现候选因素
       │          ↓
       │   归并、去重、规范化
       │          ↓
       │  schema = core + 各罪名 extensions
       │          │
       └──────────┴─ ② 每条案例按适用字段抽取
                              ↓
                    统一 JSON 记录（值 / null）
                              ↓
                    后续比较、聚类、原型归纳
```

**实验全景预告**（只说明路线，不揭示结果）：

- 任务：沿着教材一次已保存的真实 CAIL2018 运行，核对 schema 是怎样进入抽取请求、单条案情怎样变成字段 JSON；再在本机用同一模型、同一案情，只改变允许抽取的字段集合。
- 会运行：先用 PowerShell 读取冻结的 schema、请求与响应收据；随后通过 Ollama 的 OpenAI 兼容接口，让 `qwen3:8b` 分别按 `core` 与 `core + 盗窃罪扩展` 抽取同一文本。
- 会观察：请求中的字段键、响应中的非空字段、`null`、罪名扩展字段，以及本地收据文件；不会把模型可见的 reasoning 当作内部思维证明。
- 最终能回答：字段发现与逐条抽取各自改变了什么；schema 变窄时丢的是表示能力、模型行为还是两者；怎样回原文判断某个结构化值是否有证据。

**确认问题**：假设 100 条工单都保存成自由文本，而且模型每次都能读懂单条工单，为什么我们仍可能需要先归纳统一字段，再逐条抽取？

## 2. 实验前预测

- 输入：教材冻结运行中的一条盗窃案事实文本、由训练样本自下而上发现的 schema、本机 `qwen3:8b`。
- 固定条件：同一案情、同一模型、`temperature=0`、同一 JSON 输出要求、同一核心字段。
- 唯一改变：允许抽取的字段集合是 `core`，还是 `core + 盗窃罪 extensions`。
- 观察点：两次请求的 `factor_keys`；两次响应的键、值与 `null`；原文中能否逐字或通过明确语义找到每个非空值的证据。
- 预测问题：加入罪名扩展字段后，输出会发生什么变化？哪些变化是“schema 允许表达更多”，哪些仍必须等实跑和回原文才能判断？
- 学员初始预测：外层 JSON 结构大致不变；加入扩展 schema 后请求会多出盗窃专用字段，具体返回哪些值必须运行后才知道。经提示后进一步区分：请求字段集合由 Harness 确定，模型是否返回、遗漏或越界仍需实测。（2026-11-10，运行前）

## 3. 运行实验

教材标准 `python -m uv run --extra ch3 ...` 在本机备课核验时因 `annoy==1.17.3` 缺少 MSVC 14+ 而在依赖构建阶段失败，尚未进入实验逻辑。今天用 `--no-project --with openai` 建立隔离的最小环境，读取教材已有数据并调用本地 Ollama；不修改 `vendor/`。本机已核实 Ollama 0.17.6、`qwen3:8b` 已安装，Python 3.12.9 与 `openai 1.109.1` 可导入；讲师未在学员预测前代跑目标抽取。

### 步骤 A：进入目录并固定编码

输入/处理/输出：只切换到实验目录，并设置本地请求绕过代理与 UTF-8；不调用模型、不产生实验结论。

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\structured-knowledge-extraction'
$env:NO_PROXY = 'localhost,127.0.0.1,::1'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
```

### 步骤 B：核对冻结的真实证据链

收到步骤 A 的输出后再给。先解释：

1. 读取历史运行的 `evidence.json`、schema 合并 checkpoint 和一条抽取 checkpoint。
2. 从 evidence 取 schema 统计，从 checkpoint 分别取实际请求和模型解析结果。
3. 只显示请求是否含 schema/案例、字段数量与单案抽取值，不打印整份大文件。
4. 用这些字段回答“历史运行记录了哪些阶段”；它不能替代本机新运行，也不能自动证明每个抽取值正确。

```powershell
$evidence = Get-Content '.\validation\runs\20260730T044309Z-3_12-3ab8ec7b\evidence.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$schemaCp = Get-Content '.\validation\checkpoints\official-seed37\schema-consolidation.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$extractCp = Get-Content '.\validation\checkpoints\official-seed37\extraction-case-cail2018-47583d42744d74fd.json' -Raw -Encoding UTF8 | ConvertFrom-Json
$case = $extractCp.parsed.cases[0]

[pscustomobject]@{
    RunStatus       = $evidence.status
    CoreFactors     = @($schemaCp.parsed.core).Count
    TheftExtensions = @($schemaCp.parsed.extensions.'盗窃罪').Count
    RequestHasSchema = ($extractCp.receipt.request.messages[-1].content -match 'DISCOVERED SCHEMA')
    RequestHasCase   = ($extractCp.receipt.request.messages[-1].content -match $case.id)
    CaseId           = $case.id
    Charge           = $case.charge
    FactorCount      = @($case.factors.PSObject.Properties).Count
} | Format-List

$case.factors.PSObject.Properties |
    Sort-Object Name |
    Select-Object Name, Value |
    Format-Table -AutoSize
```

**准备观察**：`status` / schema 数量只能证明收据记录的范围；`RequestHasSchema` / `RequestHasCase` 核对请求构造；单案字段表是模型响应经 checkpoint 保存的解析结果。非空值还要回原文核对，`false` 与 `null` 不能混为一谈。

**真实证据**（学员运行）：历史收据显示 `RunStatus=passed`、`CoreFactors=16`、`TheftExtensions=12`、`FactorCount=28`，且 `RequestHasSchema=True`、`RequestHasCase=True`。逐字段回原文时发现历史结果中的 `theft_tool=徒手` 没有原文明确信息，属于模型推断；因此 campaign 通过不能替代字段级核验。首次命令使用 Windows PowerShell 5.1 不支持的 `ConvertFrom-Json -Depth` 而失败，未读取任何收据；去掉该参数后重跑成功，讲义已同步修正。

### 步骤 C：本机运行 core 基线

收到步骤 B 的输出、完成预测后再给。命令读取冻结 schema 与同一条官方样本，选择 `core` 字段，向本机 Ollama 发一次 JSON 抽取请求，并把请求字段、原文、响应、usage 保存为 `lesson-d24-core.json`。

```powershell
@'
import json
from pathlib import Path
from openai import OpenAI

root = Path('.')
run = json.loads((root / 'validation/runs/20260730T044309Z-3_12-3ab8ec7b/evidence.json').read_text(encoding='utf-8'))
schema = run['discovered_schema']
case_id = 'cail2018-47583d42744d74fd'
rows = [json.loads(line) for line in (root / 'data/official/cail2018_sample_seed37.jsonl').read_text(encoding='utf-8').splitlines() if line.strip()]
case = next(row for row in rows if row['id'] == case_id)
factors = schema['core']
contract = [{'key': f['key'], 'kind': f['kind'], 'values': f.get('values', [])} for f in factors]
messages = [
    {'role': 'system', 'content': 'Extract one JSON object strictly from the given schema and case. Information not stated in the case must be null; do not infer. Output JSON only.'},
    {'role': 'user', 'content': 'SCHEMA:\n' + json.dumps(contract, ensure_ascii=False) + '\n\nCASE:\n' + case['fact']},
]
client = OpenAI(api_key='ollama', base_url='http://127.0.0.1:11434/v1')
resp = client.chat.completions.create(model='qwen3:8b', temperature=0, response_format={'type': 'json_object'}, messages=messages)
parsed = json.loads(resp.choices[0].message.content)
receipt = {'mode': 'core', 'case_id': case_id, 'factor_keys': [f['key'] for f in factors], 'fact': case['fact'], 'response': parsed, 'usage': resp.usage.model_dump() if resp.usage else None}
(root / 'lesson-d24-core.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'mode': 'core', 'factor_count': len(factors), 'response': parsed, 'output': 'lesson-d24-core.json'}, ensure_ascii=False, indent=2))
'@ | python -m uv run --with openai==1.109.1 --python 3.12 --no-project python -
```

**基线真实输出**（学员运行）：请求 `factor_count=16`，原始模型响应只返回 9 个键，生成 `lesson-d24-core.json`（1744 bytes）。响应是合法 JSON，但没有按约束把另外 7 个字段显式写成 `null`；`recidivism=true` 也缺少足以认定累犯的原文证据。收据记录 `prompt_tokens=1569`、`completion_tokens=86`、`total_tokens=1655`；这是 Ollama/OpenAI 兼容接口返回的本次 usage，不外推为供应商计费。

### 步骤 D：单变量对照 core + 盗窃罪 extensions

先让学员预测。与步骤 C 相比，只把 `factors = schema['core']` 改为 `factors = schema['core'] + schema['extensions']['盗窃罪']`，输出改名为 `lesson-d24-full.json`；其余模型、案情、温度、提示和 JSON 要求保持相同。课堂给出完整可复制命令，不要求学员手改 Python。实际命令另外计算 `missing_keys`、`extra_keys`、非空扩展字段和两次运行间变化的 core 字段，并连同原始响应保存到收据。

运行后比较：

- 两份收据的 `factor_keys` 是否只差盗窃罪扩展字段。
- 响应是否只使用允许的键，扩展字段里哪些非空、哪些 `null`。
- 同一事实是否被重复表达在通用字段和罪名字段中；这说明 schema 可能有冗余，不等于两个值都正确。
- 回原文逐项标注 `明确支持 / 明确否定 / 未提及 / 需要推断`，不拿 JSON 外形替代证据核验。

**对照真实输出**（学员运行）：请求字段 28 个，原始响应返回 10 个键、缺 18 个、无额外键，生成 `lesson-d24-full.json`（2139 bytes）。非空扩展字段为 `stolen_property_recovery=部分追回`、`theft_method=顺手牵羊`、`theft_target=食品`、`use_special_convenience=无`。基线存在的 `first_offense`、`recidivism`、`victim_count` 在对照响应中被省略。收据 usage 为 prompt 4568、completion 93、total 4661 tokens。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 请求字段 16→28；响应键 9→10；对照缺 18 个键并返回 4 个非空扩展字段 | 扩展 schema 提供了专用表示槽位，也可能增加本地模型的抽取负担 | 单个案例、每种条件一次不足以断言扩展 schema 导致更多遗漏；需重复配对、交换顺序并换案例 |

## 4. 逐段读真实轨迹

课堂只读一小段，依次区分：

| 阶段 | 要核对的证据 | 能证明什么 | 不能直接证明什么 |
|---|---|---|---|
| 字段发现 | discovery checkpoint 的请求、响应 | 模型曾从一批训练文本提出候选因子 | 候选完整、正确或适用于所有数据 |
| schema 归并 | `schema-consolidation.json` 的 `parsed` | 候选被归并为 `core + extensions` | 同义项一定合并正确 |
| 抽取请求 | extraction receipt 的 messages | schema 与案例文本实际进入该次请求 | 模型正确使用了每条信息 |
| 抽取响应 | response / `parsed.cases[].factors` | 模型返回了字段 JSON | 每个值有原文证据或业务上正确 |
| 下游使用 | `normalize_extractions`、向量化、聚类输入 | 抽取值被标准化后参与后续计算 | 最终原型或建议正确 |

本课不把 `acceptance.passed=true`、模型 reasoning 文本或最终建议评分当作单个字段正确性的证明。

## 5. 追到关键源码

课堂分小段阅读，总量控制在 20–40 行：

- `discovery.py:64-94`：`discover_schema()`；输入案例批次，先累积 `raw_factors`，再归并成 schema 并落盘。
- `discovery.py:108-119`：`factors_for_charge()`；输入 schema 和罪名，输出去重后的 `core + 当前罪名扩展`。
- `extractor.py:39-83`：`extract_one()` 的关键请求构造；已知罪名时跳过罪名判断，按适用因子生成抽取契约。
- `extractor.py:85-106`：`_normalize()`；输出必须覆盖 schema 中每个字段，缺失写 `None`，并按 numeric/bool/categorical 归一化。
- 官方 campaign 的同类证据链：`campaign.py:194-265` 发现与抽取请求，`campaign.py:323-340` 按 schema 标准化抽取结果。

学员用“输入 → 处理 → 输出”解释。重点检查 `null` 是在哪里产生、字段集合由谁决定，以及原始模型响应和标准化结果是否是同一层证据。

**课堂源码结论**：`factors_for_charge()` 只拼接并去重字段定义，不读取案情或模型响应；`response_format=json_object` 只保证 JSON 外壳，不保证键完整、类型和值正确。`_normalize()` 会把省略键、显式 `null` 和空字符串都合并成 `None`，因此只看规范化结果无法审计模型是否显式返回了键；它还会把布尔字段的未知字符串静默压成 `False`。学员运行最小探针，实测原始字符串 `"未知"`（`str`）被转换为 `false`（`bool`）。

## 6. 单变量对照

- 唯一变化：字段集合从 `core` 扩为 `core + 盗窃罪 extensions`。
- 固定：同一事实文本、模型、温度、端点、系统约束、JSON 输出格式。
- 预期讨论而非预设结果：扩展 schema 提供了表达罪名特有因素的“槽位”，但槽位出现、值非空和值正确是三件不同的事；本地小模型两次调用也可能有非确定性。
- 证据边界：这是一次本地模型对照，不能评价教材历史运行所用模型，也不能用一次结果证明自下而上 schema 必然优于人工 schema。
- 学员最终解释：一次配对只能记录当前差异；要支持“扩展 schema 增加遗漏”的假设，需在同模型、同案情的多次配对中比较两边遗漏数，而不是只看扩展条件是否缺字段。跨模型结论还需另做模型对照。

## 7. 理解检查：每次只问一题

1. **概念复述**：字段发现和结构化抽取分别消费什么输入、产生什么输出？为什么不能合并成“模型读懂了案例”？
2. **结果预测**：若只删去 `盗窃罪 extensions`，哪些输出变化来自表示空间变窄，哪些必须实跑后才能知道？
3. **故障定位**：某条抽取把原文未提及的“累犯”写成 `false`，你会依次检查请求、原始响应、标准化代码和原文中的什么证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 发现阶段消费多条训练案例、产出 schema；抽取阶段消费 schema + 单条案例、产出统一字段记录。前者定义比较坐标，后者填值。
2. schema 删除扩展字段后，这些键不再是允许输出的字段属于表示变化；模型是否还会越界输出、核心字段值是否变化、具体非空字段是什么必须看实跑。
3. 先确认该字段在请求 schema 中、原文是否明确支持/否定/未提及，再看原始模型响应是否已给错；若原始是 `"未知"` 而标准化变成 `false`，定位当前 bool normalization；若原始与规范化都是 `null`、下游却变成 `false`，继续定位下游或收据是否错配；若请求没带原文或字段，则定位请求构造。

</details>

卡住时换成客服退款工单例子复查：`是否已退款=false` 与“工单没写是否退款”不是同一事实。

**课堂实际检查**（2026-11-10）：

1. 概念：能说明字段发现消费多条原始案例并产出 schema，抽取消费 schema + 单案并产出字段 JSON；经提示后不再把 schema 字段定义误解为案例值。
2. 预测：能区分请求字段集合由 Harness 确定，响应键、值、遗漏、越界和稳定性必须实测；经缩小问题确认删除扩展字段也不能保证模型绝不越界输出。
3. 定位：能按请求、原始响应、`_normalize()`、下游结果逐层检查；经 `null → false` 换例复查，确认若标准化输入输出仍为 `None`，应继续定位下游而不能直接归因 normalization。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell 中的 Python 是讲师提供的最小实验胶水，学员负责预测、运行和解释，不要求改写。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：能区分字段发现、模型原始抽取、确定性规范化和下游使用；能把合法 JSON、字段契约完整和值有原文证据分成三层检查。
- 尚未理解及证据：无阻塞缺口。本课未做多次重复或多模型对照，不能判断扩展 schema 对遗漏率的稳定因果影响。
- 下次从哪一小步继续：Day 25 稠密与稀疏向量，先区分精确词命中与语义相近召回。
- 状态：✅

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-11-10；完成历史收据核对、本地基线/对照、源码剖析、规范化探针和三项理解检查。
- 实验命令、模型、证据位置与必要真实摘录：本地 `qwen3:8b`，`temperature=0`；`lesson-d24-core.json` 与 `lesson-d24-full.json` 位于被忽略的实验目录。基线请求 16/返回 9，对照请求 28/返回 10；源码探针 `"未知" → false`。
- 学员的原始预测与后续解释：预测扩展 schema 会增加专用字段而值需实跑；曾把合法 JSON 判为契约通过、把字段定义当案例值，并只用“不同模型可能不同”解释因果边界，经请求/响应/标准化分层与配对复测提示后修正。
- 三项理解检查：概念 通过 / 预测 通过 / 定位 通过（均含缩小问题或换例复查）
- 是否采用代码演示及原因：采用教材冻结收据读取 + 本地 Ollama 最小调用；避免整章依赖和外部 API 成本干扰核心变量
- 本次观察、假设、待验证点：历史 passed 收据仍含无原文支持的 `theft_tool=徒手`；本机两次原始响应都遗漏契约字段，对照多出 4 个非空扩展字段但遗漏更多。假设扩展 schema 增加小模型负担，待通过重复配对、换案例和必要的模型对照验证；一次结果不作因果结论。
- 对应备课 skill：`vendor/ai-agent-book/skills/knowledge-org/SKILL.md`
- 进度处理：已追加 Day 24 `✅` 日志；下一课 Day 25。
