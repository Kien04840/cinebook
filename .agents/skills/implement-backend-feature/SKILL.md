---
name: implement-backend-feature
description: Reusable procedure for designing, implementing, and verifying a backend feature in CineBook.
---

# Implement Backend Feature Skill

Use this procedure when developing a new service, controller, integration, or business feature.

## Required Documentation to Inspect First
- Architecture: `docs/architecture.md`
- Target Domain: `docs/use-cases/{domain}.md` (e.g., `movie.md`, `authentication.md`)
- Schema Reference: `docs/database.md`
- Business Rules: `docs/business-rules.md`
- API Reference: `docs/api.md`
- Rules: `.agents/rules/backend.md`, `.agents/rules/security.md`

## Procedure

### Step 1: Inspection & Discovery
1. Inspect the relevant entities and repository methods.
2. Inspect existing DTOs, mappers, and controller conventions in analogous modules.
3. Identify existing exception classes (`AppException` hierarchy).

### Step 2: Implementation Order (Bottom-Up)
1. **Repository / Entities**: Reuse existing entities and Spring Data methods. Add custom JPQL/query only if necessary.
2. **DTOs & Mappers**: Create request/response DTOs with `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` and Jakarta validation annotations (`@NotBlank`, `@NotNull`, etc.). Implement manual mapping in a Spring bean mapper.
3. **Service Layer**: Implement service interface and implementation. Apply `@Transactional` explicitly on write operations. Enforce all domain invariants from `docs/business-rules.md`.
4. **Controller Layer**: Expose endpoint under `/api/v1/...` with `@Valid` on request bodies and correct `@Operation` OpenAPI annotations. Enforce RBAC security via `@SecurityRequirement(name = "bearerAuth")` and `SecurityConfig`.

### Step 3: Testing & Verification
1. Create unit tests for Service using Mockito (`@ExtendWith(MockitoExtension.class)`).
2. Create unit tests for Controller using `MockMvcBuilders.standaloneSetup()`.
3. If security or integration behavior is involved, verify with `@SpringBootTest`.
4. Run regression suite: `.\mvnw.cmd clean test`.