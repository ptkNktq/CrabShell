package server.geo

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CityResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * MaxMind GeoLite2-City `.mmdb` を引いて [GeoLocation] を返す実装。
 *
 * - 国名・地域名・都市名は **日本語ロケール** を優先し、欠けていれば英語 (`en`) にフォールバックする。
 * - プライベート IP / 不正入力は [IpClassifier] で先に弾く（DB に含まれず無駄なクエリになるため）。
 * - 全フィールドが取れなかった場合は null（=「位置情報なし」）を返す。
 * - 例外は WARN ログのみで上位に伝播させない。
 *
 * `reader.city()` は memory-mapped file 越しの同期 I/O（cold 時はページフォルト）が起きうるため、
 * [Dispatchers.IO] にディスパッチしてリクエストハンドラのスレッドを止めない。
 */
class MaxMindIpGeolocationService(
    private val reader: DatabaseReader,
) : IpGeolocationService {
    private val logger = LoggerFactory.getLogger(MaxMindIpGeolocationService::class.java)

    override suspend fun lookup(ip: String?): GeoLocation? {
        val addr = IpClassifier.parsePublicAddress(ip) ?: return null

        return withContext(Dispatchers.IO) {
            try {
                reader.city(addr).toGeoLocation()
            } catch (_: AddressNotFoundException) {
                null
            } catch (e: Exception) {
                // DB 破損、I/O エラー等。サーバー全体は止めず、ジオロケーションだけスキップする。
                logger.warn("GeoIP lookup failed for '$ip': ${e.message}")
                null
            }
        }
    }

    private fun CityResponse.toGeoLocation(): GeoLocation? {
        val countryName = country.localizedName()
        val regionName = mostSpecificSubdivision?.localizedName()
        val cityName = city.localizedName()
        // すべて null なら、そもそも記録する意味がないので null を返す
        if (countryName == null && regionName == null && cityName == null) return null
        return GeoLocation(country = countryName, region = regionName, city = cityName)
    }

    /** GeoLite2 の names マップから日本語名 → 英語名の順で取得。両方なければ null。 */
    private fun com.maxmind.geoip2.record.AbstractNamedRecord.localizedName(): String? = names["ja"] ?: names["en"]
}
