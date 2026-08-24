package com.mediavault.core.domain.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyDecisionTest {

    @Test
    fun `warn and block decisions carry their reason`() {
        val warn = NetworkPolicyDecision.Warn("Exceeds per-download mobile limit")
        val block = NetworkPolicyDecision.Block("Daily budget exhausted")

        assertEquals("Exceeds per-download mobile limit", warn.reason)
        assertEquals("Daily budget exhausted", block.reason)
    }

    @Test
    fun `allow and queue-for-wifi are singletons`() {
        assertTrue(NetworkPolicyDecision.Allow === NetworkPolicyDecision.Allow)
        assertTrue(NetworkPolicyDecision.QueueForWifi === NetworkPolicyDecision.QueueForWifi)
    }
}
