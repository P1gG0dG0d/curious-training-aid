package com.haoqi.training.di

import android.content.Context
import com.haoqi.training.data.db.AppDatabase
import com.haoqi.training.data.prefs.ApiConfigStore
import com.haoqi.training.data.repo.DietRepository
import com.haoqi.training.data.repo.ProfileRepository
import com.haoqi.training.data.repo.TrainingRepository

/** 手工依赖容器（沿用「好奇旅行」的轻量做法，不引入 DI 框架）。 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.build(appContext)
    val trainingRepository = TrainingRepository(database)
    val dietRepository = DietRepository(database)
    val profileRepository = ProfileRepository(database)
    val apiConfigStore = ApiConfigStore(appContext)
}
