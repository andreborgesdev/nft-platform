package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class IssueFiatCurrencyFlow(private val amount: Long,
                            private val currency: String,
                            private val recipient: Party
) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val bank = ourIdentity

        checkIfInitiatorNodeIsBank(bank)

        // Create an instance of the fiat currency TokenType
        val fiatCurrencyToken = getInstance(currency)

        // Create an instance of IssuedTokenType for the fiat currency
        val issuedTokenType = fiatCurrencyToken issuedBy bank // Same as having IssuedTokenType(bank, token)

        // Create an instance of FungibleToken for the fiat currency to be issued
        val fungibleToken = FungibleToken(Amount(amount, issuedTokenType), recipient)

        // Issue the tokens for the fiat currency
        return subFlow(IssueTokens(listOf(fungibleToken), listOf(recipient)))
    }

    private fun checkIfInitiatorNodeIsBank(issuer: Party) {
        val bank =
            serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name("Bank", "Zurich", "CH"))!!

        if (issuer != bank) {
            throw FlowException("The bank is the only party that is allowed to issue fiat currency.")
        }
    }
}