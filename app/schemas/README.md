# Database schema history

Room exports versioned JSON schemas here during builds. Commit these files.
Version 1 contains exercises, workouts, workout_exercises, and workout_sets.
Version 2 adds the set modifier. Version 3 adds target muscles, day, and training plan.
Version 4 distinguishes draft, plan, and session records using workouts.kind.
Version 10 adds nullable workout_sets.rpe for recorded exertion ratings from 1 to 10.
Existing sets retain all data and start with no recorded RPE.
Version 11 adds training_plans.dayOfWeek and workouts.sourceTrainingPlanId.
Starting training snapshots the included workouts in order into an independent
session, initially ready with stopped timers. The first set's Start action starts
both duty and set timing. Existing session states and historical data are preserved.

Saving promotes a draft to a plan without a completion timestamp. Starting a plan
copies its exercises and sets into a new session with independent IDs and a
sourcePlanId. Only completed session records appear in History. Clearing the
Create form removes drafts only. Changes to a session never rewrite its plan.

The version 3 to 4 migration preserves existing completed sessions in History and
creates separate plan copies to retain the home cards exposed by the older app.
Existing unfinished records become creation drafts. Migrated plan IDs use a
plan- prefix; new records use UUIDs.

For every schema change, increment FitnessDatabase's version, implement and register
a Migration (or a suitable Room AutoMigration), and test upgrading a populated
database. Never use fallbackToDestructiveMigration for user workout data.

IDs are UUID strings; timestamps are UTC epoch milliseconds; external weights are
integer grams (zero is valid); positions are zero-based and unique within their
parent. Archive catalog exercises to preserve history. Deleting a workout cascades
to its exercise entries and sets. Kotlin constructors validate values; relationships
and position uniqueness are also enforced by SQLite.

Seed exercises are inserted only when the database is created. Editing or archiving
them is preserved when the app restarts. Screens observe Room through the repository.
