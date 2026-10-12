// Day 1 — Agent 三要素
// 跑法： kotlinc kotlin\d01-agent-three-elements.kts -script
//
// 讲师只给规格和 assert。函数体是你自己写的 —— 我不会给实现，也别问。
// 目标：把「Agent = LLM + 上下文 + 工具」从一句口号，变成你能编译、能断言的东西。

// ---------- 规格（三要素，已由讲师定义，不要改） ----------

/** 大脑：决策内核。它不产出动作，只产出「下一步想干什么」。 */
data class Llm(val model: String)

/** 手脚：一个能对外部世界产生作用或读取外部世界的接口。 */
data class Tool(val name: String, val description: String)

/** 眼睛：这一轮决策点上，模型能看到的全部信息。 */
data class Context(
    val systemPrompt: String,
    val tools: List<Tool>,
    val history: List<String>,
    val toolResults: List<String>,
)

// ---------- 你要实现的 ----------

/**
 * 把三要素组装成一次真正要发给模型的请求体。
 *
 * 这里该发生什么：请求体里必须同时带上「用哪个模型」「有哪些工具可用」
 * 「之前发生了什么」。三者缺一，Agent 就不成立 —— 下面的 assert 会告诉你是哪一个。
 *
 * 提示：对照 vendor/ai-agent-book/chapter1/context/agent.py 里真正发出去的那个
 * JSON（跑完实验后终端会打印 ITERATION N - FULL RESPONSE 的完整请求体），
 * 你的 Map 结构应该和它对得上。
 */
fun assemble(llm: Llm?, ctx: Context): Map<String, Any?> {
    TODO("学员手写：把三要素装进请求体")
}

/**
 * 一次工具调用真的执行了吗？
 *
 * 这里该发生什么：模型「说要调用 convert_currency」和「convert_currency 真的跑了」
 * 是两件事。前者在 assistant 消息里，后者在 tool 消息里，靠 toolCallId 配对。
 * 只有配上了对，才算真的动手了。
 */
fun executed(requestedIds: List<String>, returnedIds: List<String>): Boolean {
    TODO("学员手写：判断请求的调用是否都拿到了结果")
}

// ---------- 自检（这部分不许改，改了就等于自己给自己放水） ----------

val tools = listOf(
    Tool("convert_currency", "按固定汇率换算货币"),
    Tool("code_interpreter", "执行 Python 代码做计算"),
)
val ctx = Context(
    systemPrompt = "你是一个能调用工具的助手。",
    tools = tools,
    history = listOf("用户：1000 美元等于多少欧元？"),
    toolResults = listOf("convert_currency -> 920 EUR"),
)

// 1. 三件都在时，请求体必须三件都带上
val full = assemble(Llm("qwen3:8b"), ctx)
assert(full["model"] == "qwen3:8b") { "大脑丢了：请求体里没有 model" }
assert((full["tools"] as List<*>).size == 2) { "手脚丢了：工具定义没进请求体" }
assert(full["messages"] != null) { "眼睛丢了：历史/系统提示词没进请求体" }

// 2. 抽掉工具定义 → 请求体仍然合法，但动作空间空了
//    （这一条是 Day 2 消融实验的预告：模型不会报错，它只会开始瞎编）
val noTools = assemble(Llm("qwen3:8b"), ctx.copy(tools = emptyList()))
assert((noTools["tools"] as List<*>).isEmpty()) { "抽掉工具后请求体里不该还有工具" }
assert(noTools["model"] == "qwen3:8b") { "抽掉工具不该影响大脑" }

// 3. 边界：llm 为 null 时必须抛 IllegalArgumentException，
//    而不是返回一个 model=null 的请求体让它在网络层才炸。
val rejected = try {
    assemble(null, ctx); false
} catch (e: IllegalArgumentException) {
    true
}
assert(rejected) { "没有大脑就不是 Agent，必须立刻失败，不能带病发请求" }

// 4. 工具调用的配对：模型自己的解释不算证据，配上了对才算
assert(executed(listOf("call_1"), listOf("call_1"))) { "一请求一返回，应该算执行了" }
assert(!executed(listOf("call_1", "call_2"), listOf("call_1"))) { "少一个返回就不算全执行" }
assert(!executed(listOf("call_1"), listOf("call_9"))) { "id 对不上就是没执行，哪怕数量一样" }

println("Day 1 全部 assert 通过。")
