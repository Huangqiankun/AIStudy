# Day 10 — Chat Template 怎样决定 KV Cache 能复用到哪里

> 章节 `vendor/ai-agent-book/book/chapter2.md` § KV Cache 友好的上下文设计（第 435–596 行）
> 实验 `vendor/ai-agent-book/chapter2/kv-cache/`
> 备课日期 2026-10-23 ｜ 授课日期 2026-10-23 ｜ 状态 ▶

本文件是分步教学材料，不一次性贴入对话。正式观察和对照均由学员在预测后亲自运行；讲师只核实环境、CLI、收据来源、源码和命令语法。

## 0. 证据边界

本机课程默认使用 Ollama，但本实验的 `cached_tokens` 指标来自 Kimi API。为避免把耗时波动冒充缓存证据，本课采用两条互补证据链：

1. `ollama show qwen3:8b --template` 是本机当前模型模板的实测观察，只用于证明结构化消息还会经过 Chat Template；它不证明跨请求缓存命中。
2. `main.py --report` 是本次在学员机器上执行的离线计算；输入的 `result_*.json` 是教材随附的 2026-07-18 历史线上收据。报告可验证计算与字段，但不能冒充本次重新调用 Kimi。

本课不使用单次 TTFT 快慢作为缓存命中的充分证据。若以后有可用 Kimi API Key，再把同样的 `correct` / `shuffled_tools` 任务做成当前线上复现；本课不要求提供密钥。

## 1. 建立概念：API 消息先被装进“模型家族自己的信封”

**本课只回答**：结构化 `messages` 怎样变成模型实际读取的 token 前缀，而前缀中某处变化为什么会决定 KV Cache 从哪里开始不能复用？

**类比**：Chat Template 像 Android Binder 的 Parcel 编码约定：业务对象不是直接穿过内核，而是先按协议写入带边界和类型的信息流。KV Cache 则像 Compose 的稳定前缀复用：前面的输入保持稳定，就可以沿用已计算状态；从第一个变化点开始，后续依赖它的计算需要更新。类比边界是：Compose 的跳过依据不是 token 前缀，KV Cache 也不是应用层对象缓存。

```text
API 层                                  模型输入层
messages + tools
[{system}, {user}, ...]                 Chat Template
          │                    ┌──────────────────────────────┐
          └───────────────────>│ 特殊 token + 角色边界 + 内容 │
                               └──────────────┬───────────────┘
                                              │
                               [稳定前缀][首个变化][变化后缀]
                               <──可复用──>   <──需重算────>
```

**实验全景预告**（只说明路线，不揭示结果）：

- 要完成的任务：找到本机 Qwen3 的真实 Chat Template，并比较同一线上任务在固定工具顺序与每轮打乱工具顺序下留下的缓存收据。
- 学员会亲自运行：先查看 Ollama 模型模板；再用 `--report` 分别读取 `correct` 与 `shuffled_tools` 两份历史收据。
- 会得到的产物：`lesson-d10-ollama-template.txt`、`lesson-d10-baseline.txt`、`lesson-d10-comparison.txt`，以及两份原始 `result_*.json`。
- 结束时要能回答：角色消息如何变成 token 流、为何“内容集合相同但顺序不同”仍会改变前缀，以及哪类字段能证明缓存命中。

**确认问题**：假设三份工具定义的文字完全没变，只把顺序从 `A,B,C` 改成 `B,A,C`。对模型最终看到的 token 前缀来说，这是“同一份内容”还是“从某个位置起已经不同”？你判断的依据是什么？

**学员回答**：待回答。

## 2. 实验前预测

- 任务与输入：同一个代码仓分析任务、同一个 Kimi 模型、同一组工具定义；比较 `correct` 与 `shuffled_tools` 收据。
- 固定条件：历史收据均来自 `kimi-k2.6`；报告程序、`--cache-price-ratio 0.1` 和指标算法相同。
- 唯一关注的策略变化：工具定义保持相同，但 `shuffled_tools` 在每轮请求前改变排列顺序。
- 观察点：`prompt_tokens`、`cached_tokens`、`Cache%`、各轮 TTFT；先看缓存 token，再把耗时当辅助信号。
- 预测问题：哪一种策略更可能保留更长的共同 token 前缀？对应的 `Cache%` 应向哪个方向变化？为什么？
- 学员原始预测：待回答。

