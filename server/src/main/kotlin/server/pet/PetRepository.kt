package server.pet

import model.Pet

/** ペットデータのリポジトリインターフェース */
interface PetRepository {
    suspend fun getPets(): List<Pet>

    /** pets コレクションにデフォルトペットが存在しなければ作成する（ブロッキング: Application 初期化時用） */
    fun seedDefaultPet()
}
