package com.template.contracts

import com.template.states.NFTState
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import org.junit.Test

class NFTStateTest {
    @Test
    @Throws(NoSuchFieldException::class)
    fun hasFieldsOfCorrectType() {
        // Does the field exist?
        NFTState::class.java.getDeclaredField("name")
        NFTState::class.java.getDeclaredField("price")
        NFTState::class.java.getDeclaredField("url")
        NFTState::class.java.getDeclaredField("artist")
        NFTState::class.java.getDeclaredField("artRegistry")
        // Is the field of the correct type?
        assert(NFTState::class.java.getDeclaredField("name").type == String::class.java)
        assert(NFTState::class.java.getDeclaredField("price").type == Amount::class.java)
        assert(NFTState::class.java.getDeclaredField("url").type == String::class.java)
        assert(NFTState::class.java.getDeclaredField("artist").type == Party::class.java)
        assert(NFTState::class.java.getDeclaredField("artRegistry").type == Party::class.java)
    }
}