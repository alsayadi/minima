# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Minima OS, please **do not** open a public GitHub issue.

Instead, report it privately via one of the following:

1. **GitHub Security Advisory** — preferred. Go to the [Security tab](https://github.com/alsayadi/minima/security/advisories) and click "Report a vulnerability".
2. **Email** — open an issue asking for a contact email if you need one.

Please include:

- A clear description of the issue
- Steps to reproduce
- Affected version / commit
- Any proof-of-concept code or screenshots

You can expect an initial response within 7 days. Coordinated disclosure timeline: 90 days or until a fix ships, whichever is sooner.

## Scope

In scope:

- The Android app in this repository
- Handling of user credentials (API keys) stored on-device
- Data sent to LLM providers
- Permission usage and privilege escalation

Out of scope:

- Vulnerabilities in third-party LLM providers (OpenAI, Anthropic, Groq, etc.) — report to the provider directly
- Vulnerabilities in Android itself
- Attacks requiring physical access to an unlocked device
- Social engineering against maintainers

## Known Considerations

- API keys are currently stored in plain-text `SharedPreferences`. Encrypted storage is on the roadmap. Users on rooted devices should be aware.
- Voice audio is sent to Google's `SpeechRecognizer` service (standard Android behavior, not a Minima-specific leak).
- Every user command is forwarded to the LLM provider the user selects, along with relevant memory context.
