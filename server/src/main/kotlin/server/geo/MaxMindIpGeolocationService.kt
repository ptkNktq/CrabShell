package server.geo

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/**
 * MaxMind GeoLite2-City `.mmdb` を引いて [GeoLocation] を返す実装。
 *
 * - 国名・地域名・都市名は **日本語ロケール** を優先し、欠けていれば英語 (`en`) → 現地語表記
 *   （マップ内の任意のブランクでない値）の順でフォールバックする。MaxMind がサポートロケールを
 *   追加した場合もコード変更なしで自動的に拾える。
 * - プライベート IP / 不正入力は [IpClassifier] で先に弾く（DB に含まれず無駄なクエリになるため）。
 * - DB に未登録の IP は MaxMind 公式推奨の [DatabaseReader.tryCity] を使い、例外ではなく
 *   `Optional.empty()` で受ける（ホットパスでスタックトレース生成のコストを払わない）。
 * - 全フィールドが取れなかった場合は null（=「位置情報なし」）を返す。
 * - 想定外の例外（DB 破損、I/O エラー等）は WARN ログのみで上位に伝播させない。
 *
 * `reader.tryCity()` は memory-mapped file 越しの同期 I/O（cold 時はページフォルト）が起きうるため、
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
                reader.tryCity(addr).orElse(null)?.toGeoLocation()
            } catch (e: CancellationException) {
                // コルーチンキャンセルは握り潰さず親に伝播させる。
                throw e
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

    /**
     * GeoLite2 の names マップから日本語 → 英語 → 現地語表記（マップ内任意のブランクでない値）の順で取得。
     * すべてブランク / マップ自体が空なら null。
     *
     * 「ja も en も無いが他言語のみ存在する」レコードはマイナーな現地名（例: `de` のみ持つ
     * ドイツの地名、`zh-CN` のみ持つ中国の地名）が大半で、その値は現地語表記として表示する
     * 価値がある。MaxMind のサポートロケール（`de` / `en` / `es` / `fr` / `ja` / `pt-BR` /
     * `ru` / `zh-CN`）が将来拡張されてもコード変更なしで自動追従するため、明示チェーンではなく
     * `names.values` の最終フォールバックを採用する。
     */
    private fun com.maxmind.geoip2.record.AbstractNamedRecord.localizedName(): String? =
        names["ja"]?.takeIf { it.isNotBlank() }
            ?: names["en"]?.takeIf { it.isNotBlank() }
            ?: names.values.firstOrNull { it.isNotBlank() }
}
