package com.breadwallet.presenter.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ViewFlipper;

import com.breadwallet.BreadApp;
import com.breadwallet.R;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRButton;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.fragments.FragmentHyperSend;
import com.breadwallet.tools.animation.BRDialog;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.manager.DeepLinkingManager;
import com.breadwallet.tools.manager.FontManager;
import com.breadwallet.tools.manager.InternetManager;
import com.breadwallet.tools.manager.TxManager;
import com.breadwallet.tools.services.SyncService;
import com.breadwallet.tools.sqlite.RatesDataSource;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.SyncTestLogger;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.abstracts.OnBalanceChangedListener;
import com.breadwallet.wallet.abstracts.OnTxListModified;
import com.breadwallet.wallet.abstracts.SyncListener;
import com.breadwallet.wallet.wallets.bitcoin.BaseBitcoinWalletManager;
import com.breadwallet.wallet.wallets.ela.ElaDataSource;
import com.breadwallet.wallet.wallets.ela.WalletElaManager;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.breadwallet.wallet.wallets.ioex.WalletIoexManager;
import com.elastos.jni.Constants;
import com.elastos.jni.UriFactory;
import com.platform.HTTPServer;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;

/**
 * Created by byfieldj on 1/16/18.
 * <p>
 * <p>
 * This activity will display pricing and transaction information for any currency the user has access to
 * (BTC, BCH, ETH)
 */

