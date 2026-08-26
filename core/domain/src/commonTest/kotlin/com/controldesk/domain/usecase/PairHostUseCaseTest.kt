package com.controldesk.domain.usecase

import com.controldesk.domain.model.OsType
import com.controldesk.testing.FakeHostPreferences
import com.controldesk.testing.FakePairHostRemote
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairHostUseCaseTest {
    @Test
    fun rejectsEmptyName() = runTest {
        val useCase = PairHostUseCase(FakePairHostRemote(), FakeHostPreferences())
        val result = useCase("", "10.0.2.2", "8741", "123456", OsType.MACOS)
        assertTrue(result.isFailure)
    }

    @Test
    fun pairsAndSavesToken() = runTest {
        val prefs = FakeHostPreferences()
        val useCase = PairHostUseCase(FakePairHostRemote(token = "tok"), prefs)
        val result = useCase("Mac Mini", "10.0.2.2", "8741", "123456", OsType.MACOS).getOrThrow()
        assertEquals("10.0.2.2:8741", result.id)
        assertEquals("tok", result.token)
        assertEquals(2, result.buttons.size)
        assertEquals(1, prefs.getHosts().size)
    }

    @Test
    fun rePairSameIdUpdatesToken() = runTest {
        val prefs = FakeHostPreferences()
        val remote = FakePairHostRemote(token = "first")
        val useCase = PairHostUseCase(remote, prefs)
        useCase("Mac", "10.0.2.2", "8741", "123456", OsType.MACOS)
        remote.token = "second"
        val again = useCase("Mac", "10.0.2.2", "8741", "654321", OsType.MACOS).getOrThrow()
        assertEquals("second", again.token)
        assertEquals(1, prefs.getHosts().size)
    }
}
