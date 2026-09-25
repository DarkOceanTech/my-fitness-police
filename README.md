# myFitnessPolice

**Plan your training. Log your work. Track your progress.**

myFitnessPolice is an Android-first strength and fitness tracking app built around reusable workouts, training plans, and detailed session logging.

The app combines a police-inspired visual theme with practical tools for recording sets, weight, repetitions, effort, equipment settings, and workout history.

**Status:** Active development / prototype.

[Project Board](https://github.com/orgs/DarkOceanTech/projects/3) ·
[Wiki](https://github.com/DarkOceanTech/my-fitness-police/wiki) ·
[Report a Bug or Suggest a Feature](https://github.com/DarkOceanTech/my-fitness-police/issues)

## What the app does

The current focus is making strength-training data easy to capture accurately during a workout and useful to review afterward.

Current functionality includes:

- An exercise catalog with descriptions, equipment, and target muscles.
- Reusable workouts containing exercises and prescribed sets.
- Training plans that combine workouts in a chosen order.
- Planned weight and repetitions, actual repetitions, and optional RPE.
- Active-set, break, and overall workout timers.
- Pause/resume controls and a final workout cool-down.
- Exercise and set notes.
- Equipment position information.
- Workout history with recorded-set details and corrections.
- Dashboard charts based on recorded workout data.
- Portrait and landscape workout layouts.

## App navigation

| Tab | Purpose |
|---|---|
| **Dispatch** | Dashboard with sets completed, reps performed, weight lifted, and activity time |
| **Academy** | Exercise library, reusable workouts, and training plans |
| **Field** | Planned indoor and outdoor activity tracking |
| **Reports** | Workout history and progress reporting |
| **Armory** | Tools, learning resources, and support |

Field and Armory currently include under-construction features. The settings screen is an interactive demonstration; its controls do not yet save or apply preferences.

## How to use the workout flow

1. **Explore exercises**
   Open Academy and browse or filter the exercise catalog.

2. **Create a workout**
   Add exercises and configure their sets, weight, repetitions, and set types. Save the workout for reuse.

3. **Create a training plan**
   Include the workouts you want to perform, arrange their order, and assign a day of the week.

4. **Start training**
   Open the plan and select **Start training!** The session opens ready to begin; press Start on the first set to start timing.

5. **Record your sets**
   Complete each set and enter the actual repetitions performed. Add RPE and notes when useful.

6. **Review your results**
   Completed sessions appear under **Reports → Workout History**. Review recorded sets, notes, and timing, and correct supported fields when necessary.

## Core concepts

| Concept | Meaning |
|---|---|
| **Exercise** | A movement in the catalog, with information such as equipment and target muscles |
| **Workout** | A reusable collection of exercises and prescribed sets |
| **Training plan** | An ordered group of workouts to perform together |
| **Session** | An actual training occurrence, including performed sets, results, notes, and timing |

Templates describe intended training. Sessions record what actually happened.

Starting training creates a separate session. Changes to reusable workout definitions should not silently rewrite completed workout history.

## Technology

- **Platform:** Android
- **Language:** Kotlin
- **User interface:** Jetpack Compose and Material 3
- **State management:** ViewModels and Kotlin Flow
- **Local persistence:** Room with SQLite
- **Build system:** Gradle with Kotlin DSL
- **Testing:** JUnit, Android instrumentation, and Compose UI tests

Azure Table Storage is a planned storage direction. Azure SQL is being considered for future analytics. The current app uses local Room/SQLite persistence.

## Getting started with development

### Requirements

- Git.
- Android Studio compatible with the Android Gradle Plugin version specified in `gradle/libs.versions.toml`.
- Android SDK Platform **37**, as configured by the project.
- JDK **25** for the configured Gradle daemon toolchain.
- An emulator or Android device running **API 24 or later**.

Build and toolchain versions are defined in the repository’s Gradle configuration.

### Clone and open the project

```bash
git clone https://github.com/DarkOceanTech/my-fitness-police.git
cd my-fitness-police
```

1. Open the repository folder in Android Studio.
2. Allow Gradle to sync.
3. Install any SDK components requested by the project.
4. Select an emulator or connected Android device.
5. Run the `app` configuration.

### Build from the command line

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug
```

The debug APK is generated under:

```text
app/build/outputs/apk/debug/
```

### Run tests

Local unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Android instrumentation and UI tests require a running emulator or connected test device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

## Repository structure

```text
app/
├── schemas/                 Exported Room schemas and database documentation
└── src/
    ├── main/
    │   ├── java/            Kotlin application code
    │   │   └── com/darkoceantech/myfitnesspolice/
    │   │       ├── data/    Database, entities, repositories, and data logic
    │   │       └── ui/      Screens, UI state, and theme
    │   └── res/             Android resources
    ├── test/                Local unit tests
    └── androidTest/         Android instrumentation and UI tests

gradle/                      Dependency catalog, wrapper, and toolchain configuration
```

## Data and database changes

Workout history is a core part of the app.

When changing persistence:

- Preserve existing recorded sessions.
- Add and test database migrations.
- Keep exported Room schemas under version control.
- Keep planned values distinct from performed results.
- Verify that saved values survive reopening the app.
- Avoid destructive database resets as a migration strategy.

See the [database schema documentation](app/schemas/README.md) for existing conventions and migration details.

## Roadmap

Development priorities include:

- More efficient exercise selection and set entry.
- Better correction of recorded workout data.
- Reusable gym and equipment setups.
- Saving live workout changes back to templates.
- Reusable Set Groups and rerunning historical workouts.
- Workout strategy, session focus, and richer notes.
- Timed exercises and movement-phase timing.
- Weekly and monthly training-plan views.
- Exercise catalog maintenance and restoration.
- Field activity tracking, learning resources, and future web tools.

The Wiki and project board contain the detailed scope, priorities, and implementation sequence.

## Feedback and participation

Bug reports and feature suggestions are welcome.

Before opening an issue, check the existing issues and Master Backlog for related work.

For a **bug report**, include:

- Steps to reproduce the problem.
- Expected and actual behavior.
- App version, device, and Android version when available.
- Screen orientation and relevant screenshots.
- Whether recorded workout data was affected.

For a **feature suggestion**, describe the need, the affected app area, and a practical example of how you would use it.

Public participation is currently focused on submitting issues and suggesting features.

[Open an issue](https://github.com/DarkOceanTech/my-fitness-police/issues)

## Project resources

- [Task Tracker](https://github.com/orgs/DarkOceanTech/projects/3)
- [Project Wiki](https://github.com/DarkOceanTech/my-fitness-police/wiki)
- [Master Backlog](https://github.com/DarkOceanTech/my-fitness-police/wiki/Master-Backlog)
- [Implementation Strategy](https://github.com/DarkOceanTech/my-fitness-police/wiki/Implementation-Strategy)
- [Issue Tracker](https://github.com/DarkOceanTech/my-fitness-police/issues)
