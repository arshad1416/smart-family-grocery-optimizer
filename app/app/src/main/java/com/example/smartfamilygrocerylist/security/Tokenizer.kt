package com.example.smartfamilygrocerylist.security

import java.security.MessageDigest

object Tokenizer {
    /**
     * Standardizes an item name (lowercase, removes whitespace) and computes its SHA-256 hash.
     * Example: "Organic Bananas" -> "organicbananas" -> "e3b0c442..."
     */
    fun tokenize(name: String): String {
        val normalized = normalizeName(name)
        return sha256(normalized)
    }

    fun hashPassphrase(passphrase: String): String {
        val canonical = passphrase.lowercase().replace("\\s".toRegex(), "")
        return sha256(canonical)
    }

    private fun normalizeName(name: String): String {
        val lower = name.lowercase().trim()
        return when {
            // Produce
            lower.contains("banana") -> "organicbananas"
            lower.contains("apple") -> "honeycrispapples"
            lower.contains("spinach") -> "babyspinach"
            lower.contains("tomato") -> "romatomatoes"
            lower.contains("avocado") -> "avocados"
            
            // Dairy & Eggs
            lower.contains("egg") -> "largeeggsbrown"
            lower.contains("milk") -> "organicmilk3.25%"
            lower.contains("butter") -> "saltedbutter"
            lower.contains("yogurt") -> "greekyogurtplain"
            lower.contains("cheese") -> "cheddarcheeseblock"
            
            // Pantry
            lower.contains("honey") -> "organichoney"
            lower.contains("peanut butter") -> "peanutbutter"
            lower.contains("oat") || lower.contains("oatmeal") -> "oldfashionedrolledoats"
            lower.contains("olive oil") -> "extravirginoliveoil"
            lower.contains("bread") -> "wholewheatbread"
            
            // Meat & Seafood
            lower.contains("chicken") -> "chickenbreastsboneless"
            lower.contains("beef") -> "leangroundbeef"
            lower.contains("salmon") -> "atlanticsalmonfillet"
            
            // Bakery
            lower.contains("croissant") -> "buttercroissants"
            lower.contains("cookie") -> "chocolatechipcookies"
            
            else -> lower.replace("\\s".toRegex(), "")
        }
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
