package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentNonFungibleToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.money.FiatCurrency.Companion.getInstance
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import com.template.helpers.NotaryHelper
import com.template.states.NFTState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class BuyNftFlow(private val nftId: String) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val buyer = ourIdentity

        checkIfInitiatorNodeIsCustomer(buyer)

        val notary = NotaryHelper.getNotary(serviceHub)

        // Fetch the NFT state from the Customer's vault using the vault query
        val nftUuid = UniqueIdentifier.fromString(nftId)
        val inputCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(nftUuid))
        val nftStateAndRef = serviceHub.vaultService.queryBy<NFTState>(criteria = inputCriteria).states.single()
        val nftState = nftStateAndRef.state.data

        // Fetch the current NFT holder (seller)
        val tokenIdentifierIndex = PersistentNonFungibleToken::tokenIdentifier.equal(nftUuid.toString())
        val nftTokenIdentifierCustomCriteria = VaultCustomQueryCriteria(tokenIdentifierIndex)
        val nftTokenStateAndRef = serviceHub.vaultService.queryBy<NonFungibleToken>(nftTokenIdentifierCustomCriteria).states.single()
        val seller = nftTokenStateAndRef.state.data.holder.toParty(serviceHub)

        // Build the transaction builder
        val txBuilder = TransactionBuilder(notary)

        // Create a move token proposal for the NFT using the helper function provided by Token SDK. This would create
        // the movement proposal and would be committed in the ledgers of parties once the transaction in finalized.
        addMoveNonFungibleTokens(txBuilder, serviceHub, nftState.toPointer(nftState.javaClass), buyer)

        // Initiate a flow session with the seller
        val sellerSession = initiateFlow(seller)

        // Create instance of the fiat currency token amount
        val priceToken = Amount(nftState.price.quantity, getInstance(nftState.price.token.currencyCode))

        // Generate the move of the fiat currency token from the buyer to the seller to pay for the NFT
        val inputsAndOutputs = DatabaseTokenSelection(serviceHub).generateMove(listOf(Pair(seller, priceToken)), buyer)
        val inputs = inputsAndOutputs.first
        val moneyReceived = inputsAndOutputs.second

        // Create a fiat currency proposal for the NFT using the helper function provided by Token SDK.
        addMoveTokens(txBuilder, inputs, moneyReceived)

        // Sign the transaction with the buyer's private key
        val initialSignedTx = serviceHub.signInitialTransaction(txBuilder)

        // Call the CollectSignaturesFlow to receive the seller's signature
        val ftx = subFlow(CollectSignaturesFlow(initialSignedTx, listOf(sellerSession)))

        // Call the finality flow to notarise the transaction
        val stx = subFlow(FinalityFlow(ftx, listOf(sellerSession)))

        // Distribution list is a list of identities that should receive updates
        subFlow(UpdateDistributionListFlow(stx))

        return stx
    }

    private fun checkIfInitiatorNodeIsCustomer(buyer: Party) {
        val customer =
            serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name("Customer", "New York", "US"))!!

        if (buyer != customer) {
            throw FlowException("The customer is the only party that is allowed to buy an NFT.")
        }
    }
}

@InitiatedBy(BuyNftFlow::class)
class NFTSaleFlowResponder(val counterPartySession: FlowSession) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        // Signing
        subFlow(object : SignTransactionFlow(counterPartySession) {
            @Throws(FlowException::class)
                override fun checkTransaction(stx: SignedTransaction) {
                val fungibleTokenOutputStates = stx.tx.outputStates.filterIsInstance<FungibleToken>().firstOrNull()
                val nftOutputStates = stx.tx.outputStates.filterIsInstance<NonFungibleToken>().firstOrNull()
                val nftStateOutputStates = serviceHub.vaultService.queryBy<NFTState>().states.firstOrNull()

                if (fungibleTokenOutputStates == null)
                    throw FlowException("There was no payment sent to the seller.")

                if (fungibleTokenOutputStates.amount.quantity != nftStateOutputStates!!.state.data.price.quantity)
                    throw FlowException("The amount paid is not the same as the price of the NFT.")

                if (fungibleTokenOutputStates.holder != ourIdentity)
                    throw FlowException("The payment was sent to an entity that is not the owner of the NFT.")

                if (nftOutputStates == null)
                    throw FlowException("The NFT was not transferred to the buyer.")

                if (nftOutputStates.holder != counterPartySession.counterparty)
                    throw FlowException("The NFT was transferred to an identity that is not the the buyer (flow initiator).")
            }
        })

        return subFlow(ReceiveFinalityFlow(counterPartySession))
    }
}