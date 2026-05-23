package com.example.smartfamilygrocerylist.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SecurityTest {

    @Test
    fun testTokenizer() {
        val original = "Organic Bananas 3.25% Extra"
        val expectedHash = Tokenizer.tokenize(original)
        
        // Canonical format should be lowercase, no spaces: "organicbananas3.25%extra"
        // Let's verify that another string with different spaces/casing produces the exact same hash
        val variation = "  ORGANIC   bananas 3.25%  ExTra  "
        val variationHash = Tokenizer.tokenize(variation)
        
        assertEquals(expectedHash, variationHash)
        
        // Verify different string has different hash
        val different = "Organic Milk"
        assertNotEquals(expectedHash, Tokenizer.tokenize(different))
    }

    @Test
    fun testCryptography() {
        val plainText = "My Secret Grocery Item"
        val passphrase = "family-passphrase-123"

        // Encrypt item name
        val cipherText = Cryptography.encrypt(plainText, passphrase)
        assertNotEquals(plainText, cipherText)

        // Decrypt with correct passphrase
        val decryptedText = Cryptography.decrypt(cipherText, passphrase)
        assertEquals(plainText, decryptedText)

        // Decrypt with wrong passphrase
        val decryptedWrong = Cryptography.decrypt(cipherText, "wrong-passphrase")
        assertEquals("Decryption Error (Key Mismatch)", decryptedWrong)

        // Decrypt with empty passphrase (should return ciphertext directly)
        val decryptedEmpty = Cryptography.decrypt(cipherText, "")
        assertEquals(cipherText, decryptedEmpty)

        // Test encryption with empty passphrase
        val encryptedEmpty = Cryptography.encrypt(plainText, "")
        assertEquals(plainText, encryptedEmpty)
    }

    @Test
    fun testVoiceSimulationDecryption() {
        // Voice simulation items start with ENC_ and contain base64 encoded plaintext
        // Example: ENC_T3JnYW5pYyBNaWxr
        val originalText = "Organic Milk"
        val simulatedVoiceCipher = "ENC_T3JnYW5pYyBNaWxr" // Base64 for "Organic Milk"

        // Decrypt simulated voice item should return "Organic Milk" even with non-empty passphrase
        val decryptedText = Cryptography.decrypt(simulatedVoiceCipher, "any-passphrase")
        assertEquals(originalText, decryptedText)
    }
}
