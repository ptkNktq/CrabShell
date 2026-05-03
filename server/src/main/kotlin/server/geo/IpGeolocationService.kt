package server.geo

/**
 * IP アドレスから国・地域・都市を解決する。
 *
 * 解決できない場合（プライベート IP、DB に存在しない、DB 未ロード等）は null を返す。
 * 例外は内部で握りつぶしてログに残し、呼び出し側でのエラーハンドリングを不要にする。
 */
interface IpGeolocationService {
    fun lookup(ip: String?): GeoLocation?
}

/** GeoLite2-City から取得した粗い位置情報。各フィールドはローカライズ済み（日本語優先）。 */
data class GeoLocation(
    val country: String?,
    val region: String?,
    val city: String?,
)
