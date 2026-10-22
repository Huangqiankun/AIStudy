# Day 07 — 相同最终答案，如何判断执行路径是否成功

> 章节：W1 / ch1 buffer 复盘
> 实验：`vendor/ai-agent-book/chapter1/context/`
> 备课日期 2026-10-22 ｜ 授课日期 2026-10-22 ｜ 状态 ✅

本日是 buffer 日，不引入新课，只复盘 Day 1–6 的证据边界与故障定位。

## 1. 核心问题与实验全景

**本课只回答**：两个运行都有相同的最终答案时，怎样根据完整轨迹判断原始路径、验证与纠正是否分别成功？

**类比**：Android 客户端最终显示“成功”，不等于 Binder 调用、reply Parcel、服务端处理和权威状态都成功；Agent 的最终文本同样不能代替中间链路证据。

```text
输入 → Tool / Constrain → Verify → Correct → Final
```

- 任务：比较最终都得到 `14` 的基线和单变量对照。
- 学员亲自运行：同一 PowerShell 探针；对照只把表达式从 `2 + 3 * 4` 改为 `().__class__`。
- 观察点：`constrain.tool_result`、`verify.passed`、`correct.action`、`final.result`。
- 结束目标：区分工具直接成功与原输入被拒绝后 fallback 成功，并能从下一轮消息定位 Harness 回传故障。

## 2. 实验前预测

- 基线：普通算式 `2 + 3 * 4`，期望值固定为 `14`。
- 对照：仅把原始表达式改成 `().__class__`；Harness、工具、期望值和 fallback 均不变。
- 学员起初预测对照的 `constrain.tool_result` 会是数字，`verify=false`，随后 `Correct=fallback` 且内部验证通过，最终为 `14`。
- 真实结果修正了第一项：原输入没有产生数字，而是返回结构化错误。

## 3. 学员实跑证据

### 基线（2026-10-22 15:02）

学员在自己的 PowerShell 中运行。必要摘录：

```text
constrain.tool_result = {expression: "2 + 3 * 4", result: 14, type: "int"}
verify.passed = true
correct.action = none
final.result = 14
```

### 单变量对照（2026-10-22 15:09）

学员在同一 PowerShell 会话中仅替换探针末行输入。必要摘录：

```text
constrain.tool_result.error = "unsupported syntax: Attribute"
verify.passed = false
correct.action = fallback
correct.result.result = 14
correct.verified = true
final.result = 14
```

实测说明相同的 `final.result=14` 掩盖了两条不同路径：基线由原输入直接通过，比较组由原输入被拒绝后再恢复。

## 4. 关键源码

- `vendor/ai-agent-book/chapter1/context/agent.py:262-282`
  - 第 263 行记录待计算表达式。
  - 第 272 行调用 `safe_eval(expression)`。
  - 第 274–278 行构造成功结果。
  - 第 280–282 行捕获异常并返回 `{"error": ...}`。
- `vendor/ai-agent-book/chapter1/context/calc_sandbox.py:87-90`
  - 白名单外的 `Attribute`、`Subscript` 等节点落入 `else`，抛出 `UnsafeExpression`。

学员能够沿源码说明：`().__class__` 进入 `calculate()`，在 `safe_eval()` 的 AST 检查中因 `Attribute` 被拒绝，异常回到 `calculate()` 后被捕获并转成结构化错误，外层再把它写入 `constrain.tool_result`。

## 5. 证据不一致点

学员贴回的比较输出只显示 fallback 的 `Calculating: 2 + 3 * 4` 日志，没有显示源码控制流预期的原输入 INFO 和异常 ERROR。当前日志配置仅为标准 `logging.basicConfig(level=logging.INFO)`，没有发现能直接解释缺失的过滤器。

- 实测观察：贴回输出中缺少两条预期日志。
- 源码预测：第 263 行在 `safe_eval()` 之前，原输入应先记录 INFO；第 281 行应记录 ERROR。
- 待验证判断：缺失可能与复制范围或流捕获有关，但本课未做隔离复现，不能据此下结论。
- 该缺失不能推翻结构化返回与源码控制流已经形成的证据链。

## 6. 理解检查

1. **概念复述**：学员指出不能凭最终答案判断整个路径成功；还要确认工具真实返回、结果是否进入上下文以及最终答案是否来自该证据。通过。
2. **结果预测**：首次把被拒绝输入预测为数值返回；经真实 `unsupported syntax: Attribute` 和源码解释校准后，对新例 `[10, 20][0]` 正确预测 `Subscript` 会产生结构化错误、`Verify=false` 并启动 `Correct`。通过。
3. **故障定位**：面对“本地工具返回 `920 EUR`，但下一轮只有 `system/user`、没有 `role=tool`”的轨迹，学员优先定位 Harness 的结果回传／上下文组装，并指出不能断言模型看见结果后仍算错，也不能由最终文本证明 Verify/Correct 是否存在。通过。

## 7. 课堂结论与后续

- 已理解：计划调用记录、工具直接返回、验证判定、纠正动作和最终文本是不同证据层。
- 已理解：同一个最终值可能来自直接成功，也可能来自失败后的恢复路径。
- 已理解：下一轮请求缺少 `role=tool` 时，优先检查 Harness 的结果回传，而不是先归因模型能力。
- 尚未验证：比较运行中两条预期日志缺失的具体原因；该问题不影响本课核心结论，后续需要时用流重定向做隔离诊断。
- 下一步：Day 8 进入 ch2“消息四种角色”。
- 状态：✅

## 8. 讲师备注

- 本课不调用模型，不修改 `vendor/`，复用了 Day 5 的最小 Harness 探针。
- 学员亲自完成基线、单变量对照、源码读取与三项理解检查。
- 本日为 buffer 复盘，不引入新的章节概念。
