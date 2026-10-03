package server.mcp

import java.util.concurrent.ConcurrentHashMap

/**
 * アクセストークンの `sub`（WorkOS のユーザー ID）を Firebase の uid に変換する。
 *
 * WorkOS のユーザー ID と external_id（= uid）の対応は変わらないため、一度引いた結果はメモリに保持する。
 * キャッシュに載るのは署名検証を通ったトークンの `sub` だけなので、外部から無制限に増やされることはない。
 */
class McpUserResolver(
    private val workOsClient: WorkOsClient,
) {
    private val cache = ConcurrentHashMap<String, String>()

    /**
     * @return uid。WorkOS 上にユーザーがいない、または external_id が未設定の場合は null
     * @throws WorkOsApiException WorkOS への問い合わせに失敗した場合
     */
    suspend fun resolveUid(workOsUserId: String): String? {
        cache[workOsUserId]?.let { return it }
        val uid = workOsClient.getExternalId(workOsUserId) ?: return null
        cache[workOsUserId] = uid
        return uid
    }
}
