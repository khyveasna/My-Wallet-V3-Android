package piuk.blockchain.android.deeplink

import android.content.Intent
import com.blockchain.notifications.links.PendingLink
import io.reactivex.Maybe
import io.reactivex.Single
import piuk.blockchain.android.kyc.KycDeepLinkHelper
import piuk.blockchain.android.kyc.KycLinkState
import piuk.blockchain.android.sunriver.CampaignLinkState
import piuk.blockchain.android.sunriver.SunriverDeepLinkHelper
import piuk.blockchain.android.thepit.ThePitDeepLinkParser

internal class DeepLinkProcessor(
    private val linkHandler: PendingLink,
    private val emailVerifiedLinkHelper: EmailVerificationDeepLinkHelper,
    private val kycDeepLinkHelper: KycDeepLinkHelper,
    private val sunriverDeepLinkHelper: SunriverDeepLinkHelper,
    private val thePitDeepLinkParser: ThePitDeepLinkParser
) {

    fun getLink(intent: Intent): Single<LinkState> =
        linkHandler.getPendingLinks(intent)
            .map { uri ->
                val emailVerifiedUri = emailVerifiedLinkHelper.mapUri(uri)
                if (emailVerifiedUri != EmailVerifiedLinkState.NoUri) {
                    return@map LinkState.EmailVerifiedDeepLink(emailVerifiedUri)
                }
                val sr = sunriverDeepLinkHelper.mapUri(uri)
                if (sr !is CampaignLinkState.NoUri) {
                    return@map LinkState.SunriverDeepLink(sr)
                }
                val kyc = kycDeepLinkHelper.mapUri(uri)
                if (kyc != KycLinkState.NoUri) {
                    return@map LinkState.KycDeepLink(kyc)
                }
                val linkId = thePitDeepLinkParser.mapUri(uri)
                if (linkId != null) {
                    return@map LinkState.ThePitDeepLink(linkId)
                }
                LinkState.NoUri
            }
            .switchIfEmpty(Maybe.just(LinkState.NoUri))
            .toSingle()
            .onErrorResumeNext { Single.just(LinkState.NoUri) }
}

sealed class LinkState {
    data class EmailVerifiedDeepLink(val link: EmailVerifiedLinkState) : LinkState()
    data class SunriverDeepLink(val link: CampaignLinkState) : LinkState()
    data class KycDeepLink(val link: KycLinkState) : LinkState()
    data class ThePitDeepLink(val linkId: String) : LinkState()

    object NoUri : LinkState()
}
