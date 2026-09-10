-- ========================================================================
-- PHẦN: MIGRATION V1_4 (Phase Localization: Independent Movie Manual Overrides)
-- File: src/main/resources/db/migration/V1_4__add_movie_manual_overrides.sql
-- ========================================================================

ALTER TABLE movies 
ADD COLUMN title_manual_override BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN overview_manual_override BOOLEAN NOT NULL DEFAULT FALSE;
