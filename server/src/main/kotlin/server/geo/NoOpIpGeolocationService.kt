package server.geo

/**
 * GeoLite2 DB ファイルが存在しない / ロードに失敗した環境で使うフォールバック。
 * 常に null を返してジオロケーションをスキップする。
 */
object NoOpIpGeolocationService : IpGeolocationService {
    override suspend fun lookup(ip: String?): GeoLocation? = null

    /** 保持するリソースが無いので何もしない */
    override fun close() = Unit
}
