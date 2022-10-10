package com.template

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.template.flows.BuyNftFlow
import com.template.flows.CreateNftFlow
import com.template.flows.IssueFiatCurrencyFlow
import com.template.states.NFTState
import junit.framework.Assert.assertTrue
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test
import java.math.BigDecimal
import java.util.Currency.getInstance
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DriverBasedTest {
    private val artist = TestIdentity(CordaX500Name("Artist", "London", "GB"))
    private val artRegistry = TestIdentity(CordaX500Name("ArtRegistry", "New York", "US"))
    private val customer = TestIdentity(CordaX500Name("Customer", "New York", "US"))
    private val bank = TestIdentity(CordaX500Name("Bank", "Zurich", "CH"))

    private val NFT_PRICE = 1000L;

    @Test
    fun `node test`() = withDriver {
        val (artistHandle, artRegistryHandle) = startNodes(artist, artRegistry)

        // This is a very basic test: in practice tests would be starting flows, and verifying the states in the vault
        // and other important metrics to ensure that your CorDapp is working as intended.
        assertEquals(artist.name, artRegistryHandle.resolveName(artist.name))
        assertEquals(artRegistry.name, artistHandle.resolveName(artRegistry.name))
    }

    @Test
    fun `create and issue NFT`() = withDriver {
        val (artistHandle, customerHandle) = startNodes(artist, customer, artRegistry)

        val transactionResponse = artistHandle.rpc
            .startFlow(::CreateNftFlow, "Bored Ape", Amount(NFT_PRICE, getInstance("CHF")), "google.com")
            .returnValue
            .getOrThrow()

        val nonFungibleTokenId = (transactionResponse.tx.outputs.first().data as NonFungibleToken).token.tokenIdentifier
        val artistQueryResultNftState = artistHandle.rpc.vaultQueryBy<NFTState>().states.single()
        val artistQueryResultNft = artistHandle.rpc.vaultQueryBy<NonFungibleToken>().states.single()
        val customerQueryResult = customerHandle.rpc.vaultQueryBy<NFTState>().states.single()
        val nftLinearIdArtistVault = artistQueryResultNftState.state.data.linearId
        val nftLinearIdCustomerVault = customerQueryResult.state.data.linearId

        // Check if NFT was created and is on the vault state of both the artist and the customer
        assertEquals(nftLinearIdArtistVault.id.toString(), nonFungibleTokenId)
        assertEquals(nftLinearIdCustomerVault.id.toString(), nonFungibleTokenId)
        assertEquals(nftLinearIdCustomerVault.id, nftLinearIdArtistVault.id)
        // Check if the artist is the owner of the NFT
        assertEquals(artistHandle.nodeInfo.singleIdentity(), artistQueryResultNft.state.data.holder)
        // Check if both signatures (form the artist and art registry) were collected
        assertEquals(2, transactionResponse.sigs.size)
        assertTrue(transactionResponse.getMissingSigners().isEmpty())
    }

    @Test
    fun `issue fiat currency`() = withDriver {
        val (bankHandle, customerHandle) = startNodes(bank, customer)

        bankHandle.rpc
            .startFlow(::IssueFiatCurrencyFlow, NFT_PRICE, "CHF", customerHandle.nodeInfo.singleIdentity())
            .returnValue
            .getOrThrow()

        val customerQueryResult = customerHandle.rpc.vaultQueryBy<FungibleToken>().states.single()

        // Check if the data for the amount corresponds to the one we used to create the fiat currency
        assertEquals(1000L, customerQueryResult.state.data.amount.quantity)
        assertEquals(BigDecimal.valueOf(0.01),  customerQueryResult.state.data.amount.displayTokenSize)
    }

    @Test
    fun `buy nft`() = withDriver {
        val (artistHandle, bankHandle, customerHandle) = startNodes(artist, bank, customer, artRegistry)

        bankHandle.rpc
            .startFlow(::IssueFiatCurrencyFlow, NFT_PRICE, "CHF", customerHandle.nodeInfo.singleIdentity())
            .returnValue
            .getOrThrow()

        artistHandle.rpc
            .startFlow(::CreateNftFlow, "Bored Ape", Amount(NFT_PRICE, getInstance("CHF")), "Image URL")
            .returnValue
            .getOrThrow()

        val nftId = artistHandle.rpc.vaultQueryBy<NFTState>().states.single().state.data.linearId.toString()

        val transactionResponse = customerHandle.rpc
            .startFlow(::BuyNftFlow, nftId)
            .returnValue
            .getOrThrow()

        val nonFungibleTokenId = (transactionResponse.tx.outputs[0].data as NonFungibleToken).token.tokenIdentifier
        val customerQueryResultNftState = customerHandle.rpc.vaultQueryBy<NFTState>().states.single()
        val customerQueryResultNft = customerHandle.rpc.vaultQueryBy<NonFungibleToken>().states.single()
        val artistQueryResultFungibleToken = artistHandle.rpc.vaultQueryBy<FungibleToken>().states.single()
        val nftLinearIdCustomerVault = customerQueryResultNftState.state.data.linearId

        // Check if the moved NFT is the correct one
        assertEquals(nftLinearIdCustomerVault.id.toString(), nonFungibleTokenId)
        // Check if the customer is the owner of the NFT
        assertEquals(customerHandle.nodeInfo.singleIdentity(), customerQueryResultNft.state.data.holder)
        // Check if artist got the funds form the selling
        assertEquals(NFT_PRICE, artistQueryResultFungibleToken.state.data.amount.quantity)
    }

    @Test
    fun `buy nft with insufficient funds`() = withDriver {
        val (artistHandle, bankHandle, customerHandle) = startNodes(artist, bank, customer, artRegistry)

        bankHandle.rpc
            .startFlow(::IssueFiatCurrencyFlow, 1L, "CHF", customerHandle.nodeInfo.singleIdentity())
            .returnValue
            .getOrThrow()

        artistHandle.rpc
            .startFlow(::CreateNftFlow, "Bored Ape", Amount(NFT_PRICE, getInstance("CHF")), "Image URL")
            .returnValue
            .getOrThrow()

        val nftId = artistHandle.rpc.vaultQueryBy<NFTState>().states.single().state.data.linearId.toString()

        assertFailsWith(
            CordaRuntimeException::class,
            message = "The amount paid is not the same as the price of the NFT.",
            block = {
                customerHandle.rpc
                    .startFlow(::BuyNftFlow, nftId)
                    .returnValue
                    .getOrThrow()
            }
        )
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = true,
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.template.contracts"),
                TestCordapp.findCordapp("com.template.flows")
            ),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList())
        )
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name) }
        .waitForAll()
}