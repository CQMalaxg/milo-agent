package com.malaxg.milo.core.database

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malaxg.milo.core.database.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiloDatabaseMigrationTest {
    @Test
    fun newTransactionPhotoUriSurvivesDatabaseReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "milo-photo-roundtrip-test.db"
        context.deleteDatabase(databaseName)
        try {
            val first = MiloDatabaseFactory.create(context, databaseName)
            try {
                first.transactionDao().insert(
                    TransactionEntity(
                        id = "lunch-photo", amountCents = 3200, currency = "CNY",
                        type = "EXPENSE", categoryId = "餐饮", note = "午饭",
                        occurredAt = 1000, createdAt = 1000, source = "MANUAL",
                        imageUri = "content://test/lunch-photo",
                    ),
                )
            } finally {
                first.close()
            }
            val reopened = MiloDatabaseFactory.create(context, databaseName)
            try {
                assertEquals("content://test/lunch-photo", reopened.transactionDao().findById("lunch-photo")?.imageUri)
                val existing = requireNotNull(reopened.transactionDao().findById("lunch-photo"))
                assertEquals(1, reopened.transactionDao().update(existing.copy(amountCents = 4567, imageUri = null)))
                assertEquals(4567L, reopened.transactionDao().findById("lunch-photo")?.amountCents)
                assertEquals(null, reopened.transactionDao().findById("lunch-photo")?.imageUri)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun versionOneTransactionsSurviveImageUriMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "milo-migration-test.db"
        context.deleteDatabase(databaseName)

        try {
            val legacyDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
            try {
                legacyDatabase.execSQL(
                    """CREATE TABLE `transactions` (
                        `id` TEXT NOT NULL,
                        `amountCents` INTEGER NOT NULL,
                        `currency` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `categoryId` TEXT,
                        `note` TEXT,
                        `occurredAt` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `source` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                legacyDatabase.execSQL("CREATE INDEX `index_transactions_occurredAt` ON `transactions` (`occurredAt`)")
                legacyDatabase.execSQL(
                    """INSERT INTO `transactions` (`id`, `amountCents`, `currency`, `type`, `categoryId`, `note`, `occurredAt`, `createdAt`, `source`)
                        VALUES ('legacy', 1250, 'CNY', 'EXPENSE', NULL, '旧账目', 1000, 1000, 'MANUAL')""".trimIndent(),
                )
                legacyDatabase.version = 1
            } finally {
                legacyDatabase.close()
            }

            val migratedDatabase = MiloDatabaseFactory.create(context, databaseName)
            try {
                val migrated = migratedDatabase.transactionDao().findById("legacy")

                assertEquals(1250L, migrated?.amountCents)
                assertEquals("旧账目", migrated?.note)
                assertEquals(null, migrated?.imageUri)
            } finally {
                migratedDatabase.close()
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun unsupportedDatabaseVersionIsRejectedWithoutDeletingRecords() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "milo-unsupported-version-test.db"
        context.deleteDatabase(databaseName)

        val futureDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            futureDatabase.execSQL(
                """CREATE TABLE `transactions` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `note` TEXT
                )""".trimIndent(),
            )
            futureDatabase.execSQL("INSERT INTO `transactions` (`id`, `note`) VALUES ('preserve-me', '保留记录')")
            futureDatabase.version = 3
        } finally {
            futureDatabase.close()
        }

        var rejected = false
        val unsupportedDatabase = MiloDatabaseFactory.create(context, databaseName)
        try {
            unsupportedDatabase.openHelper.writableDatabase
        } catch (_: RuntimeException) {
            rejected = true
        } finally {
            unsupportedDatabase.close()
        }

        val preservedDatabase = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        try {
            preservedDatabase.rawQuery("SELECT `note` FROM `transactions` WHERE `id` = 'preserve-me'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("保留记录", cursor.getString(0))
            }
            assertTrue("Unsupported schema versions must not be opened", rejected)
        } finally {
            preservedDatabase.close()
            context.deleteDatabase(databaseName)
        }
    }
}
