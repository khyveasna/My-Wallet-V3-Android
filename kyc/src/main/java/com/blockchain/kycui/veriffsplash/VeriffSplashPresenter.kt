package com.blockchain.kycui.veriffsplash

import com.blockchain.BaseKycPresenter
import com.blockchain.kyc.datamanagers.nabu.NabuDataManager
import com.blockchain.kyc.models.nabu.NabuApiException
import com.blockchain.kyc.models.nabu.NabuErrorStatusCodes
import com.blockchain.nabu.NabuToken
import com.blockchain.notifications.analytics.Analytics
import com.blockchain.notifications.analytics.AnalyticsEvent
import com.blockchain.veriff.VeriffApplicantAndToken
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import piuk.blockchain.androidcore.utils.PersistentPrefs
import piuk.blockchain.androidcoreui.ui.base.UiState
import piuk.blockchain.kyc.R

import timber.log.Timber

class VeriffSplashPresenter(
    nabuToken: NabuToken,
    private val nabuDataManager: NabuDataManager,
    private val prefs: PersistentPrefs,
    private val analytics: Analytics
) : BaseKycPresenter<VeriffSplashView>(nabuToken) {

    private var applicantToken: VeriffApplicantAndToken? = null

    override fun onViewReady() {
        updateUiState(UiState.LOADING)
        fetchRequiredDocumentList()
        fetchVeriffStartApplicantToken()

        compositeDisposable +=
            view.nextClick
                .subscribe { view.continueToVeriff(applicantToken!!) }

        compositeDisposable +=
            view.swapClick
                .subscribe { view.continueToSwap() }
    }

    private fun fetchRequiredDocumentList() {
        compositeDisposable += fetchOfflineToken
            .flatMap { token ->
                nabuDataManager.getSupportedDocuments(token, view.countryCode)
            }
            .doOnError(Timber::e)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy { documents ->
                view.supportedDocuments(documents)
            }
    }

    private fun fetchVeriffStartApplicantToken() {
        compositeDisposable +=
            fetchOfflineToken
                .flatMap { token ->
                    nabuDataManager.startVeriffSession(token)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(
                    onSuccess = {
                        applicantToken = it
                        updateUiState(UiState.CONTENT)
                    },
                    onError = { e ->
                        Timber.e(e)
                        if (e is NabuApiException) {
                            handleSessionStartError(e)
                        } else {
                            view.showErrorToast(R.string.kyc_veriff_splash_verification_error)
                        }
                    }
                )
    }

    private fun handleSessionStartError(e: NabuApiException) {
        when (e.getErrorStatusCode()) {
            // If we get a pre-IDV check failed, then this device is now blacklisted and so won't be able to
            // get to tier 2 verification. Remember this in prefs, so that the UI can avoid showing 'upgrade'
            // announcements etc
            NabuErrorStatusCodes.PreIDVCheckFailed,
            // Or did we try to register with a duplicate email?
            NabuErrorStatusCodes.AlreadyRegistered -> {
                prefs.devicePreIDVCheckFailed = true
                updateUiState(UiState.FAILURE)
            }
            // For anything else, just show the 'failed' toast as before:
            NabuErrorStatusCodes.TokenExpired,
            NabuErrorStatusCodes.Unknown -> view.showErrorToast(R.string.kyc_veriff_splash_verification_error)
        }
    }

    internal fun submitVerification() {
        compositeDisposable +=
            fetchOfflineToken
                .flatMapCompletable { tokenResponse ->
                    nabuDataManager.submitVeriffVerification(tokenResponse)
                        .subscribeOn(Schedulers.io())
                }
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { view.showProgressDialog(false) }
                .doOnTerminate { view.dismissProgressDialog() }
                .doOnError(Timber::e)
                .subscribeBy(
                    onComplete = { view.continueToCompletion() },
                    onError = {
                        view.showErrorToast(R.string.kyc_veriff_splash_verification_error)
                    }
                )
    }

    internal fun onProgressCancelled() {
        // Clear outbound requests
        compositeDisposable.clear()
        // Resubscribe
        onViewReady()
    }

    private fun updateUiState(@UiState.UiStateDef state: Int) {
        view?.setUiState(state)

        val params = when (state) {
            UiState.CONTENT -> mapOf("result" to "START_KYC")
            UiState.FAILURE -> mapOf("result" to "UNAVAILABLE")
            else -> null
        }

        params?.let {
            analytics.logEvent(object : AnalyticsEvent {
                override val event = "kyc_splash_request_gold_preIDV"
                override val params = it
                }
            )
        }
    }
}
