package com.yassmine.projetpfe.data.api

import android.content.Context
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.yassmine.projetpfe.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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

    private val nullableStringTypeAdapter = object : TypeAdapter<String?>() {
        override fun write(out: JsonWriter, value: String?) {
            if (value == null) out.nullValue() else out.value(value)
        }
        override fun read(reader: JsonReader): String? {
            return when (reader.peek()) {
                JsonToken.NULL -> { reader.nextNull(); null }
                else -> reader.nextString()
            }
        }
    }

    private val meetingCreatorDtoTypeAdapter = object : TypeAdapter<MeetingCreatorDto?>() {
        override fun write(out: JsonWriter, value: MeetingCreatorDto?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.beginObject()
            out.name("id").value(value.id)
            out.name("name").value(value.name)
            out.name("email").value(value.email)
            out.name("profilePicture").value(value.profilePicture)
            out.endObject()
        }

        override fun read(reader: JsonReader): MeetingCreatorDto? {
            return when (reader.peek()) {
                JsonToken.NULL -> { reader.nextNull(); null }

                JsonToken.BEGIN_OBJECT -> {
                    var id = ""; var name = ""; var email = ""; var profilePicture: String? = null
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "id" -> id = reader.nextString()
                            "name"      -> name = reader.nextString()
                            "email"     -> email = reader.nextString()
                            "profilePicture" -> {
                                profilePicture = if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull()
                                    null
                                } else {
                                    reader.nextString()
                                }
                            }
                            else        -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    MeetingCreatorDto(id = id, name = name, email = email, profilePicture = profilePicture)
                }
                else -> { reader.skipValue(); null }
            }
        }
    }

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .registerTypeAdapter(
                object : TypeToken<String?>() {}.type,
                nullableStringTypeAdapter
            )
            .registerTypeAdapter(
                object : TypeToken<MeetingCreatorDto?>() {}.type,
                meetingCreatorDtoTypeAdapter
            )
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService {
        return retrofit.create(ApiService::class.java)
    }
}

