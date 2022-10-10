package com.template

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.template.flows.IssueFiatCurrencyFlow
import junit.framework.Assert.assertEquals
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class IssueFiatCurrencyFlowTests {
    private var network: MockNetwork? = null
    private var bankNode: StartedMockNode? = null
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

        bankNode = network!!.createPartyNode(CordaX500Name("Bank", "Zurich", "CH"))
        customerNode = network!!.createPartyNode(CordaX500Name("Customer", "New York", "US"))
        network!!.runNetwork()
    }

    @After
    fun tearDown() {
        network!!.stopNodes()
    }

    @Test
    fun fiatCurrencyIssuanceCheckIfCustomerHasAllIssuedCurrencyOnVault() {
        val customerNodeParty = customerNode!!.info.singleIdentity()
        val issueFiatCurrencyFlow = IssueFiatCurrencyFlow(1000, "CHF", customerNodeParty)
        val future = bankNode!!.startFlow(issueFiatCurrencyFlow)
        network!!.runNetwork()

        val signedTransaction = future.get()
        val outputStateAmount = (signedTransaction.tx.outputStates[0] as FungibleToken).amount
        val customerFungibleTokenStates = customerNode!!.services.vaultService.queryBy<FungibleToken>().states
        val amountRecordedOnTheCustomerVault = customerFungibleTokenStates[0].state.data.amount

        assertEquals(outputStateAmount.quantity, amountRecordedOnTheCustomerVault.quantity)
    }
}