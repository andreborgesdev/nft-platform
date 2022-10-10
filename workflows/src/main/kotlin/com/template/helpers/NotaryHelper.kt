package com.template.helpers

import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub

class NotaryHelper {
    companion object {
        fun getNotary(serviceHub: ServiceHub): Party {
            /* Obtain a reference to a notary we wish to use.
             * METHOD 1: Take first notary on network, WARNING: use for test, non-prod environments, and single-notary networks only!*
             *  METHOD 2: Explicit selection of notary by CordaX500Name - argument can by coded in flows or parsed from config (Preferred)
             *  * - For production you always want to use Method 2 as it guarantees the expected notary is returned.
             */
            return serviceHub.networkMapCache.notaryIdentities[0]; // METHOD 1
            //return serviceHub.networkMapCache.getNotary(CordaX500Name.parse("O=Notary,L=London,C=GB")) // METHOD 2
        }
    }
}