package com.example.smartfamilygrocerylist.security

import java.security.MessageDigest

object Tokenizer {
    /**
     * Standardizes an item name (lowercase, removes whitespace) and computes its SHA-256 hash.
     * Example: "Organic Bananas" -> "organicbananas" -> "e3b0c442..."
     */
    fun tokenize(name: String): String {
        val canonical = name.lowercase().replace("\\s".toRegex(), "")
        return sha256(canonical)
    }

    private fun sha256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
