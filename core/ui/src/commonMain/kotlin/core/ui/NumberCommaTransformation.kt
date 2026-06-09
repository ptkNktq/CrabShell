package core.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** 金額入力フィールドに3桁カンマ区切りを表示するビジュアル変換。state はカンマなし生数字文字列のまま維持される。 */
object NumberCommaTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatAmountInput(text.text)
        return TransformedText(AnnotatedString(formatted), CommaOffsetMapping(formatted))
    }
}

private class CommaOffsetMapping(
    private val formatted: String,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        var digitsCount = 0
        var i = 0
        while (i < formatted.length && digitsCount < offset) {
            if (formatted[i] != ',') digitsCount++
            i++
        }
        return i
    }

    override fun transformedToOriginal(offset: Int): Int {
        var digitsSeen = 0
        for (i in 0 until minOf(offset, formatted.length)) {
            if (formatted[i] != ',') digitsSeen++
        }
        return digitsSeen
    }
}
