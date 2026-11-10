# Day 24 — 同一段原始文本，为什么要先发现字段，再按字段抽取？

> 章节 `vendor/ai-agent-book/book/chapter3.md`「从结构化数据中提取隐性知识」
> 实验 `vendor/ai-agent-book/chapter3/structured-knowledge-extraction/`
> 备课日期 2026-11-10 ｜ 授课日期待开展 ｜ 状态待开展

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
- 学员初始预测：待回答。

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
    {'role': 'system', 'content': '严格按给定 schema 从案情抽取 JSON。未提及必须为 null，不得推断；只输出 JSON。'},
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

### 步骤 D：单变量对照 core + 盗窃罪 extensions

先让学员预测。与步骤 C 相比，只把 `factors = schema['core']` 改为 `factors = schema['core'] + schema['extensions']['盗窃罪']`，输出改名为 `lesson-d24-full.json`；其余模型、案情、温度、提示和 JSON 要求保持相同。课堂给出完整可复制命令，不要求学员手改 Python。

运行后比较：

- 两份收据的 `factor_keys` 是否只差盗窃罪扩展字段。
- 响应是否只使用允许的键，扩展字段里哪些非空、哪些 `null`。
- 同一事实是否被重复表达在通用字段和罪名字段中；这说明 schema 可能有冗余，不等于两个值都正确。
- 回原文逐项标注 `明确支持 / 明确否定 / 未提及 / 需要推断`，不拿 JSON 外形替代证据核验。

对照真实输出：待学员运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待填写 | 待填写 | 可换另一条案情或重复运行，检查模型稳定性与字段证据覆盖 |

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

## 6. 单变量对照

- 唯一变化：字段集合从 `core` 扩为 `core + 盗窃罪 extensions`。
- 固定：同一事实文本、模型、温度、端点、系统约束、JSON 输出格式。
- 预期讨论而非预设结果：扩展 schema 提供了表达罪名特有因素的“槽位”，但槽位出现、值非空和值正确是三件不同的事；本地小模型两次调用也可能有非确定性。
- 证据边界：这是一次本地模型对照，不能评价教材历史运行所用模型，也不能用一次结果证明自下而上 schema 必然优于人工 schema。

## 7. 理解检查：每次只问一题

1. **概念复述**：字段发现和结构化抽取分别消费什么输入、产生什么输出？为什么不能合并成“模型读懂了案例”？
2. **结果预测**：若只删去 `盗窃罪 extensions`，哪些输出变化来自表示空间变窄，哪些必须实跑后才能知道？
3. **故障定位**：某条抽取把原文未提及的“累犯”写成 `false`，你会依次检查请求、原始响应、标准化代码和原文中的什么证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 发现阶段消费多条训练案例、产出 schema；抽取阶段消费 schema + 单条案例、产出统一字段记录。前者定义比较坐标，后者填值。
2. schema 删除扩展字段后，这些键不再是允许输出的字段属于表示变化；模型是否还会越界输出、核心字段值是否变化、具体非空字段是什么必须看实跑。
3. 先确认该字段在请求 schema 中、原文是否明确支持/否定/未提及，再看原始模型响应是否已给错；若原始是 `null` 而标准化变成 `false`，定位 normalization；若请求没带原文或字段，则定位请求构造。最终还要区分字段值、后续聚类和业务结论。

</details>

卡住时换成客服退款工单例子复查：`是否已退款=false` 与“工单没写是否退款”不是同一事实。

## 8. 可选 AI 代码演示

默认不布置编程作业。本课 PowerShell 中的 Python 是讲师提供的最小实验胶水，学员负责预测、运行和解释，不要求改写。

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
- 是否采用代码演示及原因：采用教材冻结收据读取 + 本地 Ollama 最小调用；避免整章依赖和外部 API 成本干扰核心变量
- 本次观察、假设、待验证点：教材历史 evidence 记录一次通过的官方 campaign，但不把通过标签当单字段正确性；本机目标实验尚未运行
- 对应备课 skill：`vendor/ai-agent-book/skills/knowledge-org/SKILL.md`
- 进度处理：授课结束或中断后追加 Day 24 日志；仅备课不追加上课日志。
