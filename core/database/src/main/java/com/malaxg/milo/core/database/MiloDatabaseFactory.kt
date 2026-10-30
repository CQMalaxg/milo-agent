package com.malaxg.milo.core.database

import android.content.Context
import androidx.room.Room

/**
 * 数据库创建入口。
 * 放在本模块内，避免上层（app）为了建库而直接依赖 Room。
 */
object MiloDatabaseFactory {

    fun create(
        context: Context,
        name: String = DATABASE_NAME,
    ): MiloDatabase = Room
        .databaseBuilder(context.applicationContext, MiloDatabase::class.java, name)
        .fallbackToDestructiveMigration()
        .build()

    private const val DATABASE_NAME = "milo.db"
}
