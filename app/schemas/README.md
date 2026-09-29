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
Version 12 adds workout_exercises.sourceWorkoutId and sourceWorkoutName snapshots
for sessions started from saved workouts or training plans. Earlier grouped sessions
retain null/empty source values; current plan membership is never used to infer history.
Version 13 adds nullable workouts.historyGroupId. Each explicit grouping selection
receives a new ID, so weekly repeats of the same plan remain separate history cards.
Existing records keep null IDs until selected for grouping; earlier selection
batches cannot be reconstructed reliably from their shared plan association.
Version 14 adds training_plan_exercises for catalog exercises included directly in
a training plan. Its composite key is (trainingPlanId, exerciseId); positions share
one order with training_plan_workouts. Each direct member owns a JSON list of set
prescriptions (reps, integer grams, warm-up flag, modifier). New direct members
default to one working set of 10 reps at zero external weight. Existing plans and
recorded history migrate unchanged; no hidden workout records are created.

Saving a mixed plan validates and replaces both member lists in one transaction.
Legacy workout-only saves preserve direct prescriptions. Starting training expands
the combined order into independent session entries, retaining a direct movement
even when the same catalog exercise also occurs inside an included workout. Direct
entries have no sourceWorkoutId; nested entries retain their saved-workout snapshots.
An included archived exercise remains available, but cannot be newly added. Deleting
a training plan cascades only its memberships, preserving exercises and session data.

Correcting a historical exercise updates its catalog association in place, preserving
its entry ID, sets, notes, equipment setup, and timing. Grouping recorded workouts
updates their training-plan ID/name and selection batch ID, without merging stored
sessions or modifying reusable workout prescriptions. A newly named plan starts
without reusable members. Each batch displays one card titled with the plan name,
with workouts ordered by recorded start time and exercises by recorded position.
History filters match whole cards if any member matches; cards retain complete
group totals. Corrections and deletion still target the original workout records.

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
