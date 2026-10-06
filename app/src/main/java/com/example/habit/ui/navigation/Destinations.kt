package com.example.habit.ui.navigation

/**
 * Screen IDs are permanent: these route names map one-to-one onto SCR-01 … SCR-15 and are
 * reused unchanged in Document 2, the UI Flow.
 */
object Routes {
    /** SCR-01 */
    const val SPLASH = "splash"

    /** SCR-02 */
    const val ONBOARDING = "onboarding"

    const val ARG_DB_ERROR = "dbError"

    /**
     * SCR-03 when there are no habits, SCR-04 once there is one. Navigation identifies a
     * destination by its full pattern, so this constant — not the bare "home" — is what
     * `popUpTo` has to be given.
     */
    const val HOME = "home?$ARG_DB_ERROR={$ARG_DB_ERROR}"

    /** SCR-05 — currently a stub that collects a name only. */
    const val NEW_HABIT = "new-habit"

    /** SCR-12 / SCR-13, SCR-09, SCR-15 — nav roots, not built in this phase. */
    const val PROGRESS = "progress"
    const val COACH = "coach"
    const val PROFILE = "profile"

    /**
     * SCR-01 failure path: Room threw, so Home opens empty and surfaces a retry rather
     * than the splash blocking on it.
     */
    fun home(dbError: Boolean = false) = "home?$ARG_DB_ERROR=$dbError"
}
