---
name: cinebook-authentication
description: Apply when implementing, modifying, testing, or reviewing CineBook authentication and authorization features.
---

# CineBook Authentication Context

When working on authentication or authorization, read and follow:

@../../docs/api.md
@../../docs/use-cases/authentication.md
@../../docs/architecture.md

Use the existing:
- User
- Role
- UserRole
- RefreshToken
- PasswordResetToken
entities and repositories.

Do not invent authentication behavior that is not defined by the referenced documentation.

If the documentation contains a TODO, TBD, or unresolved decision that materially affects implementation, inspect the existing code first and ask the developer only when the ambiguity cannot be safely resolved.

For implementation:
1. Inspect existing authentication/security code.
2. Inspect relevant entities and repositories.
3. Compare current code with API and authentication documentation.
4. Implement the smallest complete solution.
5. Run verification.
6. Review the final diff.

Do not modify unrelated modules.