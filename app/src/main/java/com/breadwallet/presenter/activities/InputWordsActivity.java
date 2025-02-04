package com.breadwallet.presenter.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.breadwallet.R;
import com.breadwallet.presenter.activities.intro.IntroActivity;
import com.breadwallet.presenter.activities.settings.UnlinkActivity;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.BRDialogView;
import com.breadwallet.presenter.customviews.PasteEditText;
import com.breadwallet.presenter.interfaces.EditPasteListener;
import com.breadwallet.tools.animation.BRDialog;
import com.breadwallet.tools.animation.SpringAnimator;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.manager.BRClipboardManager;
import com.breadwallet.tools.manager.BRReportsManager;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.AuthManager;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.security.PhraseInfo;
import com.breadwallet.tools.security.PostAuth;
import com.breadwallet.tools.security.SmartValidator;
import com.breadwallet.tools.sqlite.BRSQLiteHelper;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.StringUtil;
import com.breadwallet.tools.util.Utils;
import com.breadwallet.wallet.WalletsMaster;
import com.breadwallet.wallet.abstracts.BaseWalletManager;
import com.breadwallet.wallet.wallets.bitcoin.BaseBitcoinWalletManager;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputWordsActivity extends BRActivity implements View.OnFocusChangeListener {
    private static final String TAG = InputWordsActivity.class.getName();
    private Button mNextButton;

    private static final int NUMBER_OF_WORDS = 12;
    private static final int LAST_WORD_INDEX = 11;

    public static final String EXTRA_UNLINK = "com.breadwallet.EXTRA_UNLINK";
    public static final String EXTRA_RESET_PIN = "com.breadwallet.EXTRA_RESET_PIN";

    private List<EditText> mEditWords = new ArrayList<>(NUMBER_OF_WORDS);

    private String mDebugPhrase;

    //will be true if this screen was called from the restore screen
    private boolean mIsRestoring = false;
    private boolean mIsResettingPin = false;

    private boolean mReenter = false;
    private String mWalletName;

    private TypedValue mTypedValue = new TypedValue();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input_words);

