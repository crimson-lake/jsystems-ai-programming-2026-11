---
applyTo: '**/*.ts,**/*.tsx'
description: 'General TypeScript typing rules (apply to Angular and React code alike); framework-specific rules live in nested AGENTS.md files'
---
Never use `any` and `as` in TypeScript projects.

Always use type annotations for variables, function parameters, and return types.
Use type guards to narrow down types and make them safe at execution time.

