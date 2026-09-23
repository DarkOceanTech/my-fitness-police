package com.darkoceantech.myfitnesspolice.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Curated catalog v1. IDs are permanent: never renumber or reuse them for a different movement.
 * Primary = major intended movers; secondary = assisting movers and selected stabilizers.
 * These are anatomical roles, not measured percentages or a ranking of hypertrophy potential.
 * Research references and naming conventions: app/docs/exercise-catalog.md.
 */
internal object ExerciseCatalog {
    private const val quadriceps = "Quadriceps femoris (rectus femoris, vastus lateralis, vastus medialis, vastus intermedius)"
    private const val hamstrings = "Biceps femoris; semitendinosus; semimembranosus"
    private const val hipHamstrings = "Biceps femoris (long head); semitendinosus; semimembranosus"
    private const val erectors = "Erector spinae (iliocostalis, longissimus, spinalis)"
    private const val cuff = "Rotator cuff (supraspinatus, infraspinatus, teres minor, subscapularis)"

    // number | name | equipment | description | primary muscles | secondary muscles
    val exercises: List<Exercise> = """
        1|Barbell squat|Barbell; weight plates; squat rack|With the bar across the upper back, bend the hips and knees, then extend both to stand.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); $erectors (stabilization)
        2|Bench press|Barbell; weight plates; flat bench; rack|Lying on a flat bench, lower the bar toward the chest and press upward while keeping the feet supported.|Pectoralis major; triceps brachii|Deltoid (anterior part); $cuff (stabilization)
        3|Deadlift|Barbell; weight plates|Lift the bar from the floor by extending the hips and knees; keep the bar close, then lower with control.|Gluteus maximus; $quadriceps; $hipHamstrings|$erectors (stabilization); latissimus dorsi (stabilization); flexor digitorum superficialis; flexor digitorum profundus (grip)
        4|Overhead press|Barbell; weight plates; rack|From the upper chest, press the bar overhead while keeping the trunk braced, then lower to the starting position.|Deltoid (anterior and middle parts); triceps brachii|Trapezius (upper and lower parts); serratus anterior; $cuff (stabilization)
        5|Dumbbell row|Dumbbell; flat bench|Support one hand on a bench and pull the dumbbell toward the hip with the other arm, keeping the torso steady.|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Deltoid (posterior part); teres major; biceps brachii; brachialis; brachioradialis
        6|Pull-up|Bodyweight; pull-up bar|Hang with an overhand grip, pull the body upward by bending the elbows and drawing the upper arms down, then lower.|Latissimus dorsi; teres major; biceps brachii; brachialis|Trapezius (middle and lower parts); rhomboid major; rhomboid minor; brachioradialis
        7|Bent-over barbell row|Barbell; weight plates|Hinge at the hips, hold the torso steady, and pull the bar toward the lower ribs before lowering under control.|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Deltoid (posterior part); teres major; biceps brachii; brachialis; $erectors (stabilization)
        8|Seated independent-arm machine row|Seated row machine with two independent handles|Seated machine pull-back: draw the handles toward the lower ribs. Row both arms together (bilateral) or one at a time (unilateral).|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Deltoid (posterior part); teres major; biceps brachii; brachialis; brachioradialis
        9|Seated close-grip cable row|Cable machine; seated row bench; close-grip neutral handle|Seated cable pull-back: keep the torso steady and pull the close-grip handle toward the abdomen, then allow a controlled return.|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Teres major; deltoid (posterior part); biceps brachii; brachialis; brachioradialis
        10|Lat pulldown|Cable pulldown machine; pulldown bar|With an overhand grip, pull the bar toward the upper chest by drawing the upper arms downward, then return overhead.|Latissimus dorsi; teres major|Biceps brachii; brachialis; brachioradialis; trapezius (lower part)
        11|Chin-up|Bodyweight; pull-up bar|Using an underhand grip, pull the body upward and then lower to a controlled hang without swinging.|Latissimus dorsi; biceps brachii; brachialis|Teres major; trapezius (middle and lower parts); rhomboid major; rhomboid minor
        12|Inverted row|Bodyweight; securely fixed low bar|Hang below a fixed bar with the body straight, pull the chest toward the bar, then straighten the elbows.|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Deltoid (posterior part); biceps brachii; brachialis; rectus abdominis (stabilization)
        13|Chest-supported dumbbell row|Dumbbells; incline bench|Rest the chest on an incline bench and row the dumbbells toward the sides of the ribs, then lower.|Latissimus dorsi; trapezius (middle part); rhomboid major; rhomboid minor|Deltoid (posterior part); teres major; biceps brachii; brachialis
        14|Straight-arm cable pulldown|Cable machine; straight bar attachment|Keep a small, fixed elbow bend and move the arms from overhead toward the thighs, then return slowly.|Latissimus dorsi; teres major|Pectoralis major (sternocostal part); triceps brachii (long head); rectus abdominis (stabilization)
        15|Dumbbell bench press|Dumbbells; flat bench|Lie on a flat bench, lower the dumbbells beside the chest, and press them upward with controlled arm motion.|Pectoralis major; triceps brachii|Deltoid (anterior part); $cuff (stabilization)
        16|Incline dumbbell press|Dumbbells; adjustable incline bench|On a moderately inclined bench, lower the dumbbells beside the upper chest and press upward.|Pectoralis major (clavicular part); deltoid (anterior part)|Triceps brachii; pectoralis major (sternocostal part); $cuff (stabilization)
        17|Push-up|Bodyweight; floor|With hands on the floor and the body aligned, bend the elbows to lower the chest, then press away from the floor.|Pectoralis major; triceps brachii|Deltoid (anterior part); serratus anterior; rectus abdominis (stabilization)
        18|Incline push-up|Bodyweight; stable bench or raised support|Place the hands on a secure raised surface and perform a push-up while keeping the trunk and hips aligned.|Pectoralis major; triceps brachii|Deltoid (anterior part); serratus anterior; rectus abdominis (stabilization)
        19|Standing cable fly|Dual cable machine; single handles|With slightly bent elbows, bring the arms together in front of the chest, then open them with control.|Pectoralis major|Deltoid (anterior part); coracobrachialis; $cuff (stabilization)
        20|Machine chest press|Seated chest press machine|Sit with the handles at chest level, press them forward, and return while keeping the back against the pad.|Pectoralis major; triceps brachii|Deltoid (anterior part)
        21|Seated dumbbell shoulder press|Dumbbells; upright bench|From shoulder level, press the dumbbells overhead, then lower them while keeping the torso supported.|Deltoid (anterior and middle parts); triceps brachii|Trapezius (upper and lower parts); serratus anterior; $cuff (stabilization)
        22|Dumbbell lateral raise|Dumbbells|Raise the arms out to the sides to approximately shoulder height with a slight elbow bend, then lower.|Deltoid (middle part); supraspinatus|Trapezius (upper and lower parts); serratus anterior
        23|Chest-supported reverse fly|Dumbbells; incline bench|With the chest supported, lift the arms outward with slightly bent elbows and gently retract the shoulder blades.|Deltoid (posterior part)|Trapezius (middle part); rhomboid major; rhomboid minor; infraspinatus; teres minor
        24|Cable face pull|Cable machine; rope attachment|Pull the rope toward the face while separating the hands and rotating the upper arms outward; return with control.|Deltoid (posterior part); infraspinatus; teres minor|Trapezius (middle and lower parts); rhomboid major; rhomboid minor; biceps brachii
        25|Dumbbell shrug|Dumbbells|With arms at the sides, elevate the shoulder blades and lower them without rolling the shoulders.|Trapezius (upper part); levator scapulae|Flexor digitorum superficialis; flexor digitorum profundus (grip)
        26|Barbell curl|Barbell; weight plates|With an underhand grip and the upper arms near the torso, bend the elbows to lift the bar, then lower.|Biceps brachii; brachialis|Brachioradialis; flexor carpi radialis; flexor carpi ulnaris (wrist stabilization)
        27|Dumbbell curl|Dumbbells|Keep the upper arms still and curl the dumbbells with the palms facing upward, then lower under control.|Biceps brachii; brachialis|Brachioradialis; flexor carpi radialis; flexor carpi ulnaris (wrist stabilization)
        28|Hammer curl|Dumbbells|Hold the palms facing inward and bend the elbows to raise the dumbbells, keeping the upper arms still.|Brachialis; brachioradialis; biceps brachii|Extensor carpi radialis longus; extensor carpi radialis brevis; extensor carpi ulnaris (wrist stabilization)
        29|Preacher curl|EZ curl bar; weight plates; preacher bench|Support the upper arms on the preacher pad and flex the elbows, then lower the bar without lifting the arms from the pad.|Biceps brachii; brachialis|Brachioradialis; flexor carpi radialis; flexor carpi ulnaris (wrist stabilization)
        30|Cable triceps pushdown|Cable machine; rope attachment|Keep the upper arms alongside the torso and straighten the elbows to push the rope downward, then return.|Triceps brachii|Anconeus
        31|Overhead cable triceps extension|Cable machine; rope attachment|With upper arms raised beside the head, extend the elbows against the cable and return to the bent-elbow position.|Triceps brachii|Anconeus; deltoid (stabilization)
        32|Close-grip bench press|Barbell; weight plates; flat bench; rack|Use a grip around shoulder width, lower the bar toward the chest, then press while keeping the wrists aligned.|Triceps brachii; pectoralis major|Deltoid (anterior part)
        33|Goblet squat|Dumbbell or kettlebell|Hold the weight at chest level, bend the hips and knees into a squat, and stand back up.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); $erectors (stabilization)
        34|Bodyweight squat|Bodyweight; floor|With feet planted, bend the hips and knees to lower the body, then extend both joints to stand.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); soleus; $erectors (stabilization)
        35|Front squat|Barbell; weight plates; squat rack|Support the bar across the front of the shoulders and squat, keeping the trunk braced as the hips and knees bend.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); $erectors (stabilization)
        36|Leg press|Leg press machine; weight plates or weight stack|With the pelvis supported, bend the knees to lower the sled or platform and then extend the legs to press it away.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); soleus
        37|Split squat|Bodyweight; floor|From a staggered stance, lower the back knee toward the floor, then extend the front hip and knee to rise.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); gluteus medius (stabilization); soleus
        38|Dumbbell reverse lunge|Dumbbells|Step backward into a lunge, lower with control, and use the front leg to return to standing.|$quadriceps; gluteus maximus|Adductor magnus (hamstring part); gluteus medius (stabilization); soleus
        39|Step-up|Bodyweight; stable exercise step or box|Place one foot on a secure step and extend that hip and knee to rise, then lower with control.|$quadriceps; gluteus maximus|Gluteus medius (stabilization); adductor magnus (hamstring part); soleus
        40|Romanian deadlift|Barbell; weight plates|From standing, hinge at the hips with slightly bent knees; lower the bar along the legs, then extend the hips.|Gluteus maximus; $hipHamstrings|Adductor magnus (hamstring part); $erectors (stabilization); flexor digitorum superficialis; flexor digitorum profundus (grip)
        41|Barbell hip thrust|Barbell; weight plates; stable bench; bar pad|Rest the upper back on a bench and extend the hips to raise the loaded pelvis, then lower without overextending the back.|Gluteus maximus|$hipHamstrings; adductor magnus (hamstring part); quadriceps femoris (stabilization)
        42|Glute bridge|Bodyweight; floor or mat|Lie on the back with knees bent and feet planted; extend the hips to raise the pelvis, then lower.|Gluteus maximus|$hipHamstrings; adductor magnus (hamstring part); rectus abdominis; external oblique; internal oblique (trunk stabilization)
        43|Leg extension|Leg extension machine|Seated with the knees aligned to the machine pivot, straighten the knees against the pad and lower with control.|$quadriceps|Rectus abdominis; external oblique; internal oblique (trunk stabilization)
        44|Seated leg curl|Seated leg curl machine|With the thighs supported, bend the knees to pull the lower-leg pad downward and backward, then return.|$hamstrings|Gastrocnemius; gracilis; sartorius
        45|Standing calf raise|Standing calf raise machine|With knees extended but not forced backward, raise the heels through the ankles and lower through a controlled range.|Gastrocnemius; soleus|Tibialis posterior; fibularis longus; fibularis brevis
        46|Seated calf raise|Seated calf raise machine|With knees bent and the load supported above them, lift the heels and then lower them with control.|Soleus|Gastrocnemius; tibialis posterior; fibularis longus; fibularis brevis
        47|Crunch|Bodyweight; floor or mat|Lie on the back with knees bent and curl the rib cage toward the pelvis, then lower without pulling the head.|Rectus abdominis|External oblique; internal oblique
        48|Ab wheel rollout|Bodyweight; ab wheel; mat|From kneeling, roll the wheel forward while resisting trunk extension, then draw it back within a controlled range.|Rectus abdominis; external oblique; internal oblique|Latissimus dorsi; pectoralis major; serratus anterior; triceps brachii (stabilization)
    """.trimIndent().lineSequence().map { line ->
        val parts = line.split('|')
        require(parts.size == 6)
        Exercise(id = "e12d7b3e-588b-4fc2-8d3f-" + parts[0].padStart(12, '0'),
            name = parts[1], equipment = parts[2], description = parts[3],
            primaryMuscles = parts[4], secondaryMuscles = parts[5])
    }.toList()

