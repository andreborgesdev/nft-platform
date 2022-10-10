package com.template.contracts

import com.r3.corda.lib.tokens.contracts.commands.Create
import com.template.states.NFTState
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NFTContractTests {
    private val ledgerServices: MockServices = MockServices(listOf("com.template"))
    private val artist = TestIdentity(CordaX500Name("Artist", "TestLand", "US"))
    private val artRegistry = TestIdentity(CordaX500Name("ArtRegistry", "TestLand", "US"))

    @Test
    fun `URL cannot be empty`() {
        val tokenPass = NFTState("Test", Amount(100, Currency.getInstance("CHF")), "URL", artist.party, artRegistry.party, listOf(artist.party))
        val tokenFail = NFTState("Test", Amount(100, Currency.getInstance("CHF")), "", artist.party, artRegistry.party, listOf(artist.party))

        ledgerServices.ledger {
            transaction {
                output(NFTContract.ID, tokenFail)
                command(artist.publicKey, Create())
                this.fails()
            }
        }

        ledgerServices.ledger {
            transaction {
                output(NFTContract.ID, tokenPass)
                command(artist.publicKey, Create())
                this.verifies()
            }
        }
    }

    @ParameterizedTest(name = "{displayName}: name={0}, url={1}, price={2}, success={3}")
    @CsvSource(
        value = [
            "Bored Ape,google.com,1000,true",
            ",google.com,1000,false",
            "Bored Ape,,1000,false",
            "Bored Ape, google.com,,false"
        ]
    )
    fun `Update external entity`(nameArg: String, urlArg: String, priceArg: String, successArg: String) {
        val nftState = NFTState(nameArg, Amount(priceArg.toLong(), Currency.getInstance("CHF")), urlArg, artist.party, artRegistry.party, listOf(artist.party))

        try {
            ledgerServices.ledger {
                transaction {
                    output(NFTContract.ID, nftState)
                    command(artist.publicKey, Create())
                    verifies()
                }
            }

            assertTrue(successArg.toBoolean(), "Test was expected to fail, but it passed")
        } catch (e: Exception) {
            assertFalse(successArg.toBoolean(), "Test was expected to pass, but it failed: ${e.message}")
        }
    }
}