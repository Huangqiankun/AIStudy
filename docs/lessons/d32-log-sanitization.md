# Day 32 — 日志脱敏后，怎样确认敏感值消失而排障证据仍在

> 章节 `vendor/ai-agent-book/book/chapter3.md` § 隐私保护：日志脱敏（第 231–239 行）
> 实验 `vendor/ai-agent-book/chapter3/log-sanitization/`
> 备课日期 2026-11-19 ｜ 授课日期 待开展 ｜ 状态 待开展

本文件按 `_TEMPLATE.md` 建立，分步使用。仅核验源码和 CLI，目标实验尚未执行；不将备课记为完成。

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
- 学员预测：待回答。

## 3. 运行实验

备课已运行 `python -m uv run --no-sync --extra ch3 python main.py --help`，退出码 0。`--no-sync` 复用已安装环境。没有运行演示或对照。

**命令用途**：读取内置人工样例 → 对每条调用规则引擎 → 打印原文与脱敏文本 → 汇总类别与命中数。用来核对替换位置与剩余上下文；演示会打印人工原值，不是生产日志输出模板。

完成预测后，才给学员当前步骤的命令：

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter3\log-sanitization'
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
python -m uv run --no-sync --extra ch3 python main.py --demo --mode regex
```

真实证据：待学员运行并贴回。本模式打印控制台，不因 README 列出 `output/` 就假定它已保存文件。`--output` 仅用于 `--input` 路径。

## 4. 逐段读真实轨迹

一次选一条样例，分别核对原文敏感片段、after 中占位符、剩余错误状态与调用上下文。命中摘要和脱敏文本是不同输出。

| 证据位置 | 实测观察 | 学员解释 |
|---|---|---|
| before/after | 待运行 | 待回答 |
| 类别与数量 | 待运行 | 待回答 |
| 最终输出去向 | 待核对当前调用路径 | 待回答 |

## 5. 追到关键源码

备课核实，课堂分段读约 35 行：

- `regex_sanitizer.py:118–125`：口令赋值规则，键名、分隔符、值和捕获组。
- `regex_sanitizer.py:195–207`：匹配、选取捕获组、可选校验。
- `regex_sanitizer.py:225–239`：按位置重建文本并返回 findings。

辅助定位：`main.py` 的 `demo_regex_mode` 打印 before/after；`sanitize_file` 将 `redacted` 写入输出文件。重叠候选优先级位于 `regex_sanitizer.py:217–223`，需要解释重叠时再展开。

教师提示：`findings` 含原始 `value`，因此脱敏文本与整份返回对象需要分开审查；函数返回不等于整个日志链已经安全。这里只是源码核验，尚未讲解或实测。

## 6. 单变量对照

- 人工输入：`request_id=req-32 password=DemoOnly42 status=401`。
- 唯一变化：字段名 `password` 改为 `login_hint`，业务上约定其值仍是口令。
- 不变：敏感值、请求 ID、状态、引擎和其他文本。
- 预测问题：只改字段名，同一段口令还能否被当前规则识别？说明依据。
- 学员预测与结果：待回答、待运行。

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
    }, ensure_ascii=False, indent=2))
'@ | python -m uv run --no-sync --extra ch3 python -
```

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行 | 待结合规则解释 | 不外推所有字段或模型方法的表现 |

## 7. 理解检查：每次只问一题

1. 概念：日志脱敏为何需要同时检查敏感值和剩余排障信息？
2. 预测：已替换文本没有原口令，但调用方又打印了完整 findings，会怎样？
3. 定位：函数返回文本已替换，落盘文件却仍有原值，下一步先查哪里、用什么证据验证？

<details><summary>讲师核对要点（回答后再展开）</summary>

1. 区分漏检与过度删除；命中数不能单独证明覆盖或可用性。
2. findings 中的 value 可重新暴露原值；必须核对调用方实际输出了哪些字段。
3. 追查写入参数是否为原文、其他日志出口与文件时间/路径；用回读确认，不能只看成功提示。不要直接归因模型。

</details>

## 8. 可选 AI 代码演示

第 6 节提供最小内存对照，直接调用教材函数。学员负责预测和解释，无手写作业。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展。
- 尚未理解及证据：待开展。
- 下一步：开场回应后，建立概念和实验全景，先问一个确认问题。
- 状态：待开展。

## 10. 讲师备注

- 当前仅完成备课与 CLI 核验；未代跑目标实验，尚无本课实测结论。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 对应备课 skill：`vendor/ai-agent-book/skills/memory-system/SKILL.md`。
- 教材小模型效果与召回率描述不作为本课实测；本课离线规则实验不能证明模型脱敏表现。
- LLM 路径存在远端回退实现，本课不启用该路径。后续如扩展，需另核实真实数据流。
- `mem0`、`memobase` 按路线仅阅读，不运行。
- 授课结束或中断后才追加第 32 课进度；当前备课不追加上课日志。
