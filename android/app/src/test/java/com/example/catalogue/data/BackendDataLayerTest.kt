package com.example.catalogue.data

import com.example.catalogue.AppContainer
import com.example.catalogue.domain.model.AppError
import com.example.catalogue.domain.model.DataResult
import com.example.catalogue.domain.model.Enquiry
import com.example.catalogue.domain.model.EnquiryField
import com.example.catalogue.domain.model.SubmitEnquiryResult
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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

    @Test
    fun `email send failure keeps its code`() = runTest {
        respond(503, """{"status":503,"code":"EMAIL_SEND_FAILED","detail":"Your enquiry could not be sent right now."}""")

        assertEquals(
            SubmitEnquiryResult.Failed(AppError.ServiceUnavailable("EMAIL_SEND_FAILED")),
            container.submitEnquiry(enquiry),
        )
    }
}
