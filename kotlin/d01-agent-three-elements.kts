// Day 1 — Agent 三要素
// PowerShell 跑法（先设置 JAVA_HOME）：
// $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
// & C:\tools\kotlinc\bin\kotlinc.bat kotlin\d01-agent-three-elements.kts -script -J-ea -J-Dstdout.encoding=UTF-8 -J-Dstderr.encoding=UTF-8
//
// 本次按你的要求补全两个 TODO；结合工具日志阅读函数体，再运行末尾的 assert。
// 目标：把「Agent = LLM + 上下文 + 工具」从一句口号，变成你能编译、能断言的东西。

// ---------- 规格（三要素，已由讲师定义，不要改） ----------

/** 大脑：决策内核。它可以请求调用工具，真正执行工具的是框架。 */
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
 * 把三要素组装成一个便于观察的教学请求快照。
 *
 * 这里该发生什么：请求体里必须同时带上「用哪个模型」「有哪些工具可用」
 * 「之前发生了什么」，并保留工具返回值，包括错误信息。
 * 工具列表可以为空，那就是一种消融；模型缺失则必须立即报错。
 *
 * 这是简化模型：history 都按 user 消息处理，tools 只保留名称和说明。
 * 它不能直接发给 Ollama：真实请求还需要工具参数 schema、assistant 的
 * tool_calls，以及 tool 消息上的 tool_call_id；当前 Context 未建模这些字段。
 */
fun assemble(llm: Llm?, ctx: Context): Map<String, Any?> {
    val model = requireNotNull(llm) { "缺少 LLM，无法组装请求" }

    val messages = buildList {
        add(mapOf("role" to "system", "content" to ctx.systemPrompt))
        ctx.history.forEach { add(mapOf("role" to "user", "content" to it)) }
        // 成功值和错误信息都属于反馈；模型需要看到它们才能调整下一次调用。
        ctx.toolResults.forEach { add(mapOf("role" to "tool", "content" to it)) }
    }

    return mapOf(
        "model" to model.model,
        "tools" to ctx.tools.map { mapOf("name" to it.name, "description" to it.description) },
        "messages" to messages,
    )
}

/**
 * 请求的工具调用是否都有对应的返回记录？
 *
 * 这里该发生什么：模型「说要调用 convert_currency」和「convert_currency 真的跑了」
 * 是两件事。前者在 assistant 消息里，后者在 tool 消息里，靠 toolCallId 配对。
 * 调用 ID 按一次调用唯一标识处理，返回顺序不影响配对。
 * 错误返回也算有反馈：实验中第二次换汇报错，第三次才传入数字 920。
 * 因此本函数不判断工具成功、任务正确，也不判断模型是否看到了结果。
 * 没有请求时返回 true，表示没有等待返回的调用。
 */
fun executed(requestedIds: List<String>, returnedIds: List<String>): Boolean {
    val returned = returnedIds.toSet()
    return requestedIds.all { it in returned }
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

val fullMessages = full["messages"] as List<*>

assert(fullMessages.any {
    val message = it as? Map<*, *>
    message?.get("role") == "system" &&
        message?.get("content") == ctx.systemPrompt
}) {
    "系统提示词必须实际进入 messages"
}

assert(ctx.history.all { text ->
    fullMessages.any {
        val message = it as? Map<*, *>
        message?.get("role") == "user" &&
            message?.get("content") == text
    }
}) {
    "用户任务必须实际进入 messages"
}

assert(ctx.toolResults.all { text ->
    fullMessages.any {
        val message = it as? Map<*, *>
        message?.get("role") == "tool" &&
            message?.get("content") == text
    }
}) {
    "工具结果必须实际进入 messages，框架本地存着结果还不够"
}

// 2. 抽掉工具定义 → 请求体仍然合法，但动作空间空了
//    （消融预告：它可能拒绝，也可能自行给出答案；需要观察实际运行。）
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

// 5. 结合本次实验：错误信息也必须进入上下文，才能供下一轮纠错。
val errorResult = "convert_currency -> error: amount 必须是数字"
val withError = assemble(Llm("qwen3:8b"), ctx.copy(toolResults = ctx.toolResults + errorResult))



assert((withError["messages"] as List<*>).any {
    val message = it as? Map<*, *>
    message?.get("role") == "tool" &&
        message?.get("content") == errorResult
}) {
     "不能把错误反馈过滤掉，否则模型无法根据它调整参数"
}




// 抽掉结果只改变模型能看到的反馈，不影响本地调用 ID 的配对记录。
val noResults = assemble(Llm("qwen3:8b"), ctx.copy(toolResults = emptyList()))
assert((noResults["messages"] as List<*>).none { (it as Map<*, *>)["role"] == "tool" }) {
    "抽掉工具结果后，教学快照里不该还有工具反馈"
}
assert(noResults["tools"] == full["tools"] && noResults["model"] == full["model"]) {
    "抽掉结果不能顺带更换模型或工具定义"
}
assert(executed(listOf("call_1", "call_2", "call_3"), listOf("call_3", "call_1", "call_2"))) {
    "三次都有返回就算配对完整，包括报错的第二次；返回顺序不影响判断"
}

println("Day 1 全部 assert 通过。")
