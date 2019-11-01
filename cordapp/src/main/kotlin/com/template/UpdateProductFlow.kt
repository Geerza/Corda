package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.*
import net.corda.core.utilities.ProgressTracker
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC

class UpdateProductStatusFlow(val linearId: UniqueIdentifier, val status: String) : FlowLogic<Unit>() {

    //start UpdateProductStatusFlow linearId: status: "Available"
    // run vaultQuery contractStateType: com.template.IOUState
    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    /** The flow logic is encapsulated within the call() method. */
    @Suspendable
    override fun call() {
        // We retrieve the notary identity from the network map.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]
        val criteria = QueryCriteria.LinearStateQueryCriteria(null, listOf(linearId),status = Vault.StateStatus.UNCONSUMED)
        val results = serviceHub.vaultService.queryBy<IOUState>(criteria).states

        val iouState = results.last().state.data
        val  outputState = IOUState(iouState.value, iouState.from, iouState.to, iouState.productID, iouState.productName, iouState.productColour,status, linearId)
        // We create the transaction components.

  val command = Command(IOUContract.Update(), listOf(ourIdentity.owningKey, iouState.to.owningKey))
        // We create a transaction builder and add the components.
        val txBuilder = TransactionBuilder(notary = notary)
                .addOutputState(outputState, IOUContract.ID)
                .addInputState(results.single())
                .addCommand(command)

        // Verifying the transaction.
        txBuilder.verify(serviceHub)

        // Signing the transaction.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party.
        val otherPartySession = initiateFlow(iouState.to)

        // Obtaining the counterparty's signature.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherPartySession), CollectSignaturesFlow.tracker()))

        // Finalising the transaction.
        subFlow(FinalityFlow(fullySignedTx))
    }

}


@InitiatedBy(UpdateProductStatusFlow::class)
class IOUFlowResponder1(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an IOU transaction." using (output is IOUState)
                val iou = output as IOUState
                "The IOU's value can't be too high." using (iou.value < 100)
            }
        }

        subFlow(signTransactionFlow)
    }
}