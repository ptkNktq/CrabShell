package server.cache

/** キャッシュを持つコンポーネントの横断的関心事を分離するインターフェース */
interface Cacheable {
    val cacheName: String

    fun clearCache()
}
