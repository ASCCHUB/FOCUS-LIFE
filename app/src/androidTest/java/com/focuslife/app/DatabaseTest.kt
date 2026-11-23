package com.focuslife.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.focuslife.app.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class) class DatabaseTest {
    @Test fun persistsGoalCompletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FocusDatabase::class.java).build()
        try { val id = db.dao().addGoal(GoalEntity(name = "Reading", targetMinutes = 20)); db.dao().putCompletion(GoalCompletionEntity(id, "2026-11-21", 10)); assertEquals(10, db.dao().completions("2026-11-21").first().single().completedMinutes) } finally { db.close() }
    }
}
