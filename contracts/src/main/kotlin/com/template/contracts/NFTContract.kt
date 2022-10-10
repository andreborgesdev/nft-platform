package com.template.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import com.template.states.NFTState
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class NFTContract : EvolvableTokenContract(), Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "com.template.contracts.NFTContract"
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val newNFTOutputState = tx.outputStates.single() as NFTState

        newNFTOutputState.apply {
            require(tx.outputStates.size == 1) { "Expected exactly one output state" }
            require(tx.outputStates.first() is NFTState) { "Expected output state of type NFTState" }
            require(name.isNotEmpty() || name.isNotBlank()) {"Name cannot be empty."}
            require(price.quantity > 0) {"Price cannot be be 0 or a negative value."}
            require(url.isNotEmpty() || url.isNotBlank()) {"URL cannot be empty."}
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val nftInputState = tx.inputStates.single() as NFTState
        val nftOutputState = tx.outputStates.single() as NFTState

        nftOutputState.apply {
            require(nftInputState.name.equals(nftOutputState.name)) {"Name cannot be updated."}
            require(nftInputState.artist.equals(nftOutputState.artist)) {"Artist cannot be updated."}
            require(nftInputState.artRegistry.equals(nftOutputState.artRegistry)) {"Art Registry cannot be updated."}
            require(nftInputState.url.equals(nftOutputState.url)) {"URL cannot be updated."}
        }
    }
}