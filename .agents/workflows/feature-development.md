# Feature Development Workflow

This workflow guides the end-to-end development cycle for features in the CineBook project, ensuring high accuracy, consistent architecture, and zero regression.

```text
Inspect & Scope
      ↓
Plan & Approve (if non-trivial)
      ↓
Implement (Bottom-Up)
      ↓
Verify (Unit & Integration Tests)
      ↓
Review & Report
```

---

## 1. Inspect
- Consult `docs/documentation-map.md` to identify required documents for the task.
- Read existing entity, repository, service, and controller implementations in the target domain.
- Check relevant unit and controller tests to follow established patterns.
- Do not make assumptions about conventions without inspecting code first.

## 2. Scope & Plan
- Define the minimal required changes to meet acceptance criteria.
- Identify reusable components, existing DTOs, and exception types.
- For non-trivial tasks (multiple classes, architectural boundary, schema addition):
  - Formulate a clear implementation plan.
  - Highlight any ambiguity or necessary technical decisions.
  - Obtain user approval before executing code changes.

## 3. Implement
- Use bottom-up implementation order:
  1. Repository / Entities (managed JPA mapping).
  2. DTOs & Mappers (Jakarta validation annotations).
  3. Service Layer (business logic, transaction boundaries, domain invariant checks).
  4. Controller Layer (request mapping, OpenAPI annotations, HTTP status codes).
  5. Security configuration (if new path rules are needed).
- Follow relevant rules in `.agents/rules/` (`backend.md`, `security.md`, `database.md`).

## 4. Verify
- Write comprehensive unit tests for Service logic and Controller endpoints.
- Ensure all business invariants, error cases (400, 401, 403, 404, 409), and idempotency paths are tested.
- Run complete test suite:
  ```powershell
  .\mvnw.cmd clean test
  ```
- For frontend tasks, execute browser verification covering navigation, form validation, and error states.

## 5. Review & Report
- Inspect final `git diff` to ensure:
  - No unintended file modifications or debug statements.
  - No exposed secrets or hard-coded credentials.
  - No broken imports or missing Javadoc / comments for complex invariants.
- Present a concise report in Vietnamese covering files modified, endpoints created, and verification results.