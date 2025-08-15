# Project Context – AI-Enhanced Custom Keyboard (Lancar Fork, GPT-5 Integration)

> **Goal:** Extend an Lancar fork into an AI-powered Android IME with reply/improve/grammar/translate/custom-prompt actions, Auto Text templates, clipboard, emoji, basic billing paywall, and settings. Use **OpenAI GPT-5** via a secure backend relay with SSE streaming.

## Repo & Tech Assumptions

- Base: **Lancar** (Kotlin/Java, Android IME).
- Language: **Kotlin-first** for new code.
- Min SDK: **28+**. Target latest SDK.
- Architecture: **MVVM + Repository**, DI via **Hilt**.
- UI: Keep Lancar keyboard; add a **top action strip** and a **Templates** search strip.
- Keep Lancar licensing headers and attributions intact.

## Features to Add

### AI Actions

1. Reply – Draft reply from context.
2. Improve – Rewrite for clarity/tone.
3. Grammar – Grammar/spelling corrections only.
4. Translate – Auto-detect source → user-selected target.
5. Custom Prompt – Run user-stored instruction.

### Non-AI

- Auto Text (Templates)
- Clipboard
- Emoji

## Monetization & Settings

- Simple Paywall (Play Billing)
- Settings: credits remaining, number row toggle, AI memory, custom prompts CRUD, default translate target, privacy controls

## Guardrails

- No password/private field data sent.
- Truncate context to \~2–3k chars.
- AI fail/offline/out of credits → non-blocking UX.
- Non-AI always available.

## Modules & Structure

```
:app          # launcher, settings, onboarding, billing
:ime          # InputMethodService + keyboard UI
:data         # Room + DataStore
:ai           # GPT-5 provider + prompts + credit metering
:billing      # Google Play Billing
```

## Integration Points in Lancar

- Wrap `LatinIME` in container with action strip.
- Hook into `InputConnection` for context.
- Track selection via `onUpdateSelection`.
- Insert/replace with `beginBatchEdit`/`endBatchEdit`.

## Data Model

- Template
- CustomPrompt
- AiMemory
- CreditLedger (optional)

## Preferences

- alwaysShowNumberRow
- defaultTranslateTarget
- lastUsedAiAction

## AI Provider Abstraction

```kotlin
interface AiProvider { suspend fun generate(task: AiTask): AiResult }

data class AiTask(
  val kind: Kind,
  val context: String,
  val targetLang: String? = null,
  val customPrompt: String? = null
)
sealed class AiResult {
  data class Success(val text: String): AiResult()
  data class Error(val message: String): AiResult()
}
```

## Prompt Templates

- Reply: concise, ≤3 sentences
- Improve: clarity/tone, keep meaning/length
- Grammar: fix grammar/spelling only
- Translate: to `<lang>`, keep formatting
- Custom: user’s prompt as system instruction

## GPT-5 Integration (via Relay)

### Android Deps

```gradle
implementation "com.squareup.okhttp3:okhttp:4.12.0"
implementation "com.squareup.okhttp3:okhttp-sse:4.12.0"
implementation "com.squareup.moshi:moshi:1.15.1"
implementation "com.squareup.moshi:moshi-kotlin:1.15.1"
```

### AiProviderOpenAI

```kotlin
class AiProviderOpenAI(private val baseUrl: String) : AiProvider {
    private val client by lazy { OkHttpClient() }
    private val json = MediaType.get("application/json")

    override suspend fun generate(task: AiTask): AiResult {
        val bodyJson = JSONObject().apply {
            put("kind", task.kind.name)
            put("context", task.context)
            task.targetLang?.let { put("targetLang", it) }
            task.customPrompt?.let { put("customPrompt", it) }
        }.toString()

        val req = Request.Builder()
            .url("$baseUrl/api/keyboard/respond")
            .post(RequestBody.create(json, bodyJson))
            .build()

        val sb = StringBuilder()
        return suspendCancellableCoroutine { cont ->
            val factory = EventSources.createFactory(client)
            val es: EventSource = factory.newEventSource(req, object : EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val obj = JSONObject(data)
                        val delta = obj.optString("text", obj.optString("output_text", ""))
                        if (delta.isNotEmpty()) sb.append(delta)
                    } catch (_: Throwable) {}
                }
                override fun onClosed(es: EventSource) {
                    if (!cont.isCompleted) cont.resume(AiResult.Success(sb.toString()))
                }
                override fun onFailure(es: EventSource, t: Throwable?, response: Response?) {
                    if (!cont.isCompleted) cont.resume(AiResult.Error(t?.message ?: "Stream failed"))
                }
            })
            cont.invokeOnCancellation { es.cancel() }
        }
    }
}
```

In `LatinIME`:

```kotlin
private val aiProvider: AiProvider = AiProviderOpenAI(BuildConfig.KB_RELAY_BASE_URL)
```

In `defaultConfig`:

```gradle
buildConfigField "String", "KB_RELAY_BASE_URL", '"https://YOUR-RELAY.DOMAIN"'
```

### Node/Express Relay

```ts
import express from "express";
import cors from "cors";
import OpenAI from "openai";

const app = express();
app.use(cors());
app.use(express.json());

const openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY! });

app.post("/api/keyboard/respond", async (req, res) => {
  const { kind, context, targetLang, customPrompt } = req.body;
  const system = (() => {
    switch (kind) {
      case "REPLY": return "Draft a concise, friendly reply (≤3 sentences).";
      case "IMPROVE": return "Rewrite for clarity and friendly tone.";
      case "GRAMMAR": return "Fix grammar/spelling only.";
      case "TRANSLATE": return `Translate to ${targetLang ?? "id"}.`;
      case "CUSTOM": return customPrompt ?? "Follow instruction.";
    }
  })();

  res.setHeader("Content-Type", "text/event-stream");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("Connection", "keep-alive");

  try {
    const stream = await openai.responses.stream({
      model: "gpt-5",
      input: [
        { role: "system", content: system },
        { role: "user", content: (context ?? "").slice(-3500) }
      ]
    });

    stream.on("data", delta => res.write(`data: ${JSON.stringify(delta)}\\n\\n`));
    stream.on("end", () => res.end());
    stream.on("error", e => {
      res.write(`event: error\\ndata: ${JSON.stringify({ message: String(e) })}\\n\\n`);
      res.end();
    });
  } catch (e) {
    res.write(`event: error\\ndata: ${JSON.stringify({ message: String(e) })}\\n\\n`);
    res.end();
  }
});

app.listen(process.env.PORT ?? 8787, () => console.log("Relay running"));
```

## Acceptance Criteria

- IME loads with top strip.
- AI actions stream GPT-5 output via relay.
- Templates CRUD works.
- Credits decrement on AI use.
- Privacy guards enforced.
- Offline degrades gracefully.

## Roadmap

1. Wrap `LatinIME` with strip.
2. Implement `AiProviderOpenAI` + Grammar action.
3. Add templates CRUD.
4. Enable IME via CLI + adb.
5. Add paywall/credit metering.
6. Wire all AI actions.

