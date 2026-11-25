package com.karelisio.patoune

import com.karelisio.patoune.domain.Stock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StockTest {
    @Test fun decrementByPuffs() {
        assertEquals(118.0, Stock.decrement(120.0, 2.0)!!, 0.0)
        assertEquals(0.0, Stock.decrement(1.0, 2.0)!!, 0.0)
        assertNull(Stock.decrement(null, 1.0))
    }

    @Test fun decrementHalfTablet() {
        assertEquals(9.5, Stock.decrement(10.0, 0.5)!!, 0.0)
    }

    @Test fun lowStockAlert() {
        assertTrue(Stock.isLow(20.0, 20.0))
        assertFalse(Stock.isLow(21.0, 20.0))
        assertFalse(Stock.isLow(5.0, null))
    }

    @Test fun daysLeftAndRenewal() {
        assertEquals(30, Stock.daysLeft(120.0, 2.0, 2))
        val today = LocalDate.of(2026, 5, 1)
        assertTrue(Stock.renewalDue(null, today, 5))
        assertTrue(Stock.renewalDue(LocalDate.of(2026, 5, 6), today, 60))
        assertFalse(Stock.renewalDue(LocalDate.of(2026, 6, 1), today, 60))
    }
}
