package server.geo

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.record.City
import com.maxmind.geoip2.record.Country
import com.maxmind.geoip2.record.Subdivision
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaxMindIpGeolocationServiceTest {
    @Test
    fun `private IP is skipped without DB lookup`() =
        runTest {
            val reader = mockk<DatabaseReader>(relaxed = true)
            val service = MaxMindIpGeolocationService(reader)

            val result = service.lookup("192.168.1.1")

            assertNull(result)
            verify(exactly = 0) { reader.city(any()) }
        }

    @Test
    fun `null IP returns null without DB lookup`() =
        runTest {
            val reader = mockk<DatabaseReader>(relaxed = true)
            val service = MaxMindIpGeolocationService(reader)

            assertNull(service.lookup(null))
            verify(exactly = 0) { reader.city(any()) }
        }

    @Test
    fun `non-literal hostname is skipped without DB lookup`() =
        runTest {
            val reader = mockk<DatabaseReader>(relaxed = true)
            val service = MaxMindIpGeolocationService(reader)

            assertNull(service.lookup("example.com"))
            verify(exactly = 0) { reader.city(any()) }
        }

    @Test
    fun `AddressNotFoundException is swallowed and returns null`() =
        runTest {
            val reader = mockk<DatabaseReader>()
            every { reader.city(any()) } throws AddressNotFoundException("not in DB")
            val service = MaxMindIpGeolocationService(reader)

            assertNull(service.lookup("8.8.8.8"))
        }

    @Test
    fun `prefers Japanese names when present`() =
        runTest {
            val reader = mockk<DatabaseReader>()
            val response =
                stubResponse(
                    country = mapOf("ja" to "日本", "en" to "Japan"),
                    region = mapOf("ja" to "東京都", "en" to "Tokyo"),
                    city = mapOf("ja" to "千代田区", "en" to "Chiyoda"),
                )
            every { reader.city(InetAddress.getByName("8.8.8.8")) } returns response

            val result = MaxMindIpGeolocationService(reader).lookup("8.8.8.8")

            assertEquals(GeoLocation(country = "日本", region = "東京都", city = "千代田区"), result)
        }

    @Test
    fun `falls back to English when Japanese is absent`() =
        runTest {
            val reader = mockk<DatabaseReader>()
            val response =
                stubResponse(
                    country = mapOf("en" to "United States"),
                    region = mapOf("en" to "California"),
                    city = mapOf("en" to "Mountain View"),
                )
            every { reader.city(any()) } returns response

            val result = MaxMindIpGeolocationService(reader).lookup("8.8.8.8")

            assertEquals(
                GeoLocation(country = "United States", region = "California", city = "Mountain View"),
                result,
            )
        }

    @Test
    fun `all-null fields return null instead of empty GeoLocation`() =
        runTest {
            val reader = mockk<DatabaseReader>()
            val response =
                stubResponse(
                    country = emptyMap(),
                    region = emptyMap(),
                    city = emptyMap(),
                )
            every { reader.city(any()) } returns response

            assertNull(MaxMindIpGeolocationService(reader).lookup("8.8.8.8"))
        }

    @Test
    fun `unexpected exception returns null instead of propagating`() =
        runTest {
            val reader = mockk<DatabaseReader>()
            every { reader.city(any()) } throws RuntimeException("DB corrupted")

            assertNull(MaxMindIpGeolocationService(reader).lookup("8.8.8.8"))
        }

    private fun stubResponse(
        country: Map<String, String>,
        region: Map<String, String>,
        city: Map<String, String>,
    ): CityResponse {
        val countryRecord = mockk<Country>()
        every { countryRecord.names } returns country
        val subdivision = mockk<Subdivision>()
        every { subdivision.names } returns region
        val cityRecord = mockk<City>()
        every { cityRecord.names } returns city
        val response = mockk<CityResponse>()
        every { response.country } returns countryRecord
        every { response.mostSpecificSubdivision } returns subdivision
        every { response.city } returns cityRecord
        return response
    }
}
