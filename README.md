# myFitness Police: Task Tracker

The planning and delivery board for **myFitnessPolice**, an Android-first strength and fitness tracking app with a police-inspired visual theme.

This project organizes feature requests, bugs, usability improvements, and technical work. Our immediate focus is reliable workout data collection and an efficient experience before, during, and after training.

## App areas

| Area | Purpose |
|---|---|
| Dispatch | Dashboard, workout statistics, and insights |
| Academy | Exercise library, reusable workouts, and training plans |
| Active Workout | Set logging, timers, equipment setups, notes, and session controls |
| Reports | Workout history, data corrections, and progress reporting |
| Field | Planned cardio, distance, and activity tracking |
| Armory | Tools, learning resources, and support |

Supporting work includes database design, architecture documentation, catalog administration, and future web features.

## How to use this project

1. **Capture the work**
   Create or link a GitHub issue for a feature, bug, or technical task. Describe the problem, desired outcome, affected app areas, and acceptance criteria.

2. **Define the scope**
   Add dependencies and relevant Wiki links. For bundled work, include a checklist of the original backlog items, such as P1-01 through P1-07.

3. **Prioritize**
   Assign a priority and move the issue to Ready when its scope is clear and its dependencies are satisfied.

4. **Track implementation**
   Move the issue through the workflow. Link related pull requests, design decisions, screenshots, and validation results.

5. **Verify and complete**
   Move the issue to Done when its acceptance criteria are met and relevant documentation is updated.

For bugs, include reproduction steps, expected and actual behavior, and relevant device or orientation details.

## Workflow

| Status | Meaning |
|---|---|
| Backlog | Captured work that still needs refinement, prioritization, or dependencies |
| Ready | Clearly scoped and available to start |
| In progress | Actively being implemented |
| In review | Implementation is ready for review and validation |
| Done | Acceptance criteria have been met and the work has been verified |

## Priorities

- **P1 — Core workflow:** Data accuracy, record correction, reliable logging, and essential usability.
- **P2 — Workflow expansion:** Workout reuse, timing options, planning, catalog management, and performance.
- **P3 — Product expansion:** Additional activity tracking, AI assistance, web tools, community features, and supporting content.

Dependencies may require a supporting task to be completed ahead of a higher-priority feature.

## Working principles

- Templates describe intended training; sessions record what actually happened.
- Editing a template must not silently rewrite completed workout history.
- Protect existing data when changing the database.
- Keep data entry practical during a workout.
- Verify affected layouts in portrait and landscape.
- Keep actionable work in issues and reusable specifications and decisions in the Wiki.

## Important resources

- [Source repository](REPOSITORY_URL)
- [Issue tracker](ISSUES_URL)
- [Project board](PROJECT_URL)
- [Project Wiki](WIKI_URL)
- [Backlog and implementation strategy](BACKLOG_WIKI_URL)
- [Data model, ERD, and table documentation](DATA_MODEL_WIKI_URL)
- [Architecture and C4 diagrams](ARCHITECTURE_WIKI_URL)
