## Summary

<!-- What changed and why. -->

## Checklist

- [ ] Any new logging is gated behind `__DEV__` (via `debugLog()`, mobile) or a dev-only flag, or has a `DIAG(YYYY-MM-DD)` removal date — no bare `console.log` (see `mobile/.eslintrc.js`)
- [ ] `npm run verify` passes in `mobile/` (lint, theme-color check, DIAG-marker expiry check)
- [ ] `mvn test` passes on the backend, with new tests for new behavior
- [ ] No JPA entity is bound directly to a `@RequestBody` or returned directly from a controller — use a DTO
- [ ] Schema changes are additive/backward-compatible Flyway migrations; destructive changes called out explicitly in the PR description