public class HyperSendCryptoActivity extends BRActivity implements InternetManager.ConnectionReceiverListener,
        OnTxListModified, RatesDataSource.OnDataChanged, SyncListener, OnBalanceChangedListener {
    private static final String TAG = HyperSendCryptoActivity.class.getName();

    private static final String SYNCED_THROUGH_DATE_FORMAT = "MM/dd/yy HH:mm";
    private static final float SYNC_PROGRESS_LAYOUT_ANIMATION_ALPHA = 0.0f;

    private BaseTextView mCurrencyTitle;
    private BaseTextView mCurrencyPriceUsd;
    private BaseTextView mBalancePrimary;
    private BaseTextView mBalanceSecondary;
    private Toolbar mToolbar;
    private ImageButton mBackButton;
    private BRButton mSendButton;
    private BRButton mReceiveButton;
    private BRButton mSellButton;
    private LinearLayout mProgressLayout;
    private BaseTextView mSyncStatusLabel;
    private BaseTextView mProgressLabel;
    public ViewFlipper mBarFlipper;
    //private BRSearchBar mSearchBar;
    private ImageButton mSearchIcon;
    private ConstraintLayout mToolBarConstraintLayout;
    private LinearLayout mWalletFooter;

    private static final float PRIMARY_TEXT_SIZE = 30;
    private static final float SECONDARY_TEXT_SIZE = 16;

    private static final boolean RUN_LOGGER = false;

    private SyncTestLogger mTestLogger;

    private SyncNotificationBroadcastReceiver mSyncNotificationBroadcastReceiver;
    private String mCurrentWalletIso;

    private BaseWalletManager mWallet;

    private String mUri;

    private SwipeRefreshLayout mSwipeRefreshLayout;

    public static String mCallbackUrl;
    public static String mReturnUrl;
    public static String mOrderId;

    private static HyperSendCryptoActivity thisActivity;
    public static String sentAmount="";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hyper_send_crypto);
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if(!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
            }
            if (!StringUtil.isNullOrEmpty(mUri)) {
                UriFactory factory = new UriFactory();
                factory.parse(mUri);
                String coinName = factory.getCoinName();
                if(StringUtil.isNullOrEmpty(coinName)) return;
                if(coinName.toLowerCase().contains("usdt")) coinName = "USDT";
                BRSharedPrefs.putCurrentWalletIso(BreadApp.mContext, coinName);
            }
            Log.i("author_test", "walletActivity1 mUri:"+mUri);
        }

        BRSharedPrefs.putIsNewWallet(this, false);

        mCurrencyTitle = findViewById(R.id.currency_label);
        mCurrencyPriceUsd = findViewById(R.id.currency_usd_price);
        mBalancePrimary = findViewById(R.id.balance_primary);
        mBalanceSecondary = findViewById(R.id.balance_secondary);
        mToolbar = findViewById(R.id.bread_bar);
        mBackButton = findViewById(R.id.back_icon);
        mSendButton = findViewById(R.id.send_button);
        mReceiveButton = findViewById(R.id.receive_button);
        mSellButton = findViewById(R.id.sell_button);
        mBarFlipper = findViewById(R.id.tool_bar_flipper);
        //mSearchBar = findViewById(R.id.search_bar);
        mSearchIcon = findViewById(R.id.search_icon);
        mToolBarConstraintLayout = findViewById(R.id.bread_toolbar);
        mProgressLayout = findViewById(R.id.progress_layout);
        mSyncStatusLabel = findViewById(R.id.sync_status_label);
        mProgressLabel = findViewById(R.id.syncing_label);
        mWalletFooter = findViewById(R.id.bottom_toolbar_layout1);
        mSwipeRefreshLayout = findViewById(R.id.recycler_layout);

        startSyncLoggerIfNeeded();

        setUpBarFlipper();
        mBalancePrimary.setTextSize(TypedValue.COMPLEX_UNIT_SP, PRIMARY_TEXT_SIZE);
        mBalanceSecondary.setTextSize(TypedValue.COMPLEX_UNIT_SP, SECONDARY_TEXT_SIZE);

        mSendButton.setHasShadow(false);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 5*1000);
            }
        });

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UiUtils.showSendFragment(HyperSendCryptoActivity.this, null);

            }
        });

        mSendButton.setHasShadow(false);
        mReceiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UiUtils.showReceiveFragment(HyperSendCryptoActivity.this, true);

            }
        });

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //onBackPressed();
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        mSearchIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!UiUtils.isClickAllowed()) {
                    return;
                }
                mBarFlipper.setDisplayedChild(1); //search bar
                //mSearchBar.onShow(true);
            }
        });

        mSellButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.startWebActivity(HyperSendCryptoActivity.this, HTTPServer.URL_SELL);
            }
        });


        mBalancePrimary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swap();
            }
        });
        mBalanceSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swap();
            }
        });

        TxManager.getInstance().init(this);

        onConnectionChanged(InternetManager.getInstance().isConnected(this));

        updateUi();

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("BG:" + TAG + ":refreshBalances and address");
                Activity app = HyperSendCryptoActivity.this;
                WalletsMaster.getInstance(app).refreshBalances(app);
                WalletsMaster.getInstance(app).getCurrentWallet(app).refreshAddress(app);
                WalletElaManager.getInstance(app).updateTxHistory();
                WalletElaManager.getInstance(app).checkTxHistory();
                WalletIoexManager.getInstance(app).updateTxHistory();
                ElaDataSource.getInstance(app).getProducerByTxid();
            }
        });

        // Check if the "Twilight" screen altering app is currently running
        if (Utils.checkIfScreenAlteringAppIsRunning(this, "com.urbandroid.lux")) {
            BRDialog.showSimpleDialog(this, "Screen Altering App Detected", getString(R.string.Android_screenAlteringMessage));
        }

        mWallet = WalletsMaster.getInstance(this).getCurrentWallet(this);

        boolean cryptoPreferred = BRSharedPrefs.isCryptoPreferred(this);

        setPriceTags(cryptoPreferred, false);

        thisActivity=this;
    }


    private void startSyncLoggerIfNeeded() {
        if (Utils.isEmulatorOrDebug(this) && RUN_LOGGER) {
            if (mTestLogger != null) {
                mTestLogger.interrupt();
            }
            mTestLogger = new SyncTestLogger(this); //Sync logger
            mTestLogger.start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        //since we have one instance of activity at all times, this is needed to know when a new intent called upon this activity
        DeepLinkingManager.handleUrlClick(this, intent);
        if (intent != null) {
            String action = intent.getAction();
            if(!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                mUri = uri.toString();
                hyperPayScheme();
            }
        }
    }

    private void updateUi() {
        final BaseWalletManager wm = WalletsMaster.getInstance(this).getCurrentWallet(this);
        if (wm == null) {
            Log.e(TAG, "updateUi: wallet is null");
            return;
        }

        String fiatExchangeRate = CurrencyUtils.getFormattedAmount(this, BRSharedPrefs.getPreferredFiatIso(this), wm.getFiatExchangeRate(this));
        String fiatBalance = CurrencyUtils.getFormattedAmount(this, BRSharedPrefs.getPreferredFiatIso(this), wm.getFiatBalance(this));
        String cryptoBalance = CurrencyUtils.getFormattedAmount(this, wm.getIso(), wm.getCachedBalance(this), wm.getUiConfiguration().getMaxDecimalPlacesForUi());

        mCurrencyTitle.setText(wm.getIso());
        mCurrencyPriceUsd.setText(String.format("%s / %s", fiatExchangeRate, wm.getIso()));
        mBalancePrimary.setText(fiatBalance);
        mBalanceSecondary.setText(cryptoBalance.replace(wm.getIso(), ""));

        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("BG:" + TAG + ":updateTxList");
                TxManager.getInstance().updateTxList(HyperSendCryptoActivity.this);
            }
        });
    }

    private void swap() {
        if (!UiUtils.isClickAllowed()) {
            return;
        }
        BRSharedPrefs.setIsCryptoPreferred(HyperSendCryptoActivity.this, !BRSharedPrefs.isCryptoPreferred(HyperSendCryptoActivity.this));
        setPriceTags(BRSharedPrefs.isCryptoPreferred(HyperSendCryptoActivity.this), true);
    }


    private void setPriceTags(final boolean cryptoPreferred, boolean animate) {
        ConstraintSet set = new ConstraintSet();
        set.clone(mToolBarConstraintLayout);
        if (animate) {
            TransitionManager.beginDelayedTransition(mToolBarConstraintLayout);
        }

        if (cryptoPreferred) {
            set.connect(R.id.balance_secondary, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(R.id.balance_secondary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(R.id.balance_secondary, ConstraintSet.BOTTOM, R.id.anchor, ConstraintSet.TOP, 0);


            set.connect(R.id.balance_primary, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(R.id.balance_primary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(R.id.balance_primary, ConstraintSet.BOTTOM, R.id.anchor, ConstraintSet.BOTTOM, 0);

            set.connect(R.id.swap, ConstraintSet.END, R.id.balance_primary, ConstraintSet.START, 0);

            mBalanceSecondary.setTextSize(PRIMARY_TEXT_SIZE);
            mBalancePrimary.setTextSize(SECONDARY_TEXT_SIZE);
        } else {
            set.connect(R.id.balance_primary, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(R.id.balance_primary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(R.id.balance_primary, ConstraintSet.BOTTOM, R.id.anchor, ConstraintSet.TOP, 0);

            set.connect(R.id.balance_secondary, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0);
            set.connect(R.id.balance_secondary, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            set.connect(R.id.balance_secondary, ConstraintSet.BOTTOM, R.id.anchor, ConstraintSet.BOTTOM, 0);

            set.connect(R.id.swap, ConstraintSet.END, R.id.balance_secondary, ConstraintSet.START, 0);

            mBalanceSecondary.setTextSize(SECONDARY_TEXT_SIZE);
            mBalancePrimary.setTextSize(PRIMARY_TEXT_SIZE);
        }

        set.applyTo(mToolBarConstraintLayout);
        mBalanceSecondary.setTextColor(getResources().getColor(cryptoPreferred ? R.color.white : R.color.currency_subheading_color, null));
        mBalancePrimary.setTextColor(getResources().getColor(cryptoPreferred ? R.color.currency_subheading_color : R.color.white, null));
        mBalanceSecondary.setTypeface(FontManager.get(this, cryptoPreferred ? "CircularPro-Bold.otf" : "CircularPro-Book.otf"));
        mBalancePrimary.setTypeface(FontManager.get(this, !cryptoPreferred ? "CircularPro-Bold.otf" : "CircularPro-Book.otf"));
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();

        InternetManager.registerConnectionReceiver(this, this);

        TxManager.getInstance().onResume(this);

        RatesDataSource.getInstance(this).addOnDataChangedListener(this);
        final BaseWalletManager wallet = WalletsMaster.getInstance(this).getCurrentWallet(this);
        wallet.addTxListModifiedListener(this);
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                WalletEthManager.getInstance(HyperSendCryptoActivity.this).estimateGasPrice();
                wallet.refreshCachedBalance(HyperSendCryptoActivity.this);
                BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        updateUi();
                    }
                });
                if (wallet.getConnectStatus() != 2) {
                    wallet.connect(HyperSendCryptoActivity.this);
                }

            }
        });

        wallet.addBalanceChangedListener(this);

        mCurrentWalletIso = wallet.getIso();

        wallet.addSyncListener(this);

        mSyncNotificationBroadcastReceiver = new SyncNotificationBroadcastReceiver();
        SyncService.registerSyncNotificationBroadcastReceiver(HyperSendCryptoActivity.this.getApplicationContext(), mSyncNotificationBroadcastReceiver);
        SyncService.startService(this.getApplicationContext(), SyncService.ACTION_START_SYNC_PROGRESS_POLLING, mCurrentWalletIso);

        DeepLinkingManager.handleUrlClick(this, getIntent());

        hyperPayScheme();
    }

    private void hyperPayScheme(){
        if(StringUtil.isNullOrEmpty(mUri)) return;
        FragmentHyperSend.mFromHyper = true;

        final UriFactory factory=new UriFactory();
        factory.parse(mUri);
        mUri=null;

        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable(){
            @Override
            public void run(){
                BaseWalletManager walletManager=WalletsMaster.getInstance(HyperSendCryptoActivity.this).getCurrentWallet(HyperSendCryptoActivity.this);
                FragmentHyperSend fragmentHyperSend=UiUtils.showHyperSendFragment(HyperSendCryptoActivity.this);
                if(fragmentHyperSend!=null){
                    fragmentHyperSend.setDataFromHyper(walletManager.decorateAddress(factory.getReceivingAddress()), factory.getDescription());
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        InternetManager.unregisterConnectionReceiver(this, this);
        mWallet.removeSyncListener(this);
        SyncService.unregisterSyncNotificationBroadcastReceiver(HyperSendCryptoActivity.this.getApplicationContext(), mSyncNotificationBroadcastReceiver);
    }

    /* SyncListener methods */
    @Override
    public void syncStopped(String error) {

    }

    @Override
    public void syncStarted() {
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                SyncService.startService(HyperSendCryptoActivity.this.getApplicationContext(), SyncService.ACTION_START_SYNC_PROGRESS_POLLING, mCurrentWalletIso);
            }
        });
    }
    /* SyncListener methods End*/

    private void setUpBarFlipper() {
        mBarFlipper.setInAnimation(AnimationUtils.loadAnimation(this, R.anim.flipper_enter));
        mBarFlipper.setOutAnimation(AnimationUtils.loadAnimation(this, R.anim.flipper_exit));
    }

    public void resetFlipper() {
        mBarFlipper.setDisplayedChild(0);
    }


    @Override
    public void onConnectionChanged(boolean isConnected) {
        Log.d(TAG, "onConnectionChanged: isConnected: " + isConnected);
//        Toast.makeText(this, getString(R.string.net_has_disconnect), Toast.LENGTH_SHORT).show();
        if (isConnected) {
            if (mBarFlipper != null && mBarFlipper.getDisplayedChild() == 2) {
                mBarFlipper.setDisplayedChild(0);
            }
            SyncService.startService(this.getApplicationContext(), SyncService.ACTION_START_SYNC_PROGRESS_POLLING, mCurrentWalletIso);

        } else {
            if (mBarFlipper != null) {
                mBarFlipper.setDisplayedChild(2);
            }

        }
    }

    @Override
    public void onBackPressed() {
        int c = getFragmentManager().getBackStackEntryCount();
        if (c > 0) {
            super.onBackPressed();
            return;
        }
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
        if (!isDestroyed()) {
            finish();
        }
    }

    @Override
    public void txListModified(String hash) {
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("UI:" + TAG + ":updateUi");
                updateUi();
            }
        });
    }

    public void updateSyncProgress(double progress) {
        if (progress != SyncService.PROGRESS_FINISH) {
            StringBuffer labelText = new StringBuffer(getString(R.string.SyncingView_syncing));
            labelText.append(' ')
                    .append(NumberFormat.getPercentInstance().format(progress));
            mProgressLabel.setText(labelText);
            mProgressLayout.setVisibility(View.VISIBLE);

            if (mWallet instanceof BaseBitcoinWalletManager) {
                BaseBitcoinWalletManager baseBitcoinWalletManager = (BaseBitcoinWalletManager) mWallet;
                long syncThroughDateInMillis = baseBitcoinWalletManager.getPeerManager().getLastBlockTimestamp() * DateUtils.SECOND_IN_MILLIS;
                String syncedThroughDate = new SimpleDateFormat(SYNCED_THROUGH_DATE_FORMAT).format(syncThroughDateInMillis);
                mSyncStatusLabel.setText(String.format(getString(R.string.SyncingView_syncedThrough), syncedThroughDate));
            }
        } else {
            mProgressLayout.animate()
                    .translationY(-mProgressLayout.getHeight())
                    .alpha(SYNC_PROGRESS_LAYOUT_ANIMATION_ALPHA)
                    .setDuration(DateUtils.SECOND_IN_MILLIS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mProgressLayout.setVisibility(View.GONE);
                        }
                    });
        }
    }

    @Override
    public void onChanged() {
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });
    }

    @Override
    public void onBalanceChanged(BigDecimal newBalance) {
        BRExecutor.getInstance().forMainThreadTasks().execute(new Runnable() {
            @Override
            public void run() {
                updateUi();
            }
        });
    }

    /**
     * The {@link SyncNotificationBroadcastReceiver} is responsible for receiving updates from the
     * {@link SyncService} and updating the UI accordingly.
     */
    private class SyncNotificationBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (SyncService.ACTION_SYNC_PROGRESS_UPDATE.equals(intent.getAction())) {
                String intentWalletIso = intent.getStringExtra(SyncService.EXTRA_WALLET_ISO);
                double progress = intent.getDoubleExtra(SyncService.EXTRA_PROGRESS, SyncService.PROGRESS_NOT_DEFINED);
                if (mCurrentWalletIso.equals(intentWalletIso)) {
                    if (progress >= SyncService.PROGRESS_START) {
                        HyperSendCryptoActivity.this.updateSyncProgress(progress);
                    } else {
                        Log.e(TAG, "SyncNotificationBroadcastReceiver.onReceive: Progress not set:" + progress);
                    }
                } else {
                    Log.e(TAG, "SyncNotificationBroadcastReceiver.onReceive: Wrong wallet. Expected:"
                            + mCurrentWalletIso + " Actual:" + intentWalletIso + " Progress:" + progress);
                }
            }
        }
    }

    public static void finishTransaction(String txId){
        if(FragmentHyperSend.mFromHyper){
            Intent replyIntent=new Intent();
            replyIntent.putExtra("txId", txId);
            replyIntent.putExtra("amount", sentAmount);
            thisActivity.setResult(RESULT_OK, replyIntent);
            thisActivity.finish();
        }
    }

}
