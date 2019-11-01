package com.template

import net.corda.core.contracts.*
import net.corda.core.identity.*
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.schemas.*
import kotlin.reflect.KClass
import net.corda.core.schemas.QueryableState
import rx.Completable.using


// ************
// * Contract *
// ************
class IOUContract : Contract {
    companion object {
        const val ID = "com.template.IOUContract"

    }

    // Our Create command.
    class Create : CommandData
    // Our Update command.
    class Update : CommandData

    override fun verify(tx: LedgerTransaction) {
        var command=tx.getCommand<CommandData>(0)
        var requiredSignature=command.signers
        var commandType =command.value
        if (commandType is Create){

        requireThat {
            // Constraints on the shape of the transaction.
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
          //  "Product Colour should be Red or Black."


            "There should be one output state of type IOUState." using (tx.outputs.size == 1)
//lender = from, borrower = to
            // IOU-specific constraints.
            val output = tx.outputsOfType<IOUState>().single()
            "The IOU's value must be non-negative." using (output.value > 0)

            "The lender and the borrower cannot be the same entity." using (output.from != output.to)

            // Constraints on the signers.
            val expectedSigners = listOf(output.to.owningKey, output.from.owningKey)
            "There must be two signers." using (command.signers.toSet().size == 2)
            "The borrower and lender must be signers." using (command.signers.containsAll(expectedSigners))
        }

    }else if (commandType is Update){

                requireThat {
                    // Constraints on the shape of the transaction.

                    "There should be one output state of type IOUState." using (tx.outputs.size == 1)
//lender = from, borrower = to
                    // IOU-specific constraints.
                    val output = tx.outputsOfType<IOUState>().single()
                    "The IOU's value must be non-negative." using (output.value > 0)
                    "The lender and the borrower cannot be the same entity." using (output.from != output.to)

                    // Constraints on the signers.
                    val expectedSigners = listOf(output.to.owningKey, output.from.owningKey)
                    "There must be two signers." using (command.signers.toSet().size == 2)
                    "The borrower and lender must be signers." using (command.signers.containsAll(expectedSigners))
                }
            }
        }
    }




// *********
// * State *
// *********

//lender = from, borrower = to
class IOUState(val value: Int,
               val from: Party,
               val to: Party,
               val productID: Int,
               val productName: String,
               val productColour: String,
               val status: String,
               override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {    override val participants get() = listOf(from, to)   }