    fun seed(db: SupportSQLiteDatabase) {
        exercises.forEachIndexed { index, exercise ->
            db.execSQL("""
                INSERT OR IGNORE INTO exercises
                (id, name, equipment, isArchived, description, primaryMuscles, secondaryMuscles)
                VALUES (?, ?, ?, 0, ?, ?, ?)
            """.trimIndent(), arrayOf(exercise.id, exercise.name, exercise.equipment,
                exercise.description, exercise.primaryMuscles, exercise.secondaryMuscles))
            // Enrich the original six seeds without replacing IDs, names, archive flags, or edited equipment.
            if (index < 6) {
                val originalEquipment = when (index) { 4 -> "Dumbbell"; 5 -> "Pull-up bar"; else -> "Barbell" }
                db.execSQL("""
                    UPDATE exercises SET
                    equipment = CASE WHEN equipment = ? THEN ? ELSE equipment END,
                    description = CASE WHEN description = '' THEN ? ELSE description END,
                    primaryMuscles = CASE WHEN primaryMuscles = '' THEN ? ELSE primaryMuscles END,
                    secondaryMuscles = CASE WHEN secondaryMuscles = '' THEN ? ELSE secondaryMuscles END
                    WHERE id = ?
                """.trimIndent(), arrayOf(originalEquipment, exercise.equipment, exercise.description,
                    exercise.primaryMuscles, exercise.secondaryMuscles, exercise.id))
            }
        }
    }
}
