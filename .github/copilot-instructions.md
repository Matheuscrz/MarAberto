# Project Guidelines

## Documentation and scope

- Read [Contributing.md](../Contributing.md), [docs/Requirements.md](../docs/Requirements.md), and [docs/Architecture.md](../docs/Architecture.md) before making changes.
- Keep planning notes in `docs/` (Architecture, Requirements, ADRs and the roadmap in `AGENTS.md`); do not commit personal scratch files.
- Do not add secrets, real personal data, or production credentials to the repository.

## Safety-sensitive behavior

- Treat SOS, authentication, authorization, privacy, location, and audit changes as sensitive.
- Preserve idempotency, persistence before notification, explicit authorization, and auditability in those areas.
- The application supports emergency response but never replaces the official emergency number 193.

## Contributions

- AI-assisted contributions are welcome. Contributors remain responsible for reviewing generated code, its licenses, tests, security, and behavior.
- Prefer small pull requests, tests for behavior changes, and Conventional Commit messages.
