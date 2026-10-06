package com.example.habit.ui.navigation

/**
 * Destinations group shared screen states from SCR-01 … SCR-15; New/Edit uses one form.
 * Follow the UI Flow for caller-aware navigation rather than creating a route for each state.
 */
object Routes {
    const val MAIN = "main"
    const val ARG_HABIT_ID = "habitId"
    const val HABIT_DETAIL = "habit/{habitId}"
    fun detail(id: Long): String { require(id > 0); return "habit/$id" }

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

    /** SCR-05 — shared form; no permanent habit is created for a planning draft. */
    const val NEW_HABIT = "new-habit?habitId={habitId}&planning={planning}"
    fun newHabit(planning: Boolean = false) = "new-habit?habitId=0&planning=$planning"
    fun editHabit(id: Long): String { require(id > 0); return "new-habit?habitId=$id&planning=false" }

    /** SCR-12 / SCR-13, SCR-09, SCR-15 — nav roots, not built in this phase. */
    const val PROGRESS = "progress"
    const val COACH = "coach"
    const val PROFILE = "profile"

    /**
     * SCR-01 failure path: Room threw, so Home surfaces a read error with Retry rather
     * than manufacturing empty data or keeping Splash blocked.
     */
    fun home(dbError: Boolean = false) = "home?$ARG_DB_ERROR=$dbError"
}
