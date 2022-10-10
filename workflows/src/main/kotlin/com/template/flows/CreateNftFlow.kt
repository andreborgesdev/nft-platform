package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.template.helpers.NotaryHelper
import com.template.states.NFTState
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class CreateNftFlow(private val name: String,
                    private val price: Amount<Currency>,
                    private val url: String) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val artist = ourIdentity

        checkIfInitiatorNodeIsArtist(artist)

        // Since we just have one art registry, we don´t need to get it more dynamically or pass it through the constructor
        val artRegistry =
            serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name("ArtRegistry", "New York", "US"))!!

        val observers = getAllCustomerNodes()
        val observersSession: List<FlowSession> = observers.map { initiateFlow(it) }
        val notary = NotaryHelper.getNotary(serviceHub)

        // Construct the output NFTState
        val nftState = NFTState(name, price, url, artist, artRegistry, listOf(artist))

        // Wrap it with transaction state specifying the notary.
        // The notary provided here will be used in all future actions of this token.
        val transactionState = nftState withNotary notary

        // Initiate the flow using the art registry as the other party involved in the transaction
        val artRegistrySession = initiateFlow(artRegistry)

        // Using the build-in flow to create an evolvable NFT token type in the ledger
        subFlow(CreateEvolvableTokensFlow(listOf(transactionState), listOf(artRegistrySession), observersSession))

        /*
        * Create an instance of IssuedTokenType, it is used by our NFT which would be issued to the owner.
        * Note that the IssuedTokenType takes a TokenPointer as an input, since EvolvableTokenType is not TokenType,
        * but is a LinearState. This is done to separate the state info from the token
        * so that the state can evolve independently. IssuedTokenType is a wrapper around the TokenType and the issuer.
        */
        val issuedNft = nftState.toPointer(nftState.javaClass) issuedBy artist

        // Create an instance of the non-fungible token with the owner as the token holder.
        // The last parameter is a hash of the jar containing the TokenType, use the helper function to fetch it.
        val nft = NonFungibleToken(issuedNft, artist, UniqueIdentifier())

        // Issue the NFT by calling the IssueTokens flow provided with the Token SDK
        return subFlow(IssueTokens(listOf(nft), observers))
    }

    private fun checkIfInitiatorNodeIsArtist(artist: Party) {
        val artistParty =
            serviceHub.networkMapCache.getPeerByLegalName(CordaX500Name("Artist", "London", "GB"))!!

        if (artist != artistParty) {
            throw FlowException("The artist is the only party that is allowed to create an NFT.")
        }
    }

    private fun getAllCustomerNodes(): List<Party> {
        val identityService = serviceHub.identityService
        return listOf("Customer").map{  identityService.partiesFromName(it, false).single() }
    }
}

@InitiatedBy(CreateNftFlow::class)
class CreateNFTFlowResponder(val counterPartySession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(CreateEvolvableTokensFlowHandler(counterPartySession))

        // Signing
        object : SignTransactionFlow(counterPartySession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                "The URL of that art cannot be null".using(!(stx.inputs as NFTState).url.equals(""))
            }
        }
    }
}
