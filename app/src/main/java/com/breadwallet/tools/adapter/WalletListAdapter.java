package com.breadwallet.tools.adapter;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.breadwallet.R;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.services.SyncService;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.ethereum.WalletTokenManager;

import java.text.NumberFormat;
import java.util.ArrayList;

/**
 * Created by byfieldj on 1/31/18.
 */

public class WalletListAdapter extends RecyclerView.Adapter<WalletListAdapter.WalletItemViewHolder> {

    public static final String TAG = WalletListAdapter.class.getName();

    private final Context mContext;
    private ArrayList<WalletItem> mWalletItems;
    private WalletItem mCurrentWalletSyncing;
    private boolean mObesrverIsStarting;
    private SyncNotificationBroadcastReceiver mSyncNotificationBroadcastReceiver;

    public static final String IMAGE_RESOURCE_ID_PREFIX = "white_";

    public WalletListAdapter(Context context, ArrayList<BaseWalletManager> walletList) {
        this.mContext = context;
        mWalletItems = new ArrayList<>();
        for (BaseWalletManager w : walletList) {
            this.mWalletItems.add(new WalletItem(w));
        }

        mSyncNotificationBroadcastReceiver = new SyncNotificationBroadcastReceiver();
    }

    @Override
    public WalletItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = ((Activity) mContext).getLayoutInflater();
        View convertView = inflater.inflate(R.layout.wallet_list_item, parent, false);
        return new WalletItemViewHolder(convertView);
    }

    public BaseWalletManager getItemAt(int pos) {
        if (pos < mWalletItems.size()) {
            return mWalletItems.get(pos).walletManager;
        }

        return null;
    }

    @Override
    public void onBindViewHolder(final WalletItemViewHolder holder, int position) {


        WalletItem item = mWalletItems.get(position);
        final BaseWalletManager wallet = item.walletManager;
        String name = wallet.getName();
        String currencyCode = wallet.getIso();

        String fiatBalance = CurrencyUtils.getFormattedAmount(mContext, BRSharedPrefs.getPreferredFiatIso(mContext), wallet.getFiatBalance(mContext));
        String cryptoBalance = CurrencyUtils.getFormattedAmount(mContext, wallet.getIso(), wallet.getCachedBalance(mContext));

        // Set wallet fields
        holder.mWalletName.setText(currencyCode);
        holder.mIso.setText(name);
        holder.mWalletBalanceFiat.setText(cryptoBalance.replace(wallet.getIso(), ""));
        holder.mWalletBalanceCurrency.setText(fiatBalance);
        holder.mWalletBalanceCurrency.setVisibility(!item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
        holder.mSyncingProgressBar.setVisibility(item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
        holder.mSyncingLabel.setVisibility(item.mShowSyncProgress ? View.VISIBLE : View.INVISIBLE);
        holder.mSyncingLabel.setText(item.mLabelText);
        if((wallet instanceof WalletTokenManager))
            holder.mErc20Icon.setVisibility(View.VISIBLE);
        else
            holder.mErc20Icon.setVisibility(View.GONE);

//        String currencyCodeWithPrefix = IMAGE_RESOURCE_ID_PREFIX.concat(currencyCode).toLowerCase();


        int iconResourceId = 0;
        if(currencyCode.equalsIgnoreCase("1st")){
            iconResourceId = mContext.getResources().getIdentifier("first", BRConstants.DRAWABLE, mContext.getPackageName());
        } else {
            iconResourceId = mContext.getResources().getIdentifier(currencyCode.toLowerCase(), BRConstants.DRAWABLE, mContext.getPackageName());
        }
        if (iconResourceId > 0) {
//            holder.mLogoIcon.setBackground(mContext.getDrawable(iconResourceId));
            holder.mLogoIcon.setImageDrawable(mContext.getDrawable(iconResourceId));
        }

//        String startColor = wallet.getUiConfiguration().getStartColor();
//        String endColor = wallet.getUiConfiguration().getEndColor();

        Drawable drawable = mContext.getResources().getDrawable(R.drawable.crypto_card_shape, null).mutate();
        //create gradient with 2 colors if exist
//        ((GradientDrawable) drawable).setColors(new int[]{Color.parseColor(startColor), Color.parseColor(endColor == null ? startColor : endColor)});
        ((GradientDrawable) drawable).setColors(new int[]{Color.parseColor("#FFFFFF"), Color.parseColor("#FFFFFF")});
        ((GradientDrawable) drawable).setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        holder.mParent.setBackground(drawable);

    }

    public void stopObserving() {
        SyncService.unregisterSyncNotificationBroadcastReceiver(mContext.getApplicationContext(), mSyncNotificationBroadcastReceiver);
    }

    public void startObserving() {
        if (mObesrverIsStarting) {
            return;
        }
        mObesrverIsStarting = true;

        SyncService.registerSyncNotificationBroadcastReceiver(mContext.getApplicationContext(), mSyncNotificationBroadcastReceiver);

        BRExecutor.getInstance().forBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mCurrentWalletSyncing = getNextWalletToSync();
                    if (mCurrentWalletSyncing == null) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                for (WalletItem item : mWalletItems) {
                                    item.updateData(false);
                                }
                            }
                        });

                        return;
                    }
                    String walletIso = mCurrentWalletSyncing.walletManager.getIso();
                    mCurrentWalletSyncing.walletManager.connect(mContext);
                    SyncService.startService(mContext.getApplicationContext(), SyncService.ACTION_START_SYNC_PROGRESS_POLLING, walletIso);
                } finally {
                    mObesrverIsStarting = false;
                }

            }
        });

    }

    private boolean updateUi(WalletItem currentWallet, double syncProgress) {
        if (mCurrentWalletSyncing == null || mCurrentWalletSyncing.walletManager == null) {
            Log.e(TAG, "run: should not happen but ok, ignore it.");
            return false;
        }
        if (syncProgress > SyncService.PROGRESS_START && syncProgress < SyncService.PROGRESS_FINISH) {
//            Log.d(TAG, "ISO: " + currentWallet.walletManager.getIso(mContext) + " (" + progress + "%)");
            StringBuffer labelText = new StringBuffer(mContext.getString(R.string.SyncingView_syncing));
            labelText.append(' ')
                    .append(NumberFormat.getPercentInstance().format(syncProgress));

            mCurrentWalletSyncing.updateData(true, labelText.toString());
        } else if (syncProgress == SyncService.PROGRESS_FINISH) {
//            Log.d(TAG, "ISO: " + currentWallet.walletManager.getIso(mContext) + " (100%)");

            //Done should not be seen but if it is because of a bug or something, then let if be a decent explanation
            mCurrentWalletSyncing.updateData(false);

            //start from beginning
            startObserving();
            return false;

        }
        return true;
    }

    //return the next wallet that is not connected or null if all are connected
    private WalletItem getNextWalletToSync() {
        BaseWalletManager currentWallet = WalletsMaster.getInstance(mContext).getCurrentWallet(mContext);
        if (currentWallet != null && currentWallet.getSyncProgress(BRSharedPrefs.getStartHeight(mContext, currentWallet.getIso())) == 1) {
            currentWallet = null;
        }

        for (WalletItem w : mWalletItems) {
            if (currentWallet == null) {
                if (w.walletManager.getSyncProgress(BRSharedPrefs.getStartHeight(mContext, w.walletManager.getIso())) < 1
                        || w.walletManager.getConnectStatus() != 2) {
                    w.walletManager.connect(mContext);
                    return w;
                }
            } else {
                if (w.walletManager.getIso().equalsIgnoreCase(currentWallet.getIso())) {
                    return w;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return mWalletItems.size();
    }

    public class WalletItemViewHolder extends RecyclerView.ViewHolder {

        private BaseTextView mWalletName;
        private BaseTextView mIso;
        private BaseTextView mWalletBalanceFiat;
        private BaseTextView mWalletBalanceCurrency;
        private RelativeLayout mParent;
        private BaseTextView mSyncingLabel;
        private ProgressBar mSyncingProgressBar;
        private ImageView mLogoIcon;
        private BaseTextView mErc20Icon;

        public WalletItemViewHolder(View view) {
            super(view);

            mWalletName = view.findViewById(R.id.wallet_name);
            mIso = view.findViewById(R.id.wallet_trade_price);
            mWalletBalanceFiat = view.findViewById(R.id.wallet_balance_fiat);
            mWalletBalanceCurrency = view.findViewById(R.id.wallet_balance_currency);
            mParent = view.findViewById(R.id.wallet_card);
            mSyncingLabel = view.findViewById(R.id.syncing_label);
            mSyncingProgressBar = view.findViewById(R.id.sync_progress);
            mLogoIcon = view.findViewById(R.id.currency_icon_white);
            mErc20Icon = view.findViewById(R.id.erc20_flag);
        }
    }


    private class WalletItem {
        public BaseWalletManager walletManager;
        private boolean mShowSyncProgress = false;
        private String mLabelText;

        WalletItem(BaseWalletManager walletManager) {
            this.walletManager = walletManager;
        }

        public void updateData(boolean showSyncProgress) {
            updateData(showSyncProgress, null);
        }

        public void updateData(boolean showSyncProgress, String labelText) {
            mShowSyncProgress = showSyncProgress;

            if (labelText != null) {
                mLabelText = labelText;
            }

            notifyDataSetChanged();
        }
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

                if (mCurrentWalletSyncing == null) {
                    Log.e(TAG, "SyncNotificationBroadcastReceiver.onReceive: mCurrentWalletSyncing is null. Wallet:" + intentWalletIso + " Progress:" + progress + " Ignored");
                    return;
                }

                String currentWalletISO = mCurrentWalletSyncing.walletManager.getIso();
                if (currentWalletISO.equals(intentWalletIso)) {
                    if (progress >= SyncService.PROGRESS_START) {
                        updateUi(mCurrentWalletSyncing, progress);
                    } else {
                        Log.e(TAG, "SyncNotificationBroadcastReceiver.onReceive: Progress not set:" + progress);
                    }
                } else {
                    Log.e(TAG, "SyncNotificationBroadcastReceiver.onReceive: Wrong wallet. Expected:" + currentWalletISO + " Actual:" + intentWalletIso + " Progress:" + progress);
                }
            }
        }
    }
}
