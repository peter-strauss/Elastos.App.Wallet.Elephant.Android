package com.breadwallet.presenter.activities.did;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.presenter.activities.settings.BaseSettingsActivity;
import com.breadwallet.presenter.customviews.BaseTextView;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.elastos.jni.Constants;
import com.elastos.jni.UriFactory;

import java.util.Calendar;
import java.util.Date;

public class HyperGetAddressActivity extends BaseSettingsActivity {
    private static final String TAG = "hyper_get_address";

    private BaseTextView addressText;

    private Button mDenyBtn;

    private Button mAuthorizeBtn;

    @Override
    public int getLayoutId() {
        return R.layout.activity_hyper_get_address_layout;
    }

    @Override
    public int getBackButtonId() {
        return R.id.back_button;
    }

    private String mUri;

    private String elaAddress;

    private LoadingDialog mLoadingDialog;

    private UriFactory uriFactory;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            if (!StringUtil.isNullOrEmpty(action) && action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                Log.i(TAG, "server mUri: " + uri.toString());
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
            }
        }
        initView();
        initListener();
        initData();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null) {
            String action = intent.getAction();
            if(StringUtil.isNullOrEmpty(action)) return;
            if (action.equals(Intent.ACTION_VIEW)) {
                Uri uri = intent.getData();
                Log.i(TAG, "server mUri: " + uri.toString());
                mUri = uri.toString();
            } else {
                mUri = intent.getStringExtra(Constants.INTENT_EXTRA_KEY.META_EXTRA);
            }
        }
    }

    private void initView() {
        addressText = findViewById(R.id.addressText);
        mDenyBtn = findViewById(R.id.deny_btn);
        mAuthorizeBtn = findViewById(R.id.authorize_btn);
        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);
    }

    private void initListener() {
        mDenyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mAuthorizeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                author();
            }
        });
    }

    private void initData(){
        BaseWalletManager ela=WalletsMaster.getInstance(this).getWalletByIso(this, "ELA");
        if(ela!=null){
            elaAddress=ela.getAddress();
            addressText.setText(elaAddress);
        }
        else{
            dialogDismiss();
            finish();
        }
    }

    private void author() {
        String mn = getMn();
        if (StringUtil.isNullOrEmpty(mn)) {
            Toast.makeText(HyperGetAddressActivity.this, "Not yet created Wallet", Toast.LENGTH_SHORT).show();
            return;
        }

        if (StringUtil.isNullOrEmpty(mUri)) {
            Toast.makeText(HyperGetAddressActivity.this, "invalid params", Toast.LENGTH_SHORT).show();
            return;
        }

        if(!StringUtil.isNullOrEmpty(elaAddress)){
            Intent replyIntent=new Intent();
            replyIntent.putExtra("address", elaAddress);
            setResult(RESULT_OK, replyIntent);
            finish();
        }
    }

    private void dialogDismiss() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing())
                    mLoadingDialog.dismiss();
            }
        });
    }

    private long getAuthorTime(int day) {
        Date date = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(calendar.DATE, day);
        date = calendar.getTime();
        long time = date.getTime();

        return time;
    }

    private String getMn() {
        byte[] phrase = null;
        try {
            phrase = BRKeyStore.getPhrase(this, 0);
            if (phrase != null) {
                return new String(phrase);
            }
        } catch (UserNotAuthenticatedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(null != mLoadingDialog){
            mLoadingDialog.dismiss();
            mLoadingDialog = null;
        }
    }
}

