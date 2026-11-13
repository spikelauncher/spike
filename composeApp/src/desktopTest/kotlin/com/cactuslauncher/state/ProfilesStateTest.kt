package com.cactuslauncher.state

import com.cactuslauncher.game.GameService
import com.cactuslauncher.model.PlayerProfile
import com.cactuslauncher.model.ProfileSnapshot
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlin.test.*

class ProfilesStateTest {
    private class Service : GameService by unusedService() {
        var account: String? = "ElyPlayer"
        var fail = false
        var selectionGate: CompletableDeferred<Unit>? = null
        var selections = 0
        var signOuts = 0
        var addedName: String? = null
        val snapshot = ProfileSnapshot(listOf(PlayerProfile("one", "Steve"), PlayerProfile("two", "Alex")), "two")
        override fun externalAccountName() = account
        override fun signOutExternal() { signOuts++; account = null }
        override suspend fun selectProfile(id: String): ProfileSnapshot {
            selections++
            selectionGate?.await()
            check(!fail) { "save failed" }
            return snapshot.copy(activeId = id)
        }
        override suspend fun addProfile(name: String): ProfileSnapshot {
            check(!fail) { "save failed" }
            addedName = name
            return snapshot
        }
        override suspend fun signInExternal(apiRoot: String, username: String, password: String): String {
            check(!fail) { "authentication failed" }
            account = "NewElyPlayer"
            return account!!
        }
    }

    @Test fun offlineSelectionClearsExternalSessionAndClosesDialog() = runBlocking {
        val service = Service()
        val state = ProfilesState(service, this, AppMessages(service))
        state.open = true
        state.externalApiRoot = "https://account.ely.by/api/authlib-injector"
        state.select("two")
        yield()
        assertEquals("two", state.profiles?.activeId)
        assertNull(state.externalAccountName)
        assertNull(state.launchAuthServer)
        assertNull(service.account)
        assertEquals(1, service.signOuts)
        assertFalse(state.open)
        assertFalse(state.busy)
    }

    @Test fun failedSelectionKeepsPreviousAccountAndAllowsRetry() = runBlocking {
        val service = Service().apply { fail = true }
        val state = ProfilesState(service, this, AppMessages(service))
        state.open = true
        state.externalApiRoot = "https://account.ely.by/api/authlib-injector"
        state.select("two")
        yield()
        assertEquals("save failed", state.error)
        assertEquals("ElyPlayer", state.externalAccountName)
        assertNotNull(state.launchAuthServer)
        assertEquals(0, service.signOuts)
        assertTrue(state.open)
        assertFalse(state.busy)
        service.fail = false
        state.select("two")
        yield()
        assertNull(state.error)
        assertNull(state.launchAuthServer)
    }

    @Test fun repeatedClicksDoNotStartParallelSelections() = runBlocking {
        val service = Service().apply { selectionGate = CompletableDeferred() }
        val state = ProfilesState(service, this, AppMessages(service))
        state.select("two")
        state.select("one")
        yield()
        assertEquals(1, service.selections)
        assertTrue(state.busy)
        service.selectionGate!!.complete(Unit)
        yield()
        assertEquals("two", state.profiles?.activeId)
        assertFalse(state.busy)
    }

    @Test fun addingOfflineProfileActivatesItAndClearsExternalSession() = runBlocking {
        val service = Service()
        val state = ProfilesState(service, this, AppMessages(service))
        state.open = true
        state.add(" Alex ")
        yield()
        assertEquals("Alex", service.addedName)
        assertEquals("two", state.profiles?.activeId)
        assertNull(state.externalAccountName)
        assertFalse(state.open)
    }

    @Test fun failedAuthenticationKeepsAccountAndSuccessfulRetryActivatesEly() = runBlocking {
        val service = Service().apply { fail = true }
        val state = ProfilesState(service, this, AppMessages(service))
        state.open = true
        val endpoint = "https://account.ely.by/api/authlib-injector"
        state.signIn(endpoint, "name", "password")
        yield()
        assertEquals("authentication failed", state.error)
        assertEquals("ElyPlayer", state.externalAccountName)
        assertTrue(state.open)
        service.fail = false
        state.signIn(endpoint, "name", "password")
        yield()
        assertEquals("NewElyPlayer", state.externalAccountName)
        assertEquals(endpoint, state.launchAuthServer)
        assertNull(state.error)
        assertFalse(state.open)
    }

    companion object {
        private fun unusedService(): GameService = Proxy.newProxyInstance(
            GameService::class.java.classLoader, arrayOf(GameService::class.java),
        ) { _, method, _ -> error("Unexpected service call: ${method.name}") } as GameService
    }
}
