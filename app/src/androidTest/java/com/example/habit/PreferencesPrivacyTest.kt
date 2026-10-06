package com.example.habit

import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.habit.data.prefs.SettingsRepository
import com.example.habit.data.prefs.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class PreferencesPrivacyTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun oldPreferenceKeysDefaultsAndNewWritesSurviveStoreReopen() = runBlocking {
        val file = File(context.cacheDir, "test-settings.preferences_pb").apply { delete() }
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        val repo = SettingsRepository(store)
        assertEquals(SettingsRepository.Configuration(), repo.configuration.first())
        store.edit {
            it[booleanPreferencesKey("onboarding_complete")] = true
            it[stringPreferencesKey("user_name")] = "Shimul"
            it[stringPreferencesKey("theme_mode")] = "DARK"
        }
        repo.setWeekStart(DayOfWeek.SUNDAY)
        repo.setCoachEnabled(false)
        repo.setReminder(true, 22 * 60 + 15)
        try { repo.setReminder(false, 1440); fail("Invalid reminder time accepted") } catch (_: IllegalArgumentException) { }
        val expected = SettingsRepository.Configuration(true, "Shimul", ThemeMode.DARK, DayOfWeek.SUNDAY, false, true, 1335)
        assertEquals(expected, repo.configuration.first())
        job.cancelAndJoin()
        val reopenedJob = SupervisorJob()
        try {
            val reopened = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + reopenedJob)) { file }
            assertEquals(expected, SettingsRepository(reopened).configuration.first())
        } finally { reopenedJob.cancelAndJoin(); file.delete() }
    }

    @Test fun packagedBrandAndBackupRulesMatchLocalOnlyContract() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("Habit Companion", context.packageManager.getApplicationLabel(info).toString())
        assertEquals("Habit Companion", context.getString(R.string.splash_brand_mark))
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        val domains = setOf("root", "file", "database", "sharedpref", "external", "device_root", "device_file", "device_database", "device_sharedpref")
        fun exclusions(id: Int): Map<String, Set<String>> {
            val sections = mutableMapOf<String, MutableSet<String>>()
            context.resources.getXml(id).use { parser ->
                var section = ""
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        if (parser.name in setOf("full-backup-content", "cloud-backup", "device-transfer")) section = parser.name
                        if (parser.name == "exclude") {
                            assertEquals(".", parser.getAttributeValue(null, "path"))
                            sections.getOrPut(section) { mutableSetOf() }.add(parser.getAttributeValue(null, "domain"))
                        }
                    }
                    parser.next()
                }
            }
            return sections
        }
        assertEquals(domains, exclusions(R.xml.backup_rules)["full-backup-content"])
        val newer = exclusions(R.xml.data_extraction_rules)
        assertEquals(domains, newer["cloud-backup"])
        assertEquals(domains, newer["device-transfer"])
    }
}
