# Day NN — <一句话标题>

> 章节 `vendor/ai-agent-book/book/chapterN.md` § <小节名>
> 实验 `vendor/ai-agent-book/chapterN/<dir>/`
> Kotlin `kotlin/dNN-<slug>.kts`
> 日期 YYYY-MM-DD ｜ 状态 ⬜

---

## 1. 今日一问（先猜，别往下看）

> <一个安卓直觉能答对一半的问题。不要给答案。>

## 2. 你已经会这个了

<从 `AGENTS.md` 类比库选一条，一句话说清对应关系。>

| 你熟的 | 今天的 |
|---|---|
| `...` | `...` |
| `...` | `...` |

代码对读（≤5 行，左边你熟的，右边实验里的对应片段）：

```kotlin
// 你熟的
```
```python
# 今天的（vendor/.../xxx.py:NN）
```

## 3. 最小知识

≤200 字，只讲**一个**点。

```
<一张 ASCII 图>
```

书里的原话在 § <小节>，想深挖自己去看，这里不抄。

## 4. 跑一下

### A. Python 观察（只跑不改）

```bash
cd vendor/ai-agent-book/chapterN/<dir>
python -m uv run --extra chN python main.py --mode <?> --provider ollama
```

**应该看到**：<现象>
**如果看到 <异常现象>** → 说明 <原因>

### B. Kotlin 手写（你自己写实现，我只给 assert）

在 `kotlin/dNN-<slug>.kts` 里实现 `<签名>`，让它通过：

```kotlin
assert(<不变量 1>)
assert(<不变量 2>)
assert(<边界：坏输入下必须降级，而不是崩>)
```

跑：

```bash
export JAVA_HOME="C:\\Program Files\\Android\\Android Studio\\jbr"
/c/tools/kotlinc/bin/kotlinc kotlin/dNN-<slug>.kts -script \
  -J-ea -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8
```

> 讲师红线：只给签名和 assert，**绝不给实现**。学员自己敲完、跑通，再拿回来 review。

## 5. 自检三问

1. …
2. …
3. …

<details><summary>答案</summary>

…

</details>

## 6. 卡点（课后学员自己填）

- 卡在：
- 下次开课前要先复习：
- 状态：`✅` / `▶` / `🔁`

## 7. 讲师备注（只给 agent 看，学员可忽略）

- 实际用时：__ 分（超 30 就要砍明天的内容）
- 偏离计划：
- 本周后面哪几天依赖这个点：
- 对应备课 skill：`vendor/ai-agent-book/skills/<name>/SKILL.md`
