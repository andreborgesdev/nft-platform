package com.template

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.template.flows.CreateNftFlow
import com.template.states.NFTState
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

class CreateNftFlowTests {
    private var network: MockNetwork? = null
    private var artistNode: StartedMockNode? = null
    private var artRegistryNode: StartedMockNode? = null
    private var customerNode: StartedMockNode? = null

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
        network!!.runNetwork()
    }

    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun nftTokenCreationAndIssuanceWasCoSignedByArtRegistry() {
        val createAndIssueNFTFlow = CreateNftFlow("NFT Name", Amount(1000L, Currency.getInstance("CHF")), "Image URL")
        val future = artistNode!!.startFlow(createAndIssueNFTFlow)
        network!!.runNetwork()

        val signedTransaction = future.get()

        assertEquals(2, signedTransaction.sigs.size)
        assertTrue(signedTransaction.getMissingSigners().isEmpty())
    }

    @Test
    fun nftTokenCreationAndIssuanceWasCommunicatedToCustomer() {
        val createAndIssueFlow = CreateNftFlow("NFT Name", Amount(1000L, Currency.getInstance("CHF")), "Image URL")
        val future = artistNode!!.startFlow(createAndIssueFlow)
        network!!.runNetwork()

        val signedTransaction = future.get()
        val nonFungibleTokenId = (signedTransaction.tx.outputs[0].data as NonFungibleToken).token.tokenIdentifier
        val storedNonFungibleTokenArtist = artistNode!!.services.vaultService.queryBy<NFTState>().states
        val storedNonFungibleTokenCustomer = customerNode!!.services.vaultService.queryBy<NFTState>().states
        val nftLinearIdArtistVault = storedNonFungibleTokenArtist[0].state.data.linearId
        val nftLinearIdCustomerVault = storedNonFungibleTokenCustomer[0].state.data.linearId

        assertEquals(nftLinearIdArtistVault.id.toString(), nonFungibleTokenId)
        assertEquals(nftLinearIdCustomerVault.id.toString(), nonFungibleTokenId)
        assertEquals(nftLinearIdCustomerVault.id, nftLinearIdArtistVault.id)
    }
}