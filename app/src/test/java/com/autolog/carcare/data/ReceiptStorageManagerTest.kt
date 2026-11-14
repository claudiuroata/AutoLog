package com.autolog.carcare.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptStorageManagerTest {

    @Test
    fun sanitizeFileName_blocksPathTraversalAndWindowsSlashes() {
        assertEquals("passwd", ReceiptStorageManager.sanitizeFileName("../../etc/passwd"))
        assertEquals("invoice.pdf", ReceiptStorageManager.sanitizeFileName("..\\..\\secret\\invoice.pdf"))
        assertFalse(ReceiptStorageManager.sanitizeFileName("..receipt..name..txt").contains(".."))
    }

    @Test
    fun sanitizeFileName_replacesUnsafeCharactersCapsLengthAndHandlesBlank() {
        val sanitized = ReceiptStorageManager.sanitizeFileName("Factura Revizie #12 (2026)!.pdf")
        assertEquals("Factura_Revizie__12__2026__.pdf", sanitized)

        val longName = "a".repeat(150) + ".jpg"
        assertEquals(100, ReceiptStorageManager.sanitizeFileName(longName).length)

        assertEquals("receipt_file", ReceiptStorageManager.sanitizeFileName(""))
        assertEquals("receipt_file", ReceiptStorageManager.sanitizeFileName("folder///"))
    }

    @Test
    fun isPdf_detectsPdfExtensionCaseInsensitively() {
        assertTrue(ReceiptStorageManager.isPdf("invoice_01.pdf"))
        assertTrue(ReceiptStorageManager.isPdf("ITP_CERTIFICATE.PDF"))
        assertFalse(ReceiptStorageManager.isPdf("photo.jpg"))
        assertFalse(ReceiptStorageManager.isPdf("scan.png"))
        assertFalse(ReceiptStorageManager.isPdf("pdf_receipt.webp"))
    }
}
