# Day NN — <一句话标题>

> 章节 `vendor/ai-agent-book/book/chapterN.md` § <小节名>（第 NN–NN 行）
> 实验 `vendor/ai-agent-book/chapterN/<dir>/`
> 日期 YYYY-MM-DD ｜ 状态 ⬜

---

## 1. 今日一问（2′，先猜，别往下看）

> <一个安卓直觉能答对一半的问题。不要给答案。>

## 2. 你已经会这个了（3′）

<从 `AGENTS.md` 类比库选一条，一句话说清对应关系。>

| 你熟的 | 今天的 |
|---|---|
| `...` | `...` |
| `...` | `...` |

## 3. 最小知识（5′）

≤200 字，只讲**一个**点。

```
<一张 ASCII 图>
```

书里的原话在 `book/chapterN.md:NN-NN`，想深挖自己去看，这里不抄。

## 4. 实验讲解（25′，主菜）

### A. 跑一次（只跑不改）

```cmd
cd /d F:\AIStudy\vendor\ai-agent-book\chapterN\<dir>
python -m uv run --extra chN python main.py --mode <?> --provider ollama
```

**应该看到**：<现象>
**如果看到 <异常>** → 说明 <原因>

### B. 逐段读输出轨迹

把上面那次跑的输出**分段贴在这里**，每段后面写一句"这段说明了什么"。
重点标注：模型在哪一步做了决定、它当时看得见什么、哪一步开始偏。

```
<轨迹片段 1>
```
→ 说明：

```
<轨迹片段 2>
```
→ 说明：

### C. 读关键源码 20–40 行

**只读这一段，别通读整个文件**：`vendor/ai-agent-book/chapterN/<dir>/<file>.py:NN-NN`

```python
<那 20–40 行>
```

逐行回答三个问题：
1. 这段的输入是什么、输出是什么？
2. 哪一行是今天这个知识点的**落点**？
3. 如果把那一行删掉，轨迹会变成什么样？（**这就是 Day 2 消融实验的思路**）

## 5. 自检三问（5′）

1. …
2. …
3. …

<details><summary>答案</summary>

…

</details>

## 6. 移植作业（可选 · 约每周 2–3 次，没有合适对象就整节删掉）

> **布置前自查**：这是**翻译题**还是**设计题**？
> 如果学员打开文件会面对一片空白、不知道从何下手 —— 那就是设计题，**不许布置**。
> Day 1 就是栽在这里：让学员从零设计 `assemble` 的输出结构，他没参照物，只能去找 AI 要示范。

**移植对象**：`vendor/ai-agent-book/chapterN/<dir>/<file>.py:NN-NN` 的 `<函数名>`
**它做什么**：<一句话>
**输入 → 输出例子**：`<具体例子>`
**文件**：`kotlin/dNN-<slug>.kts`（签名和 assert 已给，函数体你自己写）
**交**：可以跨天，Day N+1 开头交

```bash
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
C:\tools\kotlinc\bin\kotlinc kotlin\dNN-<slug>.kts -script -J-ea -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8
```

> 红线：AI 可以查语法、看示范，**看完关掉，函数体自己敲**。review 时随机指一行问"为什么在这"，答不上记 `🔁`。

## 7. 卡点（课后学员自己填）

- 卡在：
- 下次开课前要先复习：
- 状态：`✅` / `▶` / `🔁`

## 8. 讲师备注（只给 agent 看，学员可忽略）

- 实际用时：__ 分（超 45 就砍明天的内容）
- 布置移植作业了吗：是（对象+行号）／否（**为什么没有合适对象**）
- 变异电池跑了吗：断言类作业必须跑，**四类变异缺一不可**（见 `AGENTS.md`）
- 偏离计划：
- 本周后面哪几天依赖这个点：
- 今天攒下的、后面要回收的案例：
- 对应备课 skill：`vendor/ai-agent-book/skills/<name>/SKILL.md`
