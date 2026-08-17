package com.joaosousa.atlas.dto;

/**
 * Why an insight response looks the way it does, as data rather than prose.
 *
 * <p>Before this existed the API said only "fallback: true/false" and the frontend
 * recovered the rest by string-matching the message text. That was survivable while
 * every failure came from one provider; with a user-supplied base URL the failure modes
 * multiply, and the difference between "you haven't set this up yet" and "something
 * broke" is the difference between a neutral empty state and a red error.
 *
 * <p>See the "Insights and providers" design note on the Saturn docs hub.
 */
public enum InsightState {

    /** A real generated insight. */
    OK,

    /** No API key configured. A fresh install's normal state, not an error. */
    NOT_CONFIGURED,

    /** Nothing answered at the configured base URL — wrong address, or provider is down. */
    UNREACHABLE,

    /** The provider answered with an error status. */
    PROVIDER_ERROR
}
