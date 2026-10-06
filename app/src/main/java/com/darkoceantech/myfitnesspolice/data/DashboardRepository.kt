package com.darkoceantech.myfitnesspolice.data

import com.darkoceantech.myfitnesspolice.domain.dashboard.*
import kotlinx.coroutines.flow.combine

/** Room is the source of truth; the dashboard domain never depends on Room or Compose. */
class DashboardRepository(repository: FitnessRepository) {
    val input = combine(repository.observeSessions(), repository.trainingPlans.observeAll(), repository.trainingSchedule.observeAll()) { workouts, plans, schedule ->
        DashboardInput(workouts.filter { it.workout.kind == "session" }.map { session ->
            DashboardSession(session.workout.id, session.workout.trainingPlan.ifBlank { session.workout.displayName() },
                session.workout.startedAt, session.workout.finishedAt, session.workout.sourceTrainingPlanId,
                session.workout.historyGroupId, session.orderedExercises().flatMapIndexed { exerciseIndex, entry ->
                    entry.sets.sortedBy { it.position }.filter { it.completedAt != null &&
                        !(session.sessionState?.awaitingActual == true && session.sessionState.currentSetId == it.id) }.map { set ->
                        DashboardSet(set.id, entry.exercise.id, entry.exercise.name, entry.exercise.mainMuscleGroup().label,
                            exerciseIndex * 100000 + set.position, set.completedAt!!, set.actualReps ?: set.reps,
                            set.weightGrams / 453.59237, set.isWarmup, set.activeMillis, set.restMillis)
                    }
                }, isTrainingPlan = session.workout.sourceTrainingPlanId != null || session.workout.trainingPlan.isNotBlank())
        }, plans.map { plan ->
            val children = plan.workouts(workouts)
            DashboardPlan(plan.plan.id, plan.plan.name, trainingWeekdays.indexOf(plan.plan.dayOfWeek).takeIf { it >= 0 }?.plus(1),
                plan.plan.createdAt, children.sumOf { it.plannedSets() } + plan.exerciseMembers.sumOf { it.member.sets.size },
                children.sumOf { it.plannedReps() } + plan.exerciseMembers.sumOf { it.member.sets.sumOf { set -> set.reps.toLong() } })
        }, schedule.map { DashboardSchedule(it.trainingPlanId, java.time.LocalDate.parse(it.scheduledDate)) })
    }
}
