package piuk.blockchain.android.ui.settings

import android.support.annotation.StringRes

import com.blockchain.kyc.models.nabu.Kyc2TierState
import piuk.blockchain.androidcoreui.ui.base.View
import piuk.blockchain.androidcoreui.ui.customviews.ToastCustom

internal interface SettingsView : View {

    fun setUpUi()

    fun showFingerprintDialog(pincode: String)

    fun showDisableFingerprintDialog()

    fun updateFingerprintPreferenceStatus()

    fun showNoFingerprintsAddedDialog()

    fun showProgressDialog(@StringRes message: Int)

    fun hideProgressDialog()

    fun showToast(@StringRes message: Int, @ToastCustom.ToastType toastType: String)

    fun setGuidSummary(summary: String)

    fun setKycState(kycState: Kyc2TierState)

    fun setEmailSummary(summary: String)

    fun setSmsSummary(summary: String)

    fun setFiatSummary(summary: String)

    fun setEmailNotificationsVisibility(visible: Boolean)

    fun setEmailNotificationPref(enabled: Boolean)

    fun setPushNotificationPref(enabled: Boolean)

    fun setFingerprintVisibility(visible: Boolean)

    fun setTwoFaPreference(enabled: Boolean)

    fun setTorBlocked(blocked: Boolean)

    fun setPitLinkingState(isLinked: Boolean)

    fun setScreenshotsEnabled(enabled: Boolean)

    fun showDialogEmailVerification()

    fun showDialogVerifySms()

    fun showDialogSmsVerified()

    fun goToPinEntryPage()

    fun launchThePitLandingActivity()

    fun launchThePit()

    fun setLauncherShortcutVisibility(visible: Boolean)

    fun showWarningDialog(@StringRes message: Int)

    fun launchKycFlow()

    fun isPitEnabled(enabled: Boolean)
}
