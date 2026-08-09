package com.twofold.data.session

import androidx.annotation.StringRes
import com.twofold.R

/**
 * What kind of table the phone is lying on.
 *
 * Twofold was built for an insurance agent, and for a while the app said so everywhere — "client",
 * "policy", a signature line reading *Client*. That framing was never a property of the code. Every
 * line of it does one thing: show one person a document they have to sign, in a form they can
 * understand, while the person explaining it keeps their own view.
 *
 * That situation is not insurance. It is a loan officer and a borrower, a doctor and a patient
 * consenting to a procedure, a landlord and a tenant, an employer and someone signing an offer in a
 * language they half-read. In all of them, one party understands the document and the other is
 * signing it anyway — which is the asymmetry the hardware happens to mirror.
 *
 * So the profession is a setting, not an assumption. It changes the words and nothing else, because
 * nothing else needed to change — which is the argument that the idea generalises rather than a
 * claim that it might.
 */
enum class MeetingKind(
    @param:StringRes val label: Int,
    /** What to call the person opposite, in the agent's own language. */
    @param:StringRes val partyPrompt: Int,
    /**
     * The noun stamped on the signed document.
     *
     * Deliberately not localised, for the same reason the audit line is not: a signed PDF may be
     * checked months later on a different device in a different locale, and two copies of one record
     * that read differently are worse than one that reads in English.
     */
    val signerNoun: String,
) {
    INSURANCE(R.string.kind_insurance, R.string.party_client, "Client"),
    LENDING(R.string.kind_lending, R.string.party_borrower, "Borrower"),
    MEDICAL(R.string.kind_medical, R.string.party_patient, "Patient"),
    TENANCY(R.string.kind_tenancy, R.string.party_tenant, "Tenant"),
    EMPLOYMENT(R.string.kind_employment, R.string.party_employee, "Employee"),
    OTHER(R.string.kind_other, R.string.party_signer, "Signer"),
}
