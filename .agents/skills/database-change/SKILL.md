---
name: database-change
description: Reusable procedure for proposing, validating, and applying non-destructive database changes in CineBook.
---

# Database Change Skill

Use this procedure when a backend feature requires schema additions or index adjustments.

## Required Documentation to Inspect First
- Schema Reference: `docs/database.md`
- Business Rules: `docs/business-rules.md`
- Rules: `.agents/rules/database.md`

## Procedure

### Step 1: Gap Identification
1. Inspect `docs/database.md` and the existing JPA entity classes.
2. Verify whether existing columns, relationships, or enums can satisfy the requirement.
3. Check for existing unique constraints (`uk_*`) and foreign keys (`fk_*`).

### Step 2: Proposal & Safety Check
1. Formulate the smallest safe change (e.g. adding a nullable column, new index, or new relation table).
2. **Hard Safety Constraints**:
   - Primary key for entities must be UUID (`varchar(36)`).
   - Never propose `DROP TABLE`, `TRUNCATE`, or column deletion.
   - For aggregate roots with concurrent updates, ensure `version` column is included.
3. If the change is structural or potentially destructive, request developer review and approval before proceeding.

### Step 3: Implementation & Alignment
1. Update JPA Entity classes (fields, `@Column`, `@JoinTable`, `@Version`).
2. Update corresponding DTOs, Mappers, and Service logic.
3. Update `docs/database.md` with the new schema definition.
4. Run full test suite: `.\mvnw.cmd clean test`.