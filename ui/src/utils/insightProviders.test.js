import { describe, it, expect } from 'vitest'
import {
  PROVIDERS, CUSTOM, providerIdFor, providerById, applyProviderChoice
} from './insightProviders'

const GEMINI = 'https://generativelanguage.googleapis.com/v1beta/openai'

describe('providerIdFor', () => {
  it('recognises every provider it offers — a preset the form cannot resolve is a preset you can never come back to', () => {
    for (const provider of PROVIDERS) {
      expect(providerIdFor(provider.baseUrl)).toBe(provider.id)
    }
  })

  it('resolves the seeded default, which is the URL every fresh install starts on', () => {
    expect(providerIdFor(GEMINI)).toBe('gemini')
  })

  it('falls back to Custom for a URL it does not know, rather than guessing at the nearest one', () => {
    expect(providerIdFor('https://llm.internal.example/v1')).toBe(CUSTOM)
    expect(providerIdFor('')).toBe(CUSTOM)
  })

  it('treats a trailing slash as unknown — the stored URL is shown as typed, never rewritten', () => {
    expect(providerIdFor(`${GEMINI}/`)).toBe(CUSTOM)
  })
})

describe('applyProviderChoice', () => {
  it('writes the chosen provider base URL', () => {
    expect(applyProviderChoice('openai', '').baseUrl).toBe('https://api.openai.com/v1')
  })

  it('replaces a model the new provider does not know', () => {
    // Saving Gemini's model against OpenAI stores a pair that can only fail at generation time.
    const { model } = applyProviderChoice('openai', 'gemini-3.5-flash')
    expect(model).toBe(providerById('openai').models[0])
  })

  it('keeps a model the provider does know, so re-picking your current provider changes nothing', () => {
    const { baseUrl, model } = applyProviderChoice('gemini', 'gemini-3.5-flash')
    expect(baseUrl).toBe(GEMINI)
    expect(model).toBe('gemini-3.5-flash')
  })

  it('returns null for Custom — there is nothing to fill in, only a field to reveal', () => {
    expect(applyProviderChoice(CUSTOM, 'anything')).toBeNull()
  })
})

describe('the provider table', () => {
  it('gives every provider at least one model, since the first is what a switch falls back to', () => {
    for (const provider of PROVIDERS) {
      expect(provider.models.length).toBeGreaterThan(0)
    }
  })

  it('keeps the seeded default among Gemini models, so an existing install is never silently switched', () => {
    expect(providerById('gemini').models).toContain('gemini-3.5-flash')
  })

  it('explains the blank-key trap on Ollama, which needs a key it will then ignore', () => {
    expect(providerById('ollama').note).toBeTruthy()
  })
})