## 3. 运行实验

以下步骤在课堂中一次只给一条命令，收到真实输出后才继续。

### 步骤 A：进入目录并设置当前 PowerShell 环境

```powershell
Set-Location 'F:\AIStudy\vendor\ai-agent-book\chapter2\kv-cache'; $env:NO_PROXY = 'localhost,127.0.0.1,::1'; $env:PYTHONUTF8 = '1'; $env:PYTHONIOENCODING = 'utf-8'
```

### 步骤 B：核对 CLI

```powershell
python -m uv run --extra ch2 python main.py --help
```

### 步骤 C：读取本机当前 Qwen3 Chat Template

```powershell
ollama show qwen3:8b --template | Tee-Object -FilePath lesson-d10-ollama-template.txt
```

**观察边界**：这里只证明 Ollama 为该模型保存了模板，以及模板如何引用角色/内容/工具；不要由此推断 Prompt Cache 已命中。

### 步骤 D：离线重算固定顺序收据

```powershell
python -m uv run --extra ch2 python main.py --report --input result_correct_20260718_kimi_k2_6.json | Tee-Object -FilePath lesson-d10-baseline.txt
```

### 步骤 E：预测后运行单变量对照

```powershell
python -m uv run --extra ch2 python main.py --report --input result_shuffled_tools_20260718_kimi_k2_6.json | Tee-Object -FilePath lesson-d10-comparison.txt
```

**准备观察什么**：两个报告的 `Prompt`、`Cached`、`Cache%` 与 TTFT；同时打开原始 JSON 核对报告不是凭空生成数字。

**真实证据**：待学员运行并贴回输出。记录当前命令、退出状态和产物位置；将报告标为“本次离线重算”，将 JSON 内指标标为“教材随附历史线上收据”。

## 4. 逐段读真实证据

每次只展示一个片段，等学员解释后继续。

| 片段 | 真实摘录 / 证据字段 | 能证明什么 | 不能证明什么 | 学员解释 |
|---|---|---|---|---|
| 本机模板 | 待运行 | 当前 Ollama 模型的模板文本 | 跨请求缓存是否命中 | 待回答 |
| 固定顺序收据 | 待运行 | 历史线上调用上报的 token/耗时字段 | 本次重新调用 Kimi | 待回答 |
| 打乱顺序收据 | 待运行 | 同上，策略为 `shuffled_tools` | 单次耗时差的必然因果 | 待回答 |
| 离线报告 | 待运行 | 当前代码怎样从收据计算比例 | 厂商当前价格或当前服务状态 | 待回答 |

分别核对：请求前缀是否稳定、服务商是否上报 `cached_tokens`、报告如何计算比例、TTFT 是否只被当作辅助观察。结果不符合预测也保留，不挑选数据。

## 5. 追到关键源码

关键源码总量控制在约 20–40 行，课堂分段读取：

1. `vendor/ai-agent-book/chapter2/kv-cache/agent.py:493-501`，`_get_tools()`：复制同一组工具定义；仅 `SHUFFLED_TOOLS` 调用 `random.shuffle()`。
2. `vendor/ai-agent-book/chapter2/kv-cache/agent.py:646-667`，`run()`：`correct` 复用并追加消息，其他模式在迭代开始重建请求消息。
3. `vendor/ai-agent-book/chapter2/kv-cache/agent.py:700-725`：读取服务商返回的 `cached_tokens`，分别累计命中和未命中。
4. `vendor/ai-agent-book/chapter2/kv-cache/main.py:95-114`，`print_comparison_table()`：`Cache% = cached_tokens / prompt_tokens`，成本列只是透明估算。