//        if (Utils.isEmulatorOrDebug(this)) {
//            //japanese
//            mDebugPhrase = "こせき　ぎじにってい　けっこん　せつぞく　うんどう　ふこう　にっすう　こせい　きさま　なまみ　たきび　はかい";
//            //english
//            mDebugPhrase = "video tiger report bid suspect taxi mail argue naive layer metal surface";
//            //french
//            mDebugPhrase = "vocation triage capsule marchand onduler tibia illicite entier fureur minorer amateur lubie";
//            //spanish
//            mDebugPhrase = "zorro turismo mezcla nicho morir chico blanco pájaro alba esencia roer repetir";
//            //chinese
//            mDebugPhrase = "怨 贪 旁 扎 吹 音 决 廷 十 助 畜 怒";
//        }

        mNextButton = findViewById(R.id.send_button);

        getTheme().resolveAttribute(R.attr.input_words_text_color, mTypedValue, true);

        if (Utils.isUsingCustomInputMethod(this)) {
            BRDialog.showCustomDialog(this, getString(R.string.JailbreakWarnings_title), getString(R.string.Alert_customKeyboard_android),
                    getString(R.string.Button_ok), getString(R.string.JailbreakWarnings_close), new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            InputMethodManager imeManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
                            imeManager.showInputMethodPicker();
                            brDialogView.dismissWithAnimation();
                        }
                    }, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                        }
                    }, null, 0);
        }

        ImageButton faq = findViewById(R.id.faq_button);

        faq.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!UiUtils.isClickAllowed()) return;
                BaseWalletManager wm = WalletsMaster.getInstance(InputWordsActivity.this).getCurrentWallet(InputWordsActivity.this);
                UiUtils.showSupportFragment(InputWordsActivity.this, BRConstants.FAQ_PAPER_KEY, wm);
            }
        });

        TextView title = findViewById(R.id.title);
        TextView description = findViewById(R.id.description);

        mEditWords.add((EditText) findViewById(R.id.word1));
        mEditWords.add((EditText) findViewById(R.id.word2));
        mEditWords.add((EditText) findViewById(R.id.word3));
        mEditWords.add((EditText) findViewById(R.id.word4));
        mEditWords.add((EditText) findViewById(R.id.word5));
        mEditWords.add((EditText) findViewById(R.id.word6));
        mEditWords.add((EditText) findViewById(R.id.word7));
        mEditWords.add((EditText) findViewById(R.id.word8));
        mEditWords.add((EditText) findViewById(R.id.word9));
        mEditWords.add((EditText) findViewById(R.id.word10));
        mEditWords.add((EditText) findViewById(R.id.word11));
        mEditWords.add((EditText) findViewById(R.id.word12));

        for (EditText editText : mEditWords) {
            editText.setOnFocusChangeListener(this);
        }

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mIsRestoring = extras.getBoolean(EXTRA_UNLINK);
            mIsResettingPin = extras.getBoolean(EXTRA_RESET_PIN);
        }
        mReenter = getIntent().getBooleanExtra(IntroActivity.INTRO_REENTER, false);
        mWalletName = getIntent().getStringExtra(WalletNameActivity.WALLET_NAME);

        if (mIsRestoring) {
            //change the labels
            title.setText(getString(R.string.MenuViewController_recoverButton));
            description.setText(getString(R.string.WipeWallet_instruction));
        } else if (mIsResettingPin) {
            //change the labels
            title.setText(getString(R.string.RecoverWallet_header_reset_pin));
            description.setText(getString(R.string.RecoverWallet_subheader_reset_pin));
        }

        PasteEditText pasteEditText = (PasteEditText) mEditWords.get(0);
        pasteEditText.setPasteListener(new EditPasteListener() {
            @Override
            public void onPaste(View view) {
                String inputWords = BRClipboardManager.getClipboard(InputWordsActivity.this);
                String[] words = inputWords.trim().split(" ");
                if(words.length == 12) {
                    for(int i=0; i<12; i++) {
                        mEditWords.get(i).setText(words[i]);
                    }
                }

            }
        });

        mEditWords.get(LAST_WORD_INDEX).setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    mNextButton.performClick();
                }
                return false;
            }
        });

        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!UiUtils.isClickAllowed()) return;
                final Activity app = InputWordsActivity.this;
                String phraseToCheck = getPhrase();
                if (Utils.isEmulatorOrDebug(app) && !Utils.isNullOrEmpty(mDebugPhrase)) {
                    phraseToCheck = mDebugPhrase;
                }
                if (phraseToCheck == null) {
                    return;
                }
                String cleanPhrase = SmartValidator.cleanPaperKey(app, phraseToCheck);
                if (Utils.isNullOrEmpty(cleanPhrase)) {
                    BRReportsManager.reportBug(new NullPointerException("cleanPhrase is null or empty!"));
                    return;
                }
                if (SmartValidator.isPaperKeyValid(app, cleanPhrase)) {

                    if (mIsRestoring) {
                        restorePhrase(cleanPhrase, app);
                    } else if (mIsResettingPin) {
                        resetPin(cleanPhrase, app);
                    } else {
                        Utils.hideKeyboard(app);
//                        if (!mReenter) {
//                            WalletsMaster m = WalletsMaster.getInstance(InputWordsActivity.this);
//                            m.wipeAll(InputWordsActivity.this);
//                        }

                        UiUtils.switchPhrase(InputWordsActivity.this, cleanPhrase, mReenter, true, mWalletName);
                    }

                } else {
                    BRDialog.showCustomDialog(app, "", getResources().getString(R.string.RecoverWallet_invalid),
                            getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                                @Override
                                public void onClick(BRDialogView brDialogView) {
                                    brDialogView.dismissWithAnimation();
                                }
                            }, null, null, 0);

                }
            }
        });

    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.enter_from_left, R.anim.exit_to_right);
    }

    private String getPhrase() {
        boolean success = true;

        StringBuilder paperKeyStringBuilder = new StringBuilder();
        for (EditText editText : mEditWords) {
            String cleanedWords = clean(editText.getText().toString().toLowerCase());
            if (Utils.isNullOrEmpty(cleanedWords)) {
                SpringAnimator.failShakeAnimation(this, editText);
                success = false;
            } else {
                paperKeyStringBuilder.append(cleanedWords);
                paperKeyStringBuilder.append(' ');
            }
        }

        if (!success) {
            return null;
        }

        // Ensure the paper key is 12 words.
        String paperKey = paperKeyStringBuilder.toString().trim();
        int numberOfWords = paperKey.split(" ").length;
        if (numberOfWords != NUMBER_OF_WORDS) {
            BRReportsManager.reportBug(new IllegalArgumentException("Paper key contains " + numberOfWords + " words"));
            return null;
        }

        return paperKey;
    }

    private String clean(String word) {
        return word.trim().replaceAll(" ", "");
    }

    private void clearWords() {
        for (EditText editText : mEditWords) {
            editText.setText("");
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (!hasFocus) {
            validateWord((EditText) v);
        } else {
            ((EditText) v).setTextColor(getColor(mTypedValue.resourceId));
        }
    }

    private void validateWord(EditText view) {
        String word = view.getText().toString();
        boolean valid = SmartValidator.isWordValid(this, word);
        view.setTextColor(getColor(valid ? mTypedValue.resourceId : R.color.red_text));
        if (!valid) {
            SpringAnimator.failShakeAnimation(this, view);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == InputPinActivity.SET_PIN_REQUEST_CODE && resultCode == RESULT_OK) {

            boolean isPinAccepted = data.getBooleanExtra(InputPinActivity.EXTRA_PIN_ACCEPTED, false);
            if (isPinAccepted) {
                UiUtils.startBreadActivity(this, false);
            }
        }

    }

    private void restorePhrase(String cleanPhrase, final Activity app) {
        if (SmartValidator.isPaperKeyCorrect(cleanPhrase, app)) {
            Utils.hideKeyboard(app);
            clearWords();

            BRDialog.showCustomDialog(InputWordsActivity.this, getString(R.string.WipeWallet_alertTitle),
                    getString(R.string.WipeWallet_alertMessage), getString(R.string.WipeWallet_wipe), getString(R.string.Button_cancel), new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                            unlink();
                            if (!InputWordsActivity.this.isDestroyed())
                                finish();
                        }
                    }, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                        }
                    }, null, 0);


        } else {
            BRDialog.showCustomDialog(app, "", getString(R.string.RecoverWallet_invalid),
                    getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                        }
                    }, null, null, 0);
        }

    }

    private void resetPin(String cleanPhrase, final Activity app) {
        if (SmartValidator.isPaperKeyCorrect(cleanPhrase, app)) {
            Utils.hideKeyboard(app);
            clearWords();

            AuthManager.getInstance().setPinCode(InputWordsActivity.this, "");
            Intent intent = new Intent(app, InputPinActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            overridePendingTransition(R.anim.enter_from_right, R.anim.exit_to_left);
            startActivityForResult(intent, InputPinActivity.SET_PIN_REQUEST_CODE);


        } else {
            BRDialog.showCustomDialog(app, "", getString(R.string.RecoverWallet_invalid),
                    getString(R.string.AccessibilityLabels_close), null, new BRDialogView.BROnClickListener() {
                        @Override
                        public void onClick(BRDialogView brDialogView) {
                            brDialogView.dismissWithAnimation();
                        }
                    }, null, null, 0);
        }

    }

    private void unlink() {
        byte[] phrase;
        try {
            phrase = BRKeyStore.getPhrase(InputWordsActivity.this, 0);
        } catch (UserNotAuthenticatedException e) {
            return;
        }
        List<PhraseInfo> phraseList;
        try {
            phraseList = BRKeyStore.getPhraseInfoList(InputWordsActivity.this, 0);
        } catch (UserNotAuthenticatedException e) {
            e.printStackTrace();
            return;
        }

        PhraseInfo changeTo = null;
        assert phraseList != null;
        int count = phraseList.size();
        boolean restart = false;
        if (count == 1) {
            // the last one, clear all the data
            WalletsMaster m = WalletsMaster.getInstance(InputWordsActivity.this);
            m.wipeAll(InputWordsActivity.this);
            BRSQLiteHelper.getInstance(InputWordsActivity.this).close();
            restart = true;
        } else {
            int position = 0;
            for (int i = 0; i < count; i++) {
                if (Arrays.equals(phrase, phraseList.get(i).phrase)) {
                    position = i;
                    break;
                }
            }
            changeTo = phraseList.get(position + 1 >= count ? 0 : position + 1);
            BRSQLiteHelper.getInstance(InputWordsActivity.this).close();

            try {
                PhraseInfo info = new PhraseInfo();
                info.phrase = phrase;
                BRKeyStore.removePhraseInfo(InputWordsActivity.this, info);
            } catch (UserNotAuthenticatedException e) {
                e.printStackTrace();
            }
        }

        // delete database file
        String hash = UiUtils.getSha256(phrase);
        if (!StringUtil.isNullOrEmpty(hash)) {
            String database = hash + ".db";
            deleteDatabase(database);
            database = hash + "_platform.db";
            deleteDatabase(database);
            BRSharedPrefs.clearAllPrefs(InputWordsActivity.this);
        }

        if (restart) {
            UiUtils.restartApp(InputWordsActivity.this);
            return;
        }

        UiUtils.switchPhrase(InputWordsActivity.this, new String(changeTo.phrase), true, false, changeTo.alias);
    }

}
