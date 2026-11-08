package com.wwwescape.medicinereminder.data.db

/** How a medicine looks in the app. Stored by name, so entries may be added but never renamed.
 * [twoTone] shapes draw a second part (capsule half, cap, canister…) in the second color. */
enum class MedicineShape(val twoTone: Boolean, val defaultUnit: DoseUnit) {
    ROUND(false, DoseUnit.TABLET),
    ROUND_SCORED(false, DoseUnit.TABLET),
    ROUND_HALVES(true, DoseUnit.TABLET),
    OVAL(false, DoseUnit.TABLET),
    CAPLET(false, DoseUnit.TABLET),
    CAPLET_SCORED(false, DoseUnit.TABLET),
    CAPSULE(true, DoseUnit.CAPSULE),
    SOFTGEL(false, DoseUnit.CAPSULE),
    TRIANGLE(false, DoseUnit.TABLET),
    SQUARE(false, DoseUnit.TABLET),
    DIAMOND(false, DoseUnit.TABLET),
    HEXAGON(false, DoseUnit.TABLET),
    HEART(false, DoseUnit.TABLET),
    DROPS(true, DoseUnit.DROP),
    SYRUP(true, DoseUnit.ML),
    NASAL_SPRAY(true, DoseUnit.SPRAY),
    INHALER(true, DoseUnit.PUFF),
    NEBULIZER(true, DoseUnit.SESSION),
    SYRINGE(true, DoseUnit.INJECTION),
    PEN(true, DoseUnit.UNIT),
    VIAL(true, DoseUnit.ML),
    AMPOULE(true, DoseUnit.ML),
    TUBE(true, DoseUnit.APPLICATION),
    PATCH(true, DoseUnit.PATCH),
    SACHET(true, DoseUnit.SACHET),
    SUPPOSITORY(false, DoseUnit.SUPPOSITORY),
}

/** The unit a dose is counted in. Stored by name. */
enum class DoseUnit {
    TABLET, CAPSULE, DROP, PUFF, SPRAY, ML, MG, UNIT, APPLICATION, PATCH, INJECTION, SACHET,
    SUPPOSITORY, TEASPOON, TABLESPOON, DOSE,
    /** One sitting of a treatment such as nebulization or steam inhalation. */
    SESSION,
    /** The single-dose plastic vial a nebulizer is filled from. */
    RESPULE,
    MINUTE,
    /** Powders and ointments weighed out. */
    GRAM,
    /** Presses of a gel or foam pump. */
    PUMP;

    /** What one tap of − / + changes the dose by: halves for tablets and spoons, fives for
     * milligrams and minutes, whole units for everything counted. */
    val step: Double
        get() = when (this) {
            TABLET, TEASPOON, TABLESPOON, GRAM -> 0.5
            MG, MINUTE -> 5.0
            else -> 1.0
        }
}
