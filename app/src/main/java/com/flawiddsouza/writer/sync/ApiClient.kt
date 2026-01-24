package com.flawiddsouza.writer.sync

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// Data classes for API requests/responses
data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthResponse(
    val token: String,
    @SerializedName("userId") val userId: String,
    val email: String
)

data class SyncEntry(
    @SerializedName("local_id") val localId: Long? = null,
    @SerializedName("server_id") val serverId: String? = null,
    @SerializedName("encrypted_data") val encryptedData: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_deleted") val isDeleted: Boolean = false
)

data class SyncCategory(
    @SerializedName("local_id") val localId: Long? = null,
    @SerializedName("server_id") val serverId: String? = null,
    @SerializedName("encrypted_data") val encryptedData: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("is_deleted") val isDeleted: Boolean = false
)

data class PushRequest(
    val entries: List<SyncEntry>,
    val categories: List<SyncCategory>
)

data class PushResultItem(
    @SerializedName("local_id") val localId: Long?,
    @SerializedName("server_id") val serverId: String?,
    val status: String, // "success", "conflict", "error"
    @SerializedName("conflict_data") val conflictData: Map<String, Any>? = null,
    val error: String? = null
)

data class PushResponse(
    val entries: List<PushResultItem>,
    val categories: List<PushResultItem>
)

data class ChangesResponse(
    val entries: List<ServerEntry>,
    val categories: List<ServerCategory>,
    @SerializedName("current_timestamp") val currentTimestamp: String
)

data class ServerEntry(
    val id: String,
    @SerializedName("item_type") val itemType: String,
    @SerializedName("encrypted_data") val encryptedData: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String?
)

data class ServerCategory(
    val id: String,
    @SerializedName("item_type") val itemType: String,
    @SerializedName("encrypted_data") val encryptedData: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("deleted_at") val deletedAt: String?
)

data class ErrorResponse(
    val error: String
)

data class MasterKeyResponse(
    @SerializedName("encryptedMasterKey") val encryptedMasterKey: String?,
    val exists: Boolean
)

data class MasterKeyRequest(
    @SerializedName("encryptedMasterKey") val encryptedMasterKey: String
)

data class SuccessResponse(
    val success: Boolean
)

// Retrofit API interface
interface WriterSyncApi {
    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @GET("sync/changes")
    suspend fun getChanges(
        @Header("Authorization") token: String,
        @Query("since") since: String
    ): Response<ChangesResponse>

    @POST("sync/push")
    suspend fun pushChanges(
        @Header("Authorization") token: String,
        @Body request: PushRequest
    ): Response<PushResponse>

    @GET("auth/master-key")
    suspend fun getMasterKey(
        @Header("Authorization") token: String
    ): Response<MasterKeyResponse>

    @POST("auth/master-key")
    suspend fun uploadMasterKey(
        @Header("Authorization") token: String,
        @Body request: MasterKeyRequest
    ): Response<SuccessResponse>

    @POST("auth/change-master-key-password")
    suspend fun changeMasterKeyPassword(
        @Header("Authorization") token: String,
        @Body request: MasterKeyRequest
    ): Response<SuccessResponse>
}

// API Client singleton
object ApiClient {
    private var retrofit: Retrofit? = null
    private var api: WriterSyncApi? = null
    private var baseUrl: String = ""
    private var authToken: String? = null

    fun initialize(serverUrl: String) {
        baseUrl = serverUrl.trimEnd('/')

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit?.create(WriterSyncApi::class.java)
    }

    fun setAuthToken(token: String?) {
        authToken = token
    }

    fun getAuthToken(): String? = authToken

    private fun getAuthHeader(): String {
        return "Bearer $authToken"
    }

    suspend fun register(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api?.register(AuthRequest(email, password))
            if (response?.isSuccessful == true && response.body() != null) {
                val authResponse = response.body()!!
                authToken = authResponse.token
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response?.message() ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = api?.login(AuthRequest(email, password))
            if (response?.isSuccessful == true && response.body() != null) {
                val authResponse = response.body()!!
                authToken = authResponse.token
                Result.success(authResponse)
            } else {
                Result.failure(Exception(response?.message() ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChanges(since: String): Result<ChangesResponse> {
        return try {
            if (authToken == null) {
                return Result.failure(Exception("Not authenticated"))
            }

            val response = api?.getChanges(getAuthHeader(), since)
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response?.message() ?: "Failed to get changes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pushChanges(entries: List<SyncEntry>, categories: List<SyncCategory>): Result<PushResponse> {
        return try {
            if (authToken == null) {
                return Result.failure(Exception("Not authenticated"))
            }

            val request = PushRequest(entries, categories)
            val response = api?.pushChanges(getAuthHeader(), request)
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response?.message() ?: "Failed to push changes"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMasterKey(): Result<MasterKeyResponse> {
        return try {
            if (authToken == null) {
                return Result.failure(Exception("Not authenticated"))
            }

            val response = api?.getMasterKey(getAuthHeader())
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response?.message() ?: "Failed to get master key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadMasterKey(encryptedMasterKey: String): Result<Unit> {
        return try {
            if (authToken == null) {
                return Result.failure(Exception("Not authenticated"))
            }

            val request = MasterKeyRequest(encryptedMasterKey)
            val response = api?.uploadMasterKey(getAuthHeader(), request)
            if (response?.isSuccessful == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response?.message() ?: "Failed to upload master key"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changeMasterKeyPassword(newEncryptedMasterKey: String): Result<Unit> {
        return try {
            if (authToken == null) {
                return Result.failure(Exception("Not authenticated"))
            }

            val request = MasterKeyRequest(newEncryptedMasterKey)
            val response = api?.changeMasterKeyPassword(getAuthHeader(), request)
            if (response?.isSuccessful == true) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response?.message() ?: "Failed to change master key password"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isInitialized(): Boolean = api != null
    fun isAuthenticated(): Boolean = authToken != null
}
