package com.ybugmobile.vaktiva.di

import com.ybugmobile.vaktiva.data.remote.AladhanApiService
import com.ybugmobile.vaktiva.data.remote.UmmahApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideAladhanApiService(okHttpClient: OkHttpClient): AladhanApiService {
        return Retrofit.Builder()
            .baseUrl(AladhanApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AladhanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideUmmahApiService(okHttpClient: OkHttpClient): UmmahApiService {
        return Retrofit.Builder()
            .baseUrl(UmmahApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UmmahApiService::class.java)
    }
}
