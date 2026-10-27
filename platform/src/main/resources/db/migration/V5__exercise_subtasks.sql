-- Symmetry schema version 5: worker-published subtasks.
--
-- A worker who has accepted a task may decompose it and publish a subtask for
-- another worker. A subtask is an ordinary exercise row with a parent pointer;
-- it reuses the same source/stub/test/workspace pipeline and appears on the
-- public task board. The `$` price tier is derived from complexity, not stored.

ALTER TABLE exercise
    ADD COLUMN parent_exercise_id UUID NULL;

ALTER TABLE exercise
    ADD CONSTRAINT fk_exercise_parent FOREIGN KEY (parent_exercise_id)
        REFERENCES exercise (id) ON DELETE CASCADE;

ALTER TABLE exercise
    ADD CONSTRAINT chk_exercise_parent_not_self
        CHECK (parent_exercise_id IS NULL OR parent_exercise_id <> id);

CREATE INDEX idx_exercise_parent
    ON exercise (parent_exercise_id, status, published_at DESC);

COMMENT ON COLUMN exercise.parent_exercise_id IS
    'The accepted parent task this worker-published subtask belongs to; NULL for publisher tasks.';
