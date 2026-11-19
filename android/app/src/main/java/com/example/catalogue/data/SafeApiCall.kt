package com.example.catalogue.data

import com.example.catalogue.data.remote.ProblemDto
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Runs one backend call and translates every failure into an [AppError], so nothing above
 * the data layer deals with exceptions, HTTP status codes or JSON.
 *
 * - Timeouts (connect, read or whole-call) -> Timeout
 * - Other I/O failures (offline, connection refused) -> NoConnection
 * - HTTP errors -> read the problem-details body; unknown or missing codes are kept as null/raw
 * - Unreadable success bodies -> UnreadableResponse
 */
internal suspend fun <T> safeApiCall(json: Json, call: suspend () -> T): DataResult<T> =
    try {
        DataResult.Success(call())
    } catch (e: CancellationException) {
        throw e // never swallow coroutine cancellation (for example when the user leaves the screen)
    } catch (e: HttpException) {
        DataResult.Failure(httpError(json, e))
    } catch (e: InterruptedIOException) {
        DataResult.Failure(AppError.Timeout) // SocketTimeoutException is a subclass
    } catch (e: IOException) {
        DataResult.Failure(AppError.NoConnection)
    } catch (e: SerializationException) {
        DataResult.Failure(AppError.UnreadableResponse) // includes a required JSON field missing
    }

private fun httpError(json: Json, e: HttpException): AppError {
    val problem = try {
        e.response()?.errorBody()?.string()?.let { json.decodeFromString<ProblemDto>(it) }
    } catch (_: Exception) {
        null // not JSON (e.g. a proxy's HTML page): fall back to the status code alone
    }
    val code = problem?.code
    return when {
        e.code() == 400 && code == "VALIDATION_FAILED" -> AppError.Validation(problem.errors.orEmpty())
        e.code() == 404 -> AppError.NotFound(code)
        e.code() == 503 -> AppError.ServiceUnavailable(code)
        else -> AppError.Server(e.code(), code)
    }
}
