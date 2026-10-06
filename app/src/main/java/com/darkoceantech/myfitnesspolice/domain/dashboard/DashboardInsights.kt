package com.darkoceantech.myfitnesspolice.domain.dashboard

data class ScienceReference(val citation: String, val url: String)
data class DashboardInsight(val formula: String, val purpose: String, val evidence: String,
    val limitations: String, val references: List<ScienceReference>)

/** Versioned, offline-readable explanations. Citation IDs identify separate studies, not aliases. */
object DashboardInsights {
    private val progressive = ScienceReference("Kassiano W et al. Progressive Overload Affects the Magnitude of Muscle Hypertrophy. " +
        "Med Sci Sports Exerc. 2026;58(7):1556–1565. DOI: 10.1249/MSS.0000000000003968. PMID: 41718594.", "https://pubmed.ncbi.nlm.nih.gov/41718594/")
    private val progression = ScienceReference("Chaves TS et al. Effects of Resistance Training Overload Progression Protocols on Strength and Muscle Mass. " +
        "Int J Sports Med. 2024;45(7):504–510. DOI: 10.1055/a-2256-5857. PMID: 38286426.", "https://pubmed.ncbi.nlm.nih.gov/38286426/")
    private val tonnage = ScienceReference("Hammert WB et al. Progression of total training volume in resistance training studies and its application to skeletal muscle growth. " +
        "Physiol Meas. 2024;45(8). DOI: 10.1088/1361-6579/ad7348. PMID: 39178897.", "https://pubmed.ncbi.nlm.nih.gov/39178897/")
    private val sets = ScienceReference("Baz-Valle E et al. A Systematic Review of The Effects of Different Resistance Training Volumes on Muscle Hypertrophy. " +
        "J Hum Kinet. 2022;81:199–210. DOI: 10.2478/hukin-2022-0017. PMID: 35291645.", "https://pubmed.ncbi.nlm.nih.gov/35291645/")
    private val fatigue = ScienceReference("Agudo-Ortega A et al. Acute neuromuscular fatigue in circuit strength training: effects of varying work-to-rest durations. " +
        "Front Sports Act Living. 2026;8:1761646. DOI: 10.3389/fspor.2026.1761646. PMCID: PMC12912757.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12912757/")
    private val circuit = ScienceReference("Agudo-Ortega A et al. Exploring acute physiological adaptations to circuit strength training: the role of work-to-rest interval variations. " +
        "Sci Rep. 2026;16:4884. DOI: 10.1038/s41598-025-34940-1. PMCID: PMC12873116.", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12873116/")
    private val loads = ScienceReference("Plotkin D et al. Progressive overload without progressing load? The effects of load or repetition progression on muscular adaptations. " +
        "PeerJ. 2022;10:e14142. DOI: 10.7717/peerj.14142. PMID: 36199287; PMCID: PMC9528903.", "https://pubmed.ncbi.nlm.nih.gov/36199287/")
    private val reliability = ScienceReference("Grgic J, Lazinica B, Schoenfeld BJ, Pedisic Z. Test–Retest Reliability of the One-Repetition Maximum (1RM) Strength Assessment: a Systematic Review. " +
        "Sports Med Open. 2020;6:31. DOI: 10.1186/s40798-020-00260-z. PMID: 32681399.", "https://pubmed.ncbi.nlm.nih.gov/32681399/")

    val dataContract = "Uses completed sets with saved results, including active sessions once their after-set form is saved. " +
        "Unfinished sets and results still awaiting confirmation are excluded. Legacy logs without actual reps use the recorded rep field. " +
        "Sessions belong to their local start date; weeks run Monday–Sunday. Selecting a date updates daily charts and the weekly/monthly window. " +
        "Weights are recorded external load in Lbs; body mass, movement distance and mechanical work are not estimated. " +
        "Warm-up sets contribute to load and time charts but are excluded from working-set, intensity, e1RM, PR and rep-range totals. " +
        "Recorded breaks include the final cool-down. Missing timing is not inferred from wall-clock session duration. " +
        "A missing baseline is unknown, never zero intensity. Grouped logs retain their original set records, counted once."
    val baselineContract = "Intensity uses the highest earlier Epley estimate for the same exercise from weighted working sets of 1–10 actual reps " +
        "in the preceding 90 days, before the selected set’s calendar day. A single rep uses the recorded load. " +
        "The current set cannot raise its own baseline. This is an estimated comparison ceiling, not a measured personal 1RM or a safety limit."

    fun forChart(id: String): DashboardInsight = when (id) {
        "weekly-volume" -> DashboardInsight("Sum actual reps × external weight for every completed set. Each stored row is one set; do not multiply by its set number. Units: Lbs-reps.",
            "Compare logged volume load across eight trailing weeks. Compare similar exercise selection and technique before interpreting a change.",
            "Kassiano et al. found greater triceps thickness gains with load progression in untrained young women. The separate 2024 trial found load and rep progression both improved strength and muscle cross-sectional area in novices. Neither study makes tonnage a direct measurement of muscle growth.",
            "More tonnage can reflect different lifts, warm-ups or technique. External volume load is not mechanical work or a hypertrophy score; the 2024 review cautions against treating its progression as a causal growth measure.", listOf(progressive, progression, tonnage))
        "weekly-muscles" -> DashboardInsight("Count completed non-warm-up sets per Monday–Sunday week. Assign each set to the exercise catalog’s main muscle group, once.",
            "Review where working sets are distributed. Secondary muscles are not awarded additional sets, keeping stacks additive and avoiding double counting.",
            "The 2022 systematic review discusses roughly 12–20 weekly sets in young trained men, with muscle-specific and study limitations. Treat the requested 10–20 range as planning context, not an optimum or physiological fatigue threshold for everyone. The tonnage review is not a working-set safety guideline.",
            "A completed working set is not necessarily a hard set near failure. Compound lifts train multiple muscles; one main group is a reporting convention. No automatic fatigue or volume-limit diagnosis is made.", listOf(sets, tonnage))
        "weekly-density" -> DashboardInsight("Bars = (recorded active milliseconds + recorded break milliseconds) / 60,000. Line = active / break. No break time gives an undefined ratio, shown as missing.",
            "Compare recorded training time with its work/rest balance without folding pauses into lifting time.",
            "The two circuit studies varied interval durations while keeping a 1:1 ratio. They are separate articles. Duration and protocol context matter; the ratio by itself cannot establish a capacity drop or recovery quality.",
            "Timing reflects button actions, not physiological measurements. Shorter breaks are not automatically better. Unlogged transitions and pauses are outside these bars.", listOf(fatigue, circuit))
        "weekly-intensity" -> DashboardInsight("Count working sets by external weight / earlier estimated 1RM: Low <60%; Medium 60–85% inclusive; High >85%. Missing reference or zero external load = Unknown.",
            "Review this week’s relative loading distribution. Unknown sets remain visible in the denominator.",
            "Plotkin et al. compared load progression with repetition progression over eight weeks. Both supported adaptations; small differences in rectus femoris growth and dynamic strength were uncertain. It did not validate these exact zone boundaries.",
            baselineContract + " These boundaries are descriptive programming bins; a donut cannot prove that a periodization plan is optimal.", listOf(loads))
        "daily-fatigue" -> DashboardInsight("For the selected lift, order completed sets chronologically. Left axis = external load (Lbs); right axis = actual repetitions.",
            "Spot changes across a day’s sets and compare them with notes, set type, exercise setup and intended progression.",
            "Acute circuit studies provide context for work/rest and performance changes. Weight and rep traces alone do not measure internal neuromuscular fatigue or mechanical breakdown.",
            "Warm-ups, drop sets, supersets and deliberate load reductions can produce the same visual drop-off. This is a descriptive log, not a stamina-failure diagnosis.", listOf(fatigue))
        "daily-time" -> DashboardInsight("Sum stored active lifting time and stored rest break time for completed sets on the selected date. Display each share of the recorded sum.",
            "Understand how recorded training minutes are allocated. Rest is part of training and recovery, not inherently non-productive time.",
            "Circuit work/rest duration influences acute physiological responses in controlled protocols. These results do not define a universally optimal lifting/rest split.",
            "Pauses, travel, warm-up activity outside the set timer and unrecorded time are excluded. Older records with no timing show an empty state rather than inferred slices.", listOf(circuit))
        "daily-intensity" -> DashboardInsight("External load per chronological set of the selected lift. A dashed reference line shows its earlier estimated 1RM when available.",
            "Compare prescribed loading choices within one movement. The visible reference is a comparison aid.",
            "The load/repetition progression trial supports multiple progression approaches; it does not establish a safety ceiling from a chart.",
            baselineContract + " A load above an estimate does not prove unsafe technique, and a load below it does not establish safety.", listOf(loads))
        "daily-rest" -> DashboardInsight("Plot stored break milliseconds / 1,000 after each chronological completed set. A finished session can have a recorded zero-second break; an unresolved current break is missing.",
            "Compare actual breaks with your intended pacing. Final cool-down and inter-exercise transitions are included where recorded.",
            "Controlled circuit studies show that interval duration matters alongside the work/rest ratio. The chart does not measure discipline or physical decline.",
            "Notes, equipment changes and interruptions can explain variation. Live breaks are not final until recorded by starting the next set or finishing.", listOf(circuit))
        "monthly-1rm" -> DashboardInsight("Epley estimate = weight × (1 + actual reps / 30). Use weighted working sets of 2–10 reps; singles use their recorded load. Plot the best eligible estimate per day.",
            "Track a consistent exercise’s estimated strength trend in the selected calendar month.",
            "The cited review concerns repeatability of direct 1RM tests. Reliability coefficients for tested maxima do not validate an Epley estimate or guarantee ICC >0.91 for every lift and population.",
            "Estimates depend on proximity to failure, setup and technique. Higher rep and bodyweight sets are excluded. This is not a measured maximum or an instruction to attempt one.", listOf(reliability))
        "monthly-pr" -> DashboardInsight("For the selected exercise, compare each completed working set with all earlier records. Mark dates with a new maximum weight or actual reps × weight. The first positive record establishes a baseline.",
            "Show dated performance milestones without comparing unrelated lifts or redefining a warm-up as a PR.",
            "These PR rules are transparent application definitions. They are not a validated psychological reinforcement intervention or an independent measure of adaptation.",
            "A scatter point shows load on a milestone day; rep-volume PRs can occur at lower load. Corrections and deleted logs can change the timeline. Equipment and technique still matter.", emptyList())
        "monthly-adherence" -> DashboardInsight("Unique completed plan/date matches ÷ explicitly scheduled plan/date slots × 100. Count each slot once and exclude future dates. Weekday preferences do not create scheduled slots.",
            "Compare completed routines with saved calendar appointments for the selected month through the selected date.",
            "This is a behavioral reporting definition, not a physiological measure. There is no claimed clinical validation for this app’s adherence score.",
            "Changing scheduled dates or deleting a plan alters the denominator; prior schedule revisions are not retained. Unscheduled sessions remain in other charts but do not increase scheduled adherence. A finished session with no performed sets does not fulfill a slot.", emptyList())
        "monthly-variety" -> DashboardInsight("Count completed working sets by actual repetitions: 1–5, 6–12, or 13+. Zero-rep attempts are excluded. Radar axes show absolute set counts on a shared scale.",
            "Review the month’s rep-range variety. The Strength, Hypertrophy and Endurance labels are programming shorthand.",
            "Adaptations overlap across rep ranges. The progression trial found both load and rep strategies useful; the three bins do not partition biological outcomes cleanly.",
            "A balanced polygon is not a recommendation. Goals can legitimately prioritize one range; load, effort, technique and total dose also matter.", listOf(loads))
        else -> error("Unknown dashboard chart: $id")
    }
}
