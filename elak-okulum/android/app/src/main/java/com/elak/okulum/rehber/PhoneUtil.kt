package com.elak.okulum.rehber

object PhoneUtil {
    fun normalize(raw: String?): String {
        var digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.startsWith("0090")) digits = digits.drop(4)
        if (digits.startsWith("90") && digits.length >= 12) digits = digits.drop(2)
        if (digits.startsWith("0") && digits.length >= 11) digits = digits.drop(1)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun international(raw: String?): String {
        val n = normalize(raw)
        return if (n.length == 10) "90" + n else n
    }

    fun display(raw: String?): String {
        val n = normalize(raw)
        return if (n.length == 10) {
            "0" + n.substring(0, 3) + " " + n.substring(3, 6) + " " +
                n.substring(6, 8) + " " + n.substring(8)
        } else raw.orEmpty()
    }
}
