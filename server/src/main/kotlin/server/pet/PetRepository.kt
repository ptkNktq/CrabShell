package server.pet

import model.Pet

/** ペットデータのリポジトリインターフェース */
interface PetRepository {
    suspend fun getPets(): List<Pet>

    /** 指定ユーザーが指定ペットのメンバーかどうかを返す */
    suspend fun isMember(
        petId: String,
        uid: String,
    ): Boolean

    /** pets コレクションにデフォルトペットが存在しなければ作成する（ブロッキング: Application 初期化時用） */
    fun seedDefaultPet()
}
