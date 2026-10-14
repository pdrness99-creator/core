# AI lyrics translation

For a DeepSeek key, select **Custom OpenAI Compatible** in SimpMusic's AI settings.

| Setting | DeepSeek configuration |
| --- | --- |
| Custom Base URL | `https://api.deepseek.com/v1/` |
| Custom AI model ID | `deepseek-flash` (check the provider's current model list) |
| API key | Your DeepSeek API key, entered only in the app |
| Custom headers | Leave empty for the official DeepSeek API |
| Use AI Lyric Translation | Enabled |
| Translation Language | `zh` for Chinese |

The track must have original lyrics. A DeepSeek key cannot authenticate against
the OpenAI or Gemini provider endpoints. Keys from a third-party gateway need that
gateway's base URL and model ID instead of the official DeepSeek settings above.

DeepSeek's Chat Completions endpoint accepts `response_format.type=json_object`,
not `json_schema`. The custom-provider path therefore uses JSON object mode and
validates that every requested line has a nonblank translation. The OpenAI and
Gemini paths retain their existing schema configuration.

Custom base URLs retain their path prefix, with or without a trailing slash. A
pasted `/chat/completions` endpoint is also accepted. Translated lines keep their
original timestamps; blank and instrumental lines are preserved and excluded
from the unchanged-lyrics check.

## Regression tests

`AiTranslationTest` exercises the production client and HTTP transport against a
loopback HTTP server using a dummy key. It does not call a paid API.

With this core revision checked out inside a compatible SimpMusic source tree:

```sh
./gradlew :aiService:jvmTest
```

Changing this core repository does not update an installed app. Build SimpMusic
with the repaired core revision, then install the resulting app. When using a
newer SimpMusic checkout, apply the AI changes to its matching core revision
instead of replacing the entire core submodule with an older revision.

Provider references (checked 2026-10-13):
- https://api-docs.deepseek.com/api/create-chat-completion/
- https://api-docs.deepseek.com/guides/json_mode/
- https://api-docs.deepseek.com/news/news260910/
