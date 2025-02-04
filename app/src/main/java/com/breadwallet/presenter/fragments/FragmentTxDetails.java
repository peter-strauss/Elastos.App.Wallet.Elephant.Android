package com.breadwallet.presenter.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.core.ethereum.BREthereumAmount;
import com.breadwallet.core.ethereum.BREthereumToken;
import com.breadwallet.core.ethereum.BREthereumTransaction;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.entities.CurrencyEntity;
import com.breadwallet.presenter.entities.TxUiHolder;
import com.breadwallet.tools.adapter.TxProducerAdapter;
import com.breadwallet.tools.manager.BRClipboardManager;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.manager.TxManager;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.BRDateUtil;
import com.breadwallet.tools.util.CurrencyUtils;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.ela.ElaDataSource;
import com.breadwallet.wallet.wallets.ela.data.TxProducerEntity;
import com.breadwallet.wallet.wallets.ethereum.WalletEthManager;
import com.platform.entities.TxMetaData;
import com.platform.tools.KVStoreManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by byfieldj on 2/26/18.
 * <p>
 * Reusable dialog fragment that display details about a particular transaction
 */

public class FragmentTxDetails extends DialogFragment {

    private static final String TAG = FragmentTxDetails.class.getSimpleName();

    private TxUiHolder mTransaction;

    private BaseTextView mTxAction;
    private BaseTextView mTxAmount;
    private BaseTextView mTxStatus;
    private BaseTextView mTxDate;
    private BaseTextView mToFrom;
    private BaseTextView mToFromAddress;
    private BaseTextView mMemoText;

    private BaseTextView mGasPrice;
    private BaseTextView mGasLimit;
    private View mGasPriceDivider;
    private View mGasLimitDivider;

    private ConstraintLayout mFeePrimaryContainer;
    private ConstraintLayout mFeeSecondaryContainer;
    private ConstraintLayout mGasPriceContainer;
    private ConstraintLayout mGasLimitContainer;

    private BaseTextView mFeePrimaryLabel;
    private BaseTextView mFeePrimary;
    private View mFeePrimaryDivider;
    private BaseTextView mFeeSecondaryLabel;
    private BaseTextView mFeeSecondary;
    private View mFeeSecondaryDivider;

    private BaseTextView mExchangeRate;
    private BaseTextView mConfirmedInBlock;
    private BaseTextView mTransactionId;
    private BaseTextView mShowHide;
    private BaseTextView mAmountWhenSent;
    private BaseTextView mAmountNow;
    private BaseTextView mWhenSentLabel;
    private BaseTextView mNowLabel;

    private ConstraintLayout mConfirmedContainer;
    private View mConfirmedDivider;
    private TxMetaData mTxMetaData;

    private ImageButton mCloseButton;
    private LinearLayout mDetailsContainer;

    private BaseTextView mVoteTitleTv;
    private BaseTextView mPaseTv;
    private ListView mVoteNodeLv;

