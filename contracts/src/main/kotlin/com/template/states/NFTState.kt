package com.template.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.template.contracts.NFTContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.util.*

@BelongsToContract(NFTContract::class)
data class NFTState (
    val name: String,
    val price: Amount<Currency>,
    val url: String,
    val artist: Party,
    val artRegistry: Party,
    override val maintainers: List<Party>,
    override val linearId: UniqueIdentifier = UniqueIdentifier(),
    override val fractionDigits: Int = 2
) : EvolvableTokenType()