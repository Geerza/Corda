package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.contracts.UniqueIdentifier

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class CreateProductFlow(val iouValue: Int,
              val otherParty: Party,
              val productID: Int, val productName: String, val productColour: String,val status: String) : FlowLogic<Unit>() {
//start CreateProductFlow iouValue: 30, otherParty: "O=PartyB,L=New York,C=US", productID: 01, productName: "Apple", ProductColour: "Red", status: "Available"
    // run vaultQuery contractStateType: com.template.IOUState
    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty, productID, productName,productColour,status)
        val command = Command(IOUContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))

        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, IOUContract.ID)
                .addCommand(command)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party.
        val otherPartySession = initiateFlow(otherParty)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        subFlow(FinalityFlow(fullySignedTx))
    }

}

@InitiatedBy(CreateProductFlow::class)
class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)

                "The IOU's colour should be Red or Black." using(iou.productColour == "Red" || iou.productColour == "Black")
            }
        }

        subFlow(signTransactionFlow)
    }
}
