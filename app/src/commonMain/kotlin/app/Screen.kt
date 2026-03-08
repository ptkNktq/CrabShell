package app

enum class Screen(
    val title: String,
    val path: String,
    val adminOnly: Boolean = false,
) {
    Dashboard("ダッシュボード", "/dashboard"),
    Feeding("ごはん", "/feeding"),
    Payment("お支払い", "/payment"),
    Report("家計レポート", "/report"),
    Money("お金の管理", "/money", adminOnly = true),
    Overpayment("過払い額", "/overpayment", adminOnly = true),
    Quest("クエスト", "/quest"),
    PetManagement("ペット管理", "/pet-management"),
    Settings("設定", "/settings"),
    ;

    companion object {
        fun fromPath(path: String): Screen = entries.find { it.path == path } ?: Dashboard
    }
}
