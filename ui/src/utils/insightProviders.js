/**
 * The providers whose OpenAI-compatible base URL we know, and a few models each speaks.
 *
 * <p>The URLs are the stable half — an endpoint outlives the model names by years — so the
 * Settings form writes one of these verbatim while the model stays a free-text field with
 * these as suggestions. A model that isn't listed is typed, not blocked; a stale suggestion
 * costs one keystroke, which is what the field cost before there were any suggestions.
 *
 * <p>Model IDs verified against each provider's own documentation on 2026-08-17.
 */
export const PROVIDERS = [
  {
    id: 'gemini',
    label: 'Google Gemini',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
    models: ['gemini-3.7-flash', 'gemini-3.6-flash', 'gemini-3.5-flash', 'gemini-3.5-flash-lite']
  },
  {
    id: 'openai',
    label: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    models: ['gpt-5.6-luna', 'gpt-5.6-terra', 'gpt-5.6-sol']
  },
  {
    id: 'groq',
    label: 'Groq',
    baseUrl: 'https://api.groq.com/openai/v1',
    models: ['openai/gpt-oss-20b', 'openai/gpt-oss-120b']
  },
  {
    id: 'openrouter',
    label: 'OpenRouter',
    baseUrl: 'https://openrouter.ai/api/v1',
    models: [
      'anthropic/claude-sonnet-5',
      'anthropic/claude-opus-5',
      'openai/gpt-5.6-luna',
      'google/gemini-3.7-flash'
    ]
  },
  {
    id: 'ollama',
    label: 'Ollama (local)',
    baseUrl: 'http://localhost:11434/v1',
    models: ['llama3.1', 'llama3.2', 'qwen3', 'gemma3'],
    // InsightService treats a blank key as "insights off", so a provider that wants no key
    // still needs something typed into the field. Unexplained, that reads as a bug.
    note: 'Ollama ignores the key, but Atlas needs any non-empty value to turn insights on.'
  }
]

/** The id the provider select shows for a stored base URL. */
export const CUSTOM = 'custom'

/**
 * Which provider a stored base URL belongs to, or {@link CUSTOM} for one we don't know —
 * a proxy, a corporate gateway, a URL with a trailing slash. Deriving this instead of
 * storing it alongside the URL is what keeps the two from disagreeing, and what stops an
 * unrecognised URL being silently rewritten into one we do recognise.
 */
export function providerIdFor(baseUrl) {
  return PROVIDERS.find(p => p.baseUrl === baseUrl)?.id ?? CUSTOM
}

export function providerById(id) {
  return PROVIDERS.find(p => p.id === id)
}

/**
 * The base URL and model to store when the provider changes.
 *
 * <p>Keeps the current model when the chosen provider knows it, so re-picking the provider
 * you are already on never clobbers a hand-typed one; replaces it otherwise, because
 * carrying `gemini-3.5-flash` over to OpenAI stores a pair that can only fail at generation
 * time. Selecting Custom keeps both — there is nothing to fill in, only a field to reveal.
 */
export function applyProviderChoice(id, currentModel) {
  const provider = providerById(id)
  if (!provider) return null
  return {
    baseUrl: provider.baseUrl,
    model: provider.models.includes(currentModel) ? currentModel : provider.models[0]
  }
}
