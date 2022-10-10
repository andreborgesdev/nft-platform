package com.template

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.template.flows.BuyNftFlow
import com.template.flows.CreateNftFlow
import com.template.flows.IssueFiatCurrencyFlow
import junit.framework.Assert.assertTrue
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class BuyNftFlowTest {
    private var network: MockNetwork? = null
    private var artistNode: StartedMockNode? = null
    private var customerNode: StartedMockNode? = null
    private var bankNode: StartedMockNode? = null
    private var artRegistryNode: StartedMockNode? = null

    @Before
    fun setup() {
        network = MockNetwork(
            MockNetworkParameters(cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.template.flows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows")),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB")))
            )
        )

        artistNode = network!!.createPartyNode(CordaX500Name("Artist", "London", "GB"))
        artRegistryNode = network!!.createPartyNode(CordaX500Name("ArtRegistry", "New York", "US"))
        customerNode = network!!.createPartyNode(CordaX500Name("Customer", "New York", "US"))
        bankNode = network!!.createPartyNode(CordaX500Name("Bank", "Zurich", "CH"))
        network!!.runNetwork()
    }

    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun buyNFT() {
        val customerNodeParty = customerNode!!.info.singleIdentity()
        // Call the dependency flows first to populate the vaults. For doing this it is more advisable to use a
        // function to already pre-populate the vaults instead of calling the dependencies flows because this way
        // we are doing an E2E test and not an unit test.
        val nftId = createNFT()
        issueFiatCurrency()

        val customerFungibleTokenStatesBefore = customerNode!!.services.vaultService.queryBy<FungibleToken>().states

        // Buy the NFT
        val buyNFTFlow = BuyNftFlow(nftId)
        val future = customerNode!!.startFlow(buyNFTFlow)
        network!!.runNetwork()

        val signedTransaction = future.getOrThrow()

        val customerFungibleTokenStatesAfter = customerNode!!.services.vaultService.queryBy<FungibleToken>().states

        val artistFungibleTokenStates = artistNode!!.services.vaultService.queryBy<FungibleToken>().states
        val amountRecordedOnTheArtistVault = artistFungibleTokenStates[0].state.data.amount

        // Check if customer is the new holder of the NFT
        assertEquals(customerNodeParty, (signedTransaction.tx.outputStates[0] as NonFungibleToken).holder)
        // Check if money was deducted from buyer
        assertEquals(1, customerFungibleTokenStatesBefore.size)
        assertEquals(0, customerFungibleTokenStatesAfter.size)
        // Check if money was added to seller
        assertEquals(1, artistFungibleTokenStates.size)
        assertTrue(amountRecordedOnTheArtistVault.quantity > 0L)
    }

    private fun issueFiatCurrency() {
        val customerNodeParty = customerNode!!.info.singleIdentity()
        val issueFiatCurrencyFlow = IssueFiatCurrencyFlow(1000, "CHF", customerNodeParty)
        bankNode!!.startFlow(issueFiatCurrencyFlow)
        network!!.runNetwork()
    }

    private fun createNFT(): String {
        val createAndIssueNFTFlow = CreateNftFlow("NFT Name", Amount(1000L, Currency.getInstance("CHF")), "Image URL")
        val future = artistNode!!.startFlow(createAndIssueNFTFlow)
        network!!.runNetwork()
        val signedTransaction = future.get()
        return (signedTransaction.tx.outputs[0].data as NonFungibleToken).token.tokenIdentifier
    }
}