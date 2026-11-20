package com.example.catalogue.data

import com.example.catalogue.AppContainer
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The real data layer (Retrofit, JSON config, repositories, error translation, built by the real
 * AppContainer) against a local mock HTTP server that returns the backend's documented responses.
 */
class BackendDataLayerTest {

    private val server = MockWebServer().apply { start() }
    private val fastClient = OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build()
    private val baseUrl = server.url("/").toString()
    private val container = AppContainer(baseUrl, fastClient)

    @After
    fun tearDown() = server.shutdown()

    private fun respond(status: Int, body: String) =
        server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body))

    private val productJson = """
        {"id":1,"title":"Backpack","price":109.95,"description":"A bag","category":"men's clothing",
         "rating":3.9,"ratingCount":120,"someNewField":"ignored"}
    """

    @Test
    fun `featured products map to domain with image url and missing count`() = runTest {
        respond(200, """{"configuredCount":5,"products":[$productJson],"missingProductIds":[99,98]}""")

        val result = container.getFeaturedProducts() as DataResult.Success
        val featured = result.value

        assertEquals("/api/products/featured", server.takeRequest().path)
        assertEquals(5, featured.configuredCount)
        assertEquals(2, featured.missingCount)
        assertTrue(featured.isIncomplete)
        val p = featured.products.single()
        assertEquals("Backpack", p.title)
        assertEquals(109.95, p.price, 0.0)
        assertEquals(120, p.ratingCount)
        assertEquals("${baseUrl}api/products/1/image", p.imageUrl)
    }

    @Test
    fun `category name with apostrophe and space is URL-encoded in the path`() = runTest {
        respond(200, """{"category":"men's clothing","topRated":[$productJson],"otherProducts":[]}""")

        val result = container.getCategoryProducts("men's clothing") as DataResult.Success

        assertEquals("/api/categories/men's%20clothing/products", server.takeRequest().path)
        assertEquals("men's clothing", result.value.category)
        assertEquals(listOf(1), result.value.topRated.map { it.id })
    }

    @Test
    fun `search query is sent as an encoded q parameter`() = runTest {
        respond(200, """{"query":"gold & silver","products":[]}""")

        container.searchProducts("gold & silver")

        assertEquals("/api/products/search?q=gold%20%26%20silver", server.takeRequest().path)
    }

    @Test
    fun `404 problem becomes NotFound with the backend code`() = runTest {
        respond(404, """{"status":404,"title":"Product not found","code":"PRODUCT_NOT_FOUND"}""")

        assertEquals(DataResult.Failure(AppError.NotFound("PRODUCT_NOT_FOUND")), container.getProductDetails(999))
    }

    @Test
    fun `503 problem becomes ServiceUnavailable`() = runTest {
        respond(503, """{"status":503,"code":"PRODUCT_SOURCE_UNAVAILABLE"}""")

        assertEquals(DataResult.Failure(AppError.ServiceUnavailable("PRODUCT_SOURCE_UNAVAILABLE")), container.getCategories())
    }

    @Test
    fun `error without a JSON body or with an unknown code is still handled`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        assertEquals(DataResult.Failure(AppError.Server(502, null)), container.getCategories())

        respond(500, """{"status":500,"code":"SOMETHING_NEW"}""")
        assertEquals(DataResult.Failure(AppError.Server(500, "SOMETHING_NEW")), container.getCategories())
    }

    @Test
    fun `unreadable success body becomes UnreadableResponse`() = runTest {
        respond(200, """{"unexpected":true}""")

        assertEquals(DataResult.Failure(AppError.UnreadableResponse), container.getCategories())
    }

    @Test
    fun `no response in time becomes Timeout`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(DataResult.Failure(AppError.Timeout), container.getCategories())
    }

    @Test
    fun `backend not running becomes NoConnection`() = runTest {
        val stopped = MockWebServer().apply { start() }
        val url = stopped.url("/").toString()
        stopped.shutdown()

        assertEquals(DataResult.Failure(AppError.NoConnection), AppContainer(url, fastClient).getCategories())
    }

    // --- enquiries -----------------------------------------------------------------

    private val enquiry = Enquiry(1, "Sam Lee", "sam@example.com", "")

    @Test
    fun `enquiry posts the backend's JSON shape and returns the reference`() = runTest {
        respond(200, """{"enquiryId":"ref-123","status":"SENT"}""")

        val result = container.submitEnquiry(enquiry.copy(message = "In stock?"))

        assertEquals(SubmitEnquiryResult.Sent("ref-123"), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/enquiries", request.path)
        assertEquals(
            """{"productId":1,"customerName":"Sam Lee","email":"sam@example.com","message":"In stock?"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `blank optional message is left out of the request`() = runTest {
        respond(200, """{"enquiryId":"ref-1","status":"SENT"}""")

        container.submitEnquiry(enquiry)

        assertFalse(server.takeRequest().body.readUtf8().contains("message"))
    }

    @Test
    fun `backend field errors map to form fields`() = runTest {
        respond(400, """{"status":400,"code":"VALIDATION_FAILED",
            "errors":{"email":"must be a well-formed email address","customerName":"must not be blank"}}""")

        val result = container.submitEnquiry(enquiry) as SubmitEnquiryResult.Invalid

        assertEquals("Must be a well-formed email address", result.fieldErrors[EnquiryField.Email])
        assertEquals("Must not be blank", result.fieldErrors[EnquiryField.Name])
    }

    @Test
    fun `field errors the form cannot show become a general failure`() = runTest {
        respond(400, """{"status":400,"code":"VALIDATION_FAILED","errors":{"productId":"must be greater than 0"}}""")

        val result = container.submitEnquiry(enquiry)

        assertTrue(result is SubmitEnquiryResult.Failed)
    }

    // --- OkHttp automatic retries ---------------------------------------------------

    /**
     * Evidence for the AppContainer change, not a test of our code: with OkHttp's default
     * retryOnConnectionFailure = true, a POST written to a reused keep-alive connection that then
     * drops is sent again on a new connection (RetryAndFollowUpInterceptor.recover, OkHttp 4.12.0).
     */
    @Test
    fun `default OkHttp client replays a POST when a reused connection drops after sending`() {
        val client = OkHttpClient() // retryOnConnectionFailure defaults to true
        server.enqueue(MockResponse().setBody("warm-up")) // leaves a pooled keep-alive connection
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("second attempt"))

        client.newCall(Request.Builder().url(server.url("/warm-up")).build()).execute().close()
        val post = Request.Builder().url(server.url("/api/enquiries"))
            .post("""{"x":1}""".toRequestBody("application/json".toMediaType())).build()
        val body = client.newCall(post).execute().use { it.body!!.string() }

        assertEquals("second attempt", body)
        assertEquals("GET + the same POST twice", 3, server.requestCount)
    }

    @Test
    fun `enquiry POST is not replayed when a reused connection drops after sending`() = runTest {
        respond(200, """{"categories":["electronics"]}""") // warm-up GET leaves a pooled connection
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        respond(200, """{"enquiryId":"should-not-be-used","status":"SENT"}""")

        assertTrue(container.getCategories() is DataResult.Success)
        val result = container.submitEnquiry(enquiry)

        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.ConnectionLost), result)
        assertEquals("GET + the POST once", 2, server.requestCount)
    }

    @Test
    fun `catalogue GETs keep OkHttp's automatic retry on a dropped reused connection`() = runTest {
        respond(200, """{"categories":["electronics"]}""")
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        respond(200, """{"categories":["jewelery"]}""")

        container.getCategories()
        val second = container.getCategories()

        assertEquals(DataResult.Success(listOf("jewelery")), second)
        assertEquals(3, server.requestCount)
    }

    // --- enquiry outcome: definitely not sent vs. unconfirmed -----------------------

    @Test
    fun `connection dropped after the enquiry was sent is Unconfirmed, and is not silently resent`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        val result = container.submitEnquiry(enquiry)

        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.ConnectionLost), result)
        assertEquals("the POST must reach the server exactly once", 1, server.requestCount)
    }

    @Test
    fun `timeout waiting for the enquiry response is Unconfirmed`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.Timeout), container.submitEnquiry(enquiry))
    }

    @Test
    fun `unexpected server or proxy error without a known code is Unconfirmed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>Bad gateway</html>"))
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.Server(502, null)), container.submitEnquiry(enquiry))

        respond(500, """{"status":500,"code":"INTERNAL_ERROR"}""")
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.Server(500, "INTERNAL_ERROR")), container.submitEnquiry(enquiry))
    }

    @Test
    fun `200 with an unreadable body is Unconfirmed, because the email was probably sent`() = runTest {
        respond(200, """{"unexpected":true}""")

        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.UnreadableResponse), container.submitEnquiry(enquiry))
    }

    @Test
    fun `backend not reachable at all is definitely not sent`() = runTest {
        val stopped = MockWebServer().apply { start() }
        val url = stopped.url("/").toString()
        stopped.shutdown()

        assertEquals(
            SubmitEnquiryResult.Failed(AppError.NoConnection),
            AppContainer(url, fastClient).submitEnquiry(enquiry),
        )
    }

    @Test
    fun `our backend's pre-send error codes are definitely not sent`() = runTest {
        respond(404, """{"status":404,"code":"PRODUCT_NOT_FOUND"}""")
        assertEquals(SubmitEnquiryResult.Failed(AppError.NotFound("PRODUCT_NOT_FOUND")), container.submitEnquiry(enquiry))

        respond(503, """{"status":503,"code":"PRODUCT_SOURCE_UNAVAILABLE"}""")
        assertEquals(
            SubmitEnquiryResult.Failed(AppError.ServiceUnavailable("PRODUCT_SOURCE_UNAVAILABLE")),
            container.submitEnquiry(enquiry),
        )

        respond(400, """{"status":400,"code":"MALFORMED_REQUEST"}""")
        assertEquals(SubmitEnquiryResult.Failed(AppError.Server(400, "MALFORMED_REQUEST")), container.submitEnquiry(enquiry))

        respond(415, """{"status":415,"code":"UNSUPPORTED_MEDIA_TYPE"}""")
        assertEquals(SubmitEnquiryResult.Failed(AppError.Server(415, "UNSUPPORTED_MEDIA_TYPE")), container.submitEnquiry(enquiry))
    }

    @Test
    fun `EMAIL_SEND_FAILED is Unconfirmed, because SMTP may have accepted the message before failing`() = runTest {
        respond(503, """{"status":503,"code":"EMAIL_SEND_FAILED","detail":"Your enquiry could not be sent right now."}""")

        assertEquals(
            SubmitEnquiryResult.Unconfirmed(AppError.ServiceUnavailable("EMAIL_SEND_FAILED")),
            container.submitEnquiry(enquiry),
        )
    }

    @Test
    fun `4xx without one of our pre-send codes is Unconfirmed, because a proxy may have sent it`() = runTest {
        server.enqueue(MockResponse().setResponseCode(408).setBody("<html>Request Timeout</html>"))
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.Server(408, null)), container.submitEnquiry(enquiry))

        respond(429, """{"status":429,"code":"RATE_LIMITED"}""")
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.Server(429, "RATE_LIMITED")), container.submitEnquiry(enquiry))

        server.enqueue(MockResponse().setResponseCode(404).setBody("Not Found"))
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.NotFound(null)), container.submitEnquiry(enquiry))
    }

    @Test
    fun `5xx without one of our pre-send codes is Unconfirmed`() = runTest {
        respond(503, """{"status":503}""")
        assertEquals(SubmitEnquiryResult.Unconfirmed(AppError.ServiceUnavailable(null)), container.submitEnquiry(enquiry))
    }
}
