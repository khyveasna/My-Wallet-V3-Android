package piuk.blockchain.android;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatDelegate;
import com.blockchain.koin.KoinStarter;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import dagger.Lazy;
import info.blockchain.wallet.BlockchainFramework;
import info.blockchain.wallet.FrameworkInterface;
import info.blockchain.wallet.api.Environment;
import io.fabric.sdk.android.Fabric;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.plugins.RxJavaPlugins;
import org.bitcoinj.core.NetworkParameters;
import piuk.blockchain.android.data.connectivity.ConnectivityManager;
import piuk.blockchain.android.injection.Injector;
import com.blockchain.ui.CurrentContextAccess;
import piuk.blockchain.android.ui.auth.LogoutActivity;
import piuk.blockchain.android.ui.ssl.SSLVerifyActivity;
import piuk.blockchain.android.util.PrngHelper;
import piuk.blockchain.androidcore.data.access.AccessState;
import piuk.blockchain.androidcore.data.api.EnvironmentConfig;
import piuk.blockchain.androidcore.data.connectivity.ConnectionEvent;
import piuk.blockchain.androidcore.data.rxjava.RxBus;
import piuk.blockchain.androidcore.utils.PersistentPrefs;
import piuk.blockchain.androidcore.utils.annotations.Thunk;
import piuk.blockchain.androidcoreui.ApplicationLifeCycle;
import piuk.blockchain.androidcoreui.BuildConfig;
import piuk.blockchain.androidcoreui.utils.AndroidUtils;
import piuk.blockchain.androidcoreui.utils.AppUtil;
import piuk.blockchain.androidcoreui.utils.logging.AppLaunchEvent;
import piuk.blockchain.androidcoreui.utils.logging.Logging;
import retrofit2.Retrofit;
import timber.log.Timber;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * Created by adambennett on 04/08/2016.
 */

public class BlockchainApplication extends Application implements FrameworkInterface {

    public static final String RX_ERROR_TAG = "RxJava Error";

    @Inject
    @Named("api")
    protected Lazy<Retrofit> retrofitApi;
    @Inject
    @Named("explorer")
    protected Lazy<Retrofit> retrofitExplorer;

    @Inject
    RxBus rxBus;
    @Inject
    EnvironmentConfig environmentSettings;
    @Inject
    PrngHelper prngHelper;
    @Inject
    CurrentContextAccess currentContextAccess;
    @Inject
    AppUtil appUtils;
    @Inject
    AccessState loginState;
    @Inject
    PersistentPrefs prefs;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        if (!AndroidUtils.is21orHigher()) {
            MultiDex.install(base);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (BuildConfig.USE_CRASHLYTICS) {
            // Init crash reporting
            Fabric.with(this, new Crashlytics(), new Answers());
        } else {
            Fabric.with(this, new Answers());
        }
        // Init Timber
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }
        KoinStarter.start(this);

        // Init objects first
        Injector.getInstance().init(this);
        // Inject into Application
        Injector.getInstance().getAppComponent().inject(this);

        // Pass objects to JAR
        BlockchainFramework.init(this);

        UncaughtExceptionHandler.Companion.install(appUtils);

        RxJavaPlugins.setErrorHandler(throwable -> Timber.tag(RX_ERROR_TAG).e(throwable));

        loginState.setLogoutActivity(LogoutActivity.class);

        // Apply PRNG fixes on app start if needed
        prngHelper.applyPRNGFixes();

        ConnectivityManager.getInstance().registerNetworkListener(this);

        checkSecurityProviderAndPatchIfNeeded();

        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);

        //noinspection AnonymousInnerClassMayBeStatic
        ApplicationLifeCycle.getInstance().addListener(new ApplicationLifeCycle.LifeCycleListener() {
            @Override
            public void onBecameForeground() {
                // Ensure that PRNG fixes are always current for the session
                prngHelper.applyPRNGFixes();
            }

            @Override
            public void onBecameBackground() {
                // No-op
            }
        });

        registerActivityLifecycleCallbacks(currentContextAccess.createCallBacks());

        // Report Google Play Services availability
        Logging.INSTANCE.logCustom(new AppLaunchEvent(isGooglePlayServicesAvailable(this)));

        // Set screen shots to enabled in staging builds, to simplify automation and QA processes
        prefs.setValue(
                PersistentPrefs.KEY_SCREENSHOTS_ENABLED,
                environmentSettings.shouldShowDebugMenu()
        );

        rxBus.register(ConnectionEvent.class)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(connectionEvent -> SSLVerifyActivity.start(getApplicationContext(), connectionEvent));
    }

    // Pass instances to JAR Framework, evaluate after object graph instantiated fully
    @Override
    public Retrofit getRetrofitApiInstance() {
        return retrofitApi.get();
    }

    @Override
    public Retrofit getRetrofitExplorerInstance() {
        return retrofitExplorer.get();
    }

    @Override
    public Environment getEnvironment() {
        return environmentSettings.getEnvironment();
    }

    @Override
    public NetworkParameters getBitcoinParams() {
        return environmentSettings.getBitcoinNetworkParameters();
    }

    @Override
    public NetworkParameters getBitcoinCashParams() {
        return environmentSettings.getBitcoinCashNetworkParameters();
    }

    @Override
    public String getApiCode() {
        return "25a6ad13-1633-4dfb-b6ee-9b91cdf0b5c3";
    }

    @Override
    public String getDevice() {
        return "android";
    }

    @Override
    public String getAppVersion() {
        return BuildConfig.VERSION_NAME;
    }

    /**
     * This patches a device's Security Provider asynchronously to help defend against various
     * vulnerabilities. This provider is normally updated in Google Play Services anyway, but this
     * will catch any immediate issues that haven't been fixed in a slow rollout.
     *
     * In the future, we may want to show some kind of warning to users or even stop the app, but
     * this will harm users with versions of Android without GMS approval.
     *
     * @see <a href="https://developer.android.com/training/articles/security-gms-provider.html">Updating
     * Your Security Provider</a>
     */
    protected void checkSecurityProviderAndPatchIfNeeded() {
        ProviderInstaller.installIfNeededAsync(this, new ProviderInstaller.ProviderInstallListener() {
            @Override
            public void onProviderInstalled() {
                Timber.i("Security Provider installed");
            }

            @Override
            public void onProviderInstallFailed(int errorCode, Intent intent) {
                if (GoogleApiAvailability.getInstance().isUserResolvableError(errorCode)) {
                    showError(errorCode);
                } else {
                    // Google Play services is not available.
                    onProviderInstallerNotAvailable();
                }
            }
        });
    }

    /**
     * Show a dialog prompting the user to install/update/enable Google Play services.
     *
     * @param errorCode Recoverable error code
     */
    @Thunk
    void showError(int errorCode) {
        // TODO: 05/08/2016 Decide if we should alert users here or not
        Timber.e("Security Provider install failed with recoverable error: %s", GoogleApiAvailability.getInstance().getErrorString(errorCode));
    }

    /**
     * This is reached if the provider cannot be updated for some reason. App should consider all
     * HTTP communication to be vulnerable, and take appropriate action.
     */
    @Thunk
    void onProviderInstallerNotAvailable() {
        // TODO: 05/08/2016 Decide if we should take action here or not
        Timber.wtf("Security Provider Installer not available");
    }

    /**
     * Returns true if Google Play Services are found and ready to use.
     *
     * @param context The current Application Context
     */
    private boolean isGooglePlayServicesAvailable(Context context) {
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    }

}
