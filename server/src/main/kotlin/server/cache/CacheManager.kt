package server.cache

/** 全キャッシュの一括クリアを集約するマネージャー */
class CacheManager(
    private val cacheables: List<Cacheable>,
) {
    fun clearAll(): List<String> {
        cacheables.forEach { it.clearCache() }
        return cacheables.map { it.cacheName }
    }
}