    boolean mDetailsShowing = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, 0);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return super.onCreateDialog(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.transaction_details, container, false);

        mAmountNow = rootView.findViewById(R.id.amount_now);
        mAmountWhenSent = rootView.findViewById(R.id.amount_when_sent);
        mTxAction = rootView.findViewById(R.id.tx_action);
        mTxAmount = rootView.findViewById(R.id.tx_amount);
        mNowLabel = rootView.findViewById(R.id.label_now);

        mTxStatus = rootView.findViewById(R.id.tx_status);
        mTxDate = rootView.findViewById(R.id.tx_date);
        mToFrom = rootView.findViewById(R.id.tx_to_from);
        mToFromAddress = rootView.findViewById(R.id.tx_to_from_address);
        mMemoText = rootView.findViewById(R.id.memo);
        mExchangeRate = rootView.findViewById(R.id.exchange_rate);
        mConfirmedInBlock = rootView.findViewById(R.id.confirmed_in_block_number);
        mTransactionId = rootView.findViewById(R.id.transaction_id);
        mShowHide = rootView.findViewById(R.id.show_hide_details);
        mDetailsContainer = rootView.findViewById(R.id.details_container);
        mCloseButton = rootView.findViewById(R.id.close_button);

        mConfirmedContainer = rootView.findViewById(R.id.confirmed_container);
        mConfirmedDivider = rootView.findViewById(R.id.confirmed_divider);

        mFeePrimaryLabel = rootView.findViewById(R.id.fee_primary_label);
        mFeePrimary = rootView.findViewById(R.id.fee_primary);
        mFeePrimaryDivider = rootView.findViewById(R.id.fee_primary_divider);

        mFeeSecondaryLabel = rootView.findViewById(R.id.fee_secondary_label);
        mFeeSecondary = rootView.findViewById(R.id.fee_secondary);
        mFeeSecondaryDivider = rootView.findViewById(R.id.fee_secondary_divider);

        mGasPrice = rootView.findViewById(R.id.gas_price);
        mGasLimit = rootView.findViewById(R.id.gas_limit);
        mGasPriceDivider = rootView.findViewById(R.id.gas_price_divider);
        mGasLimitDivider = rootView.findViewById(R.id.gas_limit_divider);

        mFeePrimaryContainer = rootView.findViewById(R.id.fee_primary_container);
        mFeeSecondaryContainer = rootView.findViewById(R.id.fee_secondary_container);
        mGasPriceContainer = rootView.findViewById(R.id.gas_price_container);
        mGasLimitContainer = rootView.findViewById(R.id.gas_limit_container);
        mWhenSentLabel = rootView.findViewById(R.id.label_when_sent);

        mVoteTitleTv = rootView.findViewById(R.id.vote_nodes_list_title);
        mPaseTv = rootView.findViewById(R.id.transaction_detail_vote_paste_tv);
        mVoteNodeLv = rootView.findViewById(R.id.transaction_detail_vote_node_lv);

        mCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        mMemoText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        int color = mToFromAddress.getTextColors().getDefaultColor();
        mMemoText.setTextColor(color);

        initListener();
        updateUi();
        initTxAdapter();
        return rootView;
    }

    private void initListener(){
        mShowHide.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mDetailsShowing) {
                    mDetailsContainer.setVisibility(View.VISIBLE);
                    mDetailsShowing = true;
                    mShowHide.setText(getString(R.string.TransactionDetails_titleFailed));
                } else {
                    mDetailsContainer.setVisibility(View.GONE);
                    mDetailsShowing = false;
                    mShowHide.setText(getString(R.string.TransactionDetails_showDetails));
                }
            }
        });

        mPaseTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyText();
            }
        });
    }

    private void copyText() {
        StringBuilder sb = new StringBuilder();
        if(mProducers==null || mProducers.size()<=0) return;
        for(TxProducerEntity txProducerEntity : mProducers){
            sb.append(txProducerEntity.Nickname).append("\n");
        }
        BRClipboardManager.putClipboard(getContext(), sb.toString());
        Toast.makeText(getContext(), getString(R.string.Receive_copied), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    public void setTransaction(TxUiHolder item) {
        this.mTransaction = item;
    }

    private TxProducerAdapter mAdapter;
    private List<TxProducerEntity> mProducers = new ArrayList<>();
    private void initTxAdapter(){
        List<TxProducerEntity> tmp = ElaDataSource.getInstance(getContext()).getTxProducerByTxid(mTransaction.txReversed);
        if(mTransaction.isVote() && (tmp!=null && tmp.size()>0)) {
            mVoteTitleTv.setVisibility(View.VISIBLE);
            mPaseTv.setVisibility(View.VISIBLE);
            mVoteNodeLv.setVisibility(View.VISIBLE);

            mProducers.clear();
            mProducers.addAll(tmp);
            mVoteTitleTv.setText(String.format(getString(R.string.node_list_title), tmp.size()));
            mAdapter = new TxProducerAdapter(getContext(), mProducers);
            mVoteNodeLv.setAdapter(mAdapter);
        } else {
            mVoteTitleTv.setVisibility(View.GONE);
            mPaseTv.setVisibility(View.GONE);
            mVoteNodeLv.setVisibility(View.GONE);
        }
    }

    private void updateUi() {
        Activity app = getActivity();

        BaseWalletManager walletManager = WalletsMaster.getInstance(app).getCurrentWallet(app);

        // Set mTransction fields
        if (mTransaction != null) {
            //user prefers crypto (or fiat)
            boolean isCryptoPreferred = BRSharedPrefs.isCryptoPreferred(app);
            String cryptoIso = walletManager.getIso();
            String fiatIso = BRSharedPrefs.getPreferredFiatIso(getContext());
            boolean isErc20 = WalletsMaster.getInstance(app).isIsoErc20(app, cryptoIso);

            String iso = isCryptoPreferred ? cryptoIso : fiatIso;

            boolean received = mTransaction.isReceived();

            String amountWhenSent;
            String amountNow;
            String exchangeRateFormatted;

            if (received) hideSentViews();
            else {
                BREthereumTransaction ethTx = null;
                if (mTransaction.getTransaction() instanceof BREthereumTransaction)
                    ethTx = (BREthereumTransaction) mTransaction.getTransaction();
                BigDecimal rawFee = mTransaction.getFee();
                //meaning ETH
                if (ethTx != null && !isErc20) {
                    mGasPrice.setText(String.format("%s %s", new BigDecimal(ethTx.getGasPrice(BREthereumAmount.Unit.ETHER_GWEI)).stripTrailingZeros().toPlainString(), "gwei"));
                    mGasLimit.setText(new BigDecimal(ethTx.getGasLimit()).toPlainString());
                    long gas = ethTx.isConfirmed() ? ethTx.getGasUsed() : ethTx.getGasLimit();
                    rawFee = new BigDecimal(gas).multiply(new BigDecimal(ethTx.getGasPrice(walletManager.getUnit())));
                } else {
                    hideEthViews();

                }

                BigDecimal fee = /*isCryptoPreferred ? rawFee.abs() : walletManager.getFiatForSmallestCrypto(app, rawFee, null).abs()*/ rawFee.abs();
                Log.i("fee", "fee:"+fee.longValue());
                BigDecimal rawTotalSent = mTransaction.getAmount().abs().add(rawFee.abs());
                BigDecimal totalSent = /*isCryptoPreferred ? rawTotalSent : walletManager.getFiatForSmallestCrypto(app, rawTotalSent, null)*/rawTotalSent;
                mFeeSecondary.setText(CurrencyUtils.getFormattedAmount(app, walletManager.getIso(), totalSent));
//                mFeePrimary.setText(CurrencyUtils.getFormattedAmount(app, iso, fee));
                mFeePrimary.setText(CurrencyUtils.getFormattedAmount(app, walletManager.getIso(), fee));
                mFeePrimaryLabel.setText(String.format(getString(R.string.Send_fee), ""));
                mFeeSecondaryLabel.setText(getString(R.string.Confirmation_totalLabel));

                //erc20s
                if (isErc20) {
                    hideTotalCost();
                    mFeePrimary.setText(String.format("%s %s", mTransaction.getFee().stripTrailingZeros().toPlainString(), "gwei"));
                }
            }

            if (mTransaction.getBlockHeight() == Integer.MAX_VALUE) {
                hideConfirmedView();
            }

            if (!mTransaction.isValid()) {
                mTxStatus.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }

            BigDecimal cryptoAmount = mTransaction.getAmount().setScale(walletManager.getMaxDecimalPlaces(app), BRConstants.ROUNDING_MODE);
            BREthereumToken tkn = null;
            if (walletManager.getIso().equalsIgnoreCase("ETH"))
                tkn = WalletEthManager.getInstance(app).node.lookupToken(mTransaction.getTo());
            if (tkn != null) cryptoAmount = mTransaction.getFee(); // it's a token transfer ETH tx

            BigDecimal fiatAmountNow = walletManager.getFiatForSmallestCrypto(app, cryptoAmount.abs(), null);

            BigDecimal fiatAmountWhenSent;
            TxMetaData metaData = KVStoreManager.getInstance().getTxMetaData(app, mTransaction.getTxHash());
            if (metaData == null || metaData.exchangeRate == 0 || Utils.isNullOrEmpty(metaData.exchangeCurrency)) {
                fiatAmountWhenSent = BigDecimal.ZERO;
                //always fiat amount
                amountWhenSent = CurrencyUtils.getFormattedAmount(app, fiatIso, fiatAmountWhenSent);
            } else {
                CurrencyEntity ent = new CurrencyEntity(metaData.exchangeCurrency, null, (float) metaData.exchangeRate, walletManager.getIso());
                fiatAmountWhenSent = walletManager.getFiatForSmallestCrypto(app, cryptoAmount.abs(), ent);
                //always fiat amount
                amountWhenSent = CurrencyUtils.getFormattedAmount(app, ent.code, fiatAmountWhenSent);

            }

            //always fiat amount
            amountNow = CurrencyUtils.getFormattedAmount(app, fiatIso, fiatAmountNow);

            mAmountWhenSent.setText(amountWhenSent);
            mAmountNow.setText(amountNow);

            // If 'amount when sent' is 0 or unavailable, show fiat tx amount on its own
            if (fiatAmountWhenSent.compareTo(BigDecimal.ZERO) == 0) {
                mAmountWhenSent.setVisibility(View.INVISIBLE);
                mWhenSentLabel.setVisibility(View.INVISIBLE);
                mNowLabel.setVisibility(View.INVISIBLE);

                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.addRule(RelativeLayout.CENTER_HORIZONTAL);
                params.addRule(RelativeLayout.BELOW, mTxAmount.getId());
                mAmountNow.setLayoutParams(params);

            }

            mTxAction.setText(!received ? getString(R.string.TransactionDetails_titleSent) : getString(R.string.TransactionDetails_titleReceived));
            mToFrom.setText(!received ? getString(R.string.Confirmation_to) + " " : getString(R.string.TransactionDetails_addressViaHeader) + " ");

            String from = mTransaction.getFrom();
            if(StringUtil.isNullOrEmpty(from)){
                mToFromAddress.setText(mTransaction.getTo());
            } else {
                mToFromAddress.setText(walletManager.decorateAddress(received?mTransaction.getFrom():mTransaction.getTo())); //showing only the destination address
            }

            // Allow the to/from address to be copyable
            mToFromAddress.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Get the default color based on theme
                    final int color = mToFromAddress.getCurrentTextColor();

                    mToFromAddress.setTextColor(getContext().getColor(R.color.light_gray));
                    String address = mToFromAddress.getText().toString();
                    BRClipboardManager.putClipboard(getContext(), address);
                    Toast.makeText(getContext(), getString(R.string.Receive_copied), Toast.LENGTH_LONG).show();

                    mToFromAddress.setTextColor(color);


                }
            });

            //this is always crypto amount
            mTxAmount.setText(CurrencyUtils.getFormattedAmount(app, walletManager.getIso(), received ? cryptoAmount : cryptoAmount.negate()));

            if (received) {
                mTxAmount.setTextColor(getContext().getColor(R.color.transaction_amount_received_color));
            }

            // Set the memo text if one is available
            String memo;
            mTxMetaData = KVStoreManager.getInstance().getTxMetaData(app, mTransaction.getTxHash());

            if(walletManager.getIso().equalsIgnoreCase("ELA") || walletManager.getIso().equalsIgnoreCase("IOEX")){
                mMemoText.setText(mTransaction.memo);
            } else if (mTxMetaData != null) {
                if (mTxMetaData.comment != null) {
                    memo = mTxMetaData.comment;
                    mMemoText.setText(memo);
                } else {
                    mMemoText.setText("");
                }
                String metaIso = Utils.isNullOrEmpty(mTxMetaData.exchangeCurrency) ? "USD" : mTxMetaData.exchangeCurrency;

                exchangeRateFormatted = CurrencyUtils.getFormattedAmount(app, metaIso, new BigDecimal(mTxMetaData.exchangeRate));
                mExchangeRate.setText(exchangeRateFormatted);
            } else {
                mMemoText.setText("");

            }
            if (tkn != null) { // it's a token transfer ETH tx
                mMemoText.setText(String.format(app.getString(R.string.Transaction_tokenTransfer), tkn.getSymbol()));
                mMemoText.setFocusable(false);
            }

            // timestamp is 0 if it's not confirmed in a block yet so make it now
            mTxDate.setText(BRDateUtil.getFullDate(mTransaction.getTimeStamp() == 0 ? System.currentTimeMillis() : (mTransaction.getTimeStamp() * DateUtils.SECOND_IN_MILLIS)));

            // Set the transaction id
            mTransactionId.setText(mTransaction.getHashReversed());

            // Allow the transaction id to be copy-able
            mTransactionId.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

//                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(ElaDataSource.getUrl("/tx/"+mTransactionId.getText().toString())));
//                    startActivity(browserIntent);
//                    getActivity().overridePendingTransition(R.anim.enter_from_bottom, R.anim.empty_300);

                    // Get the default color based on theme
                    final int color = mTransactionId.getCurrentTextColor();

                    mTransactionId.setTextColor(getContext().getColor(R.color.light_gray));
                    String id = mTransaction.getHashReversed();
                    BRClipboardManager.putClipboard(getContext(), id);
                    Toast.makeText(getContext(), getString(R.string.Receive_copied), Toast.LENGTH_LONG).show();

                    mTransactionId.setTextColor(color);

                }
            });

            // Set the transaction block number
            mConfirmedInBlock.setText(String.valueOf(mTransaction.getBlockHeight()));

        } else {
            Toast.makeText(getContext(), "Error getting transaction data", Toast.LENGTH_SHORT).show();
        }

    }

    private void hideSentViews() {
        mDetailsContainer.removeView(mFeePrimaryContainer);
        mDetailsContainer.removeView(mFeeSecondaryContainer);
        mDetailsContainer.removeView(mFeePrimaryDivider);
        mDetailsContainer.removeView(mFeeSecondaryDivider);
        hideEthViews();
    }

    private void hideEthViews() {
        mDetailsContainer.removeView(mGasPriceContainer);
        mDetailsContainer.removeView(mGasLimitContainer);
        mDetailsContainer.removeView(mGasPriceDivider);
        mDetailsContainer.removeView(mGasLimitDivider);
    }

    private void hideTotalCost() {
        mDetailsContainer.removeView(mFeeSecondaryContainer);
        mDetailsContainer.removeView(mFeeSecondaryDivider);
    }

    private void hideConfirmedView() {
        mDetailsContainer.removeView(mConfirmedContainer);
        mDetailsContainer.removeView(mConfirmedDivider);
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Override
    public void onPause() {
        super.onPause();
        // Update the memo field on the transaction and save it
        if (mTxMetaData == null) mTxMetaData = new TxMetaData();
        mTxMetaData.comment = mMemoText.getText().toString();
        if(null != mTransaction){
            KVStoreManager.getInstance().putTxMetaData(getContext(), mTxMetaData, mTransaction.getTxHash());
        }
        mTxMetaData = null;

        // Hide softkeyboard if it's visible
        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mMemoText.getWindowToken(), 0);

        // Update Tx list to reflect the memo change
        TxManager.getInstance().updateTxList(getActivity());
    }

    public interface OnPauseListener {
        void onPaused();
    }
}
