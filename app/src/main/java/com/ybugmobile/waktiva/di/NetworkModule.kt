package com.ybugmobile.waktiva.di

import com.ybugmobile.waktiva.BuildConfig
import com.ybugmobile.waktiva.data.remote.AladhanApiService
import com.ybugmobile.waktiva.data.remote.OverpassApiService
import com.ybugmobile.waktiva.data.remote.WeatherApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            // Never log bodies in release builds — user coordinates and API payloads are sensitive
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            // Overpass queries declare [timeout:25]; read timeout must exceed that
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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
    fun provideWeatherApiService(okHttpClient: OkHttpClient): WeatherApiService {
        return Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOverpassApiService(okHttpClient: OkHttpClient): OverpassApiService {
        return Retrofit.Builder()
            .baseUrl(OverpassApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverpassApiService::class.java)
    }
}