- 输入 → 处理 → 输出：模式选择 → 组装 `messages/tools` → API 使用 Chat Template 线性化 → 服务商返回 usage → 报告计算比例。
- 与证据连接的关键行：`random.shuffle(tools)` 改变前缀序列；`usage.cached_tokens` 是缓存证据来源；报告函数只做派生计算。
- 如果工具定义只是新建了 Python 对象、序列化后的 token 序列却逐字相同，不能仅凭“重建对象”宣称缓存失效；缓存边界看的是服务端接收并模板化后的前缀。
- 学员解释：待回答。

## 6. 单变量对照

- 唯一关注的条件：工具定义顺序固定 vs 每轮打乱。
- 保持不变：模型、任务、工具定义集合、报告算法和缓存价格比例。
- 对照前预测：待回答。
- 基线与对照命令：见步骤 D / E，由学员亲自运行并贴回输出。
- 真实轨迹与结果：待运行。

| 实测观察 | 解释假设 | 待验证判断 |
|---|---|---|
| 待运行后填写 | 若缓存比例变化，先用“共同 token 前缀长度变化”解释 | 需要当前线上重复实验才能判断该差异在今天的 Kimi 服务上是否复现 |

一次历史对照不能证明所有模型、服务商与请求长度都会得到相同幅度；TTFT 还受网络、服务负载和推理 token 波动影响。

## 7. 理解检查：每次只问一题

1. **概念复述**：Chat Template 在结构化 API 消息与模型 token 流之间做了什么？KV Cache 为什么关心它的输出而不是 Python 对象是否相同？
2. **结果预测**：如果不改 system/tools，只在对话末尾追加一条状态消息，已有前缀与新增后缀分别要怎样处理？
3. **故障定位**：线上报告显示 TTFT 变快但没有 `cached_tokens` 字段。你能否直接宣布缓存命中？下一步查哪三处证据？

<details><summary>讲师核对要点（学员回答后再展开）</summary>

1. 模板把角色、内容和工具定义编码为该模型训练时约定的特殊 token 序列；缓存按实际 token 前缀匹配，不看应用对象身份。
2. 因果注意力下，末尾追加不改变此前 token 的 K/V；旧前缀可复用，新增部分首次计算。具体跨请求命中仍取决于推理引擎/服务商机制。
3. 不能。先核对请求的模板化前缀是否相同，再核对服务商/模型是否支持并返回缓存 usage 字段，最后重复采样并把 TTFT 与网络和推理波动分开；没有权威缓存字段时只能把耗时当弱证据。

</details>

卡住时记录具体缺口，换一个 `A,B,C → B,A,C` 或“末尾追加”小例子复查。三项通过且实验、对照、源码讲解实际完成，才记 `✅`。

## 8. 可选当前线上复现

默认不安排，不要求提供 API Key。只有学员主动选择且本机已有可用的 Kimi Key 时，才在同一任务上运行 `correct` 与 `shuffled_tools`，保存新的 JSON，并明确它们是当前实测。Ollama 的单次耗时差不能替代服务商 `cached_tokens` 证据。

## 9. 卡点（课堂后共同记录）

- 已理解到哪：待开展
- 尚未理解及证据：待开展
- 下次从哪一小步继续：待开展
- 状态：▶（已正式开课，尚未完成实验与理解检查）

## 10. 讲师备注

- 实际授课日期、用时、停在哪一步：2026-10-23 已完成冷启动并正式开课，等待学员回应固定三行。
- 实验命令、模型、证据位置与必要真实摘录：命令已核实；真实输出待学员运行。
- 学员的原始预测与后续解释：待回答。
- 三项理解检查：概念 待检查 / 预测 待检查 / 定位 待检查。
- 是否采用代码演示及原因：未安排；优先使用教材现有报告程序和真实收据。
- 本次观察、假设、待验证点：已确认 CLI 支持离线 `--report`；尚未执行本课目标报告，未观察结果。
- 对应备课 skill：`vendor/ai-agent-book/skills/kv-cache-design/SKILL.md`。
- 进度处理：本次中断时在 `docs/progress.md` 追加 Day 10 `▶`；实验、对照、源码和三项检查完成后才记 `✅`。
