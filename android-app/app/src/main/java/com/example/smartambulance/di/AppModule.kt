package com.example.smartambulance.di

import com.example.smartambulance.data.api.ApiService
import com.example.smartambulance.data.api.RetrofitClient
import com.example.smartambulance.data.DataRepository
import com.example.smartambulance.data.DefaultDataRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return RetrofitClient.apiService
    }

    @Provides
    @Singleton
    fun provideDataRepository(): DataRepository {
        return DefaultDataRepository()
    }
}
