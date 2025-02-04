package com.breadwallet.presenter.activities;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.security.keystore.UserNotAuthenticatedException;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.breadwallet.R;
import com.breadwallet.presenter.activities.util.BRActivity;
import com.breadwallet.presenter.customviews.LoadingDialog;
import com.breadwallet.presenter.entities.MyAppItem;
import com.breadwallet.presenter.entities.RegisterChainData;
import com.breadwallet.presenter.entities.StringChainData;
import com.breadwallet.presenter.fragments.FragmentExplore;
import com.breadwallet.tools.adapter.ExploreAppsAdapter;
import com.breadwallet.tools.animation.UiUtils;
import com.breadwallet.tools.crypto.CryptoHelper;
import com.breadwallet.tools.listeners.OnStartDragListener;
import com.breadwallet.tools.manager.BRSharedPrefs;
import com.breadwallet.tools.security.BRKeyStore;
import com.breadwallet.tools.sqlite.ProfileDataSource;
import com.breadwallet.tools.threads.executor.BRExecutor;
import com.breadwallet.tools.util.BRConstants;
import com.breadwallet.tools.util.StringUtil;
import com.elastos.jni.Utility;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.elastos.sdk.wallet.BlockChainNode;
import org.elastos.sdk.wallet.Did;
import org.elastos.sdk.wallet.DidManager;
import org.elastos.sdk.wallet.Identity;
import org.elastos.sdk.wallet.IdentityManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ExploreActivity extends BRActivity implements OnStartDragListener, ExploreAppsAdapter.OnDeleteClickListener,
        ExploreAppsAdapter.OnTouchMoveListener,
        ExploreAppsAdapter.OnAboutClickListener,
        ExploreAppsAdapter.OnItemClickListener{

    private static final String TAG = ExploreActivity.class.getSimpleName();

    private RecyclerView mMyAppsRv;
    private ExploreAppsAdapter mAdapter;
    private View mDisclaimLayout;
    private View mPopLayout;
    private ItemTouchHelper mItemTouchHelper;
    private View mDoneBtn;
    private View mCancelBtn;
    private View mAddBtn;
    private View mEditBtn;
    private View mOkBtn;
    private View mEditPopView;
    private View mAddPopView;
    private View mAddUrlView;
    private View mAddScanView;
    private View mAboutView;
    private View mAboutShareView;
    private View mAboutAboutView;
    private View mAboutCancelView;
    private LoadingDialog mLoadingDialog;
    private static final int INIT_APPS_MSG = 0x01;
    private static final int UPDATE_APPS_MSG = 0x02;
    private static final int UNREGISTER_RECEIVER = 0x03;

    private Did mDid;
    private String mSeed;
    private String mPublicKey;
    private String mDidStr;

    private List<String> mRemoveAppId = new ArrayList<>();
    private List<String> mAppIds = new ArrayList<>();
    private static boolean mIsLongPressDragEnabled = false;

    private MyAppItem mAboutAppItem = null;
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            int flag = msg.what;
            switch (flag) {
                case INIT_APPS_MSG:
//                    List<MyAppItem> tmp = ProfileDataSource.getInstance(this).getMyAppItems();
//                    mItems.addAll(tmp);
//                    mAdapter.notifyDataSetChanged();
                    break;
                case UPDATE_APPS_MSG:
                    ProfileDataSource.getInstance(ExploreActivity.this).updateMyAppItem(mItems);
                    mAdapter.notifyDataSetChanged();
                    break;
                case UNREGISTER_RECEIVER:

                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_explore_layout);

        initView();
        initAdapter();
        initListener();
        initDid();
        initApps();
    }

    private void initView(){
        mDisclaimLayout = findViewById(R.id.disclaim_layout);
        mPopLayout = findViewById(R.id.explore_menu_pop_layout);
        mOkBtn = findViewById(R.id.disclaim_ok_btn);
        mDoneBtn = findViewById(R.id.explore_done_tv);
        mCancelBtn = findViewById(R.id.explore_cancel_tv);
        mAddBtn = findViewById(R.id.explore_add_tv);
        mEditBtn = findViewById(R.id.explore_edit_tv);
        mMyAppsRv = findViewById(R.id.app_list_rv);
        mEditPopView = findViewById(R.id.explore_edit_pop);
        mAddPopView = findViewById(R.id.explore_add_pop);
        mAddUrlView = findViewById(R.id.explore_url_pop);
        mAddScanView = findViewById(R.id.explore_scan_pop);
        mAboutView = findViewById(R.id.explore_about_layout);
        mAboutShareView = findViewById(R.id.share_tv);
        mAboutAboutView = findViewById(R.id.about_tv);
        mAboutCancelView = findViewById(R.id.cancel_tv);
        if (BRSharedPrefs.getDisclaimShow(this))
            mDisclaimLayout.setVisibility(View.VISIBLE);
        mLoadingDialog = new LoadingDialog(this, R.style.progressDialog);
        mLoadingDialog.setCanceledOnTouchOutside(false);
    }

    private void initListener(){
        mAddBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopLayout.setVisibility(mAddPopView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mAddPopView.setVisibility(mAddPopView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mEditPopView.setVisibility(View.GONE);
            }
        });

        mEditBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopLayout.setVisibility(mEditPopView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mEditPopView.setVisibility(mEditPopView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                mAddPopView.setVisibility(View.GONE);
            }
        });

        mEditPopView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEditPopView.setVisibility(View.GONE);
                mPopLayout.setVisibility(View.GONE);
                changeView(true);
                mAdapter.isDelete(true);
                mIsLongPressDragEnabled = true;
                mAdapter.notifyDataSetChanged();
            }
        });

        mAddUrlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopLayout.setVisibility(View.GONE);
                UiUtils.startAddAppsActivity(ExploreActivity.this, BRConstants.ADD_APP_URL_REQUEST);
            }
        });

        mAddScanView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPopLayout.setVisibility(View.GONE);
                UiUtils.openScanner(ExploreActivity.this, BRConstants.ADD_APP_URL_REQUEST);
            }
        });

        mCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeView(false);
            }
        });

        mDoneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeView(false);
                mAdapter.isDelete(false);
                mIsLongPressDragEnabled = false;
                if (mRemoveAppId.size() > 0) {
                    for (String appId : mRemoveAppId) {
                        ProfileDataSource.getInstance(ExploreActivity.this).deleteAppItem(appId);
                        upAppStatus(appId, "deleted");
                    }
                    mRemoveAppId.clear();
                }
                mHandler.sendEmptyMessage(UPDATE_APPS_MSG);
            }
        });

        mOkBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDisclaimLayout.setVisibility(View.GONE);
                BRSharedPrefs.setDisclaimshow(ExploreActivity.this, false);
            }
        });
        mDisclaimLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return true;
            }
        });
        mPopLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mPopLayout.setVisibility(View.GONE);
                mAddPopView.setVisibility(View.GONE);
                mEditPopView.setVisibility(View.GONE);
                return true;
            }
        });

        mAboutShareView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAboutView.setVisibility(View.GONE);
                if (null != mAboutAppItem) {
//                    QRUtils.share("mini apps url:", this, mAboutAppItem.path);
                    shareApps(ExploreActivity.this, mAboutAppItem.name, mAboutAppItem.path);
                }
            }
        });

        mAboutAboutView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAboutView.setVisibility(View.GONE);
                UiUtils.startMiniAppAboutActivity(ExploreActivity.this, mAboutAppItem.appId);
            }
        });

        mAboutCancelView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAboutView.setVisibility(View.GONE);
            }
        });

        mAdapter.setOnDeleteClick(this);
        mAdapter.setOnMoveListener(this);
        mAdapter.setOnAboutClick(this);
        mAdapter.setOnItemClick(this);
    }

    private void initDid() {
        if (null == mDid || null == mPublicKey) {
            String mnemonic = getMn();
            if (StringUtil.isNullOrEmpty(mnemonic)) return;
            mSeed = IdentityManager.getSeed(mnemonic, "");
            if (StringUtil.isNullOrEmpty(mSeed)) return;
            Identity identity = IdentityManager.createIdentity(this.getFilesDir().getAbsolutePath());
            DidManager didManager = identity.createDidManager(mSeed);
            BlockChainNode node = new BlockChainNode(ProfileDataSource.DID_URL);
            mDid = didManager.createDid(0);
            mDid.setNode(node);
            mPublicKey = Utility.getInstance(this).getSinglePublicKey(mnemonic);
            mDidStr = Utility.getInstance(this).getDid(mPublicKey);
        }
    }


    public void initApps() {
        mAppIds.clear();
        mItems.clear();
        mRemoveAppId.clear();
        List<MyAppItem> tmp = ProfileDataSource.getInstance(this).getMyAppItems();
        if (tmp != null && tmp.size() > 0) { //database
            mItems.addAll(tmp);
            for (MyAppItem item : tmp) {
                mAppIds.add(item.appId);
            }
            mAdapter.notifyDataSetChanged();
        }
        resetAppsFromNet();
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

    private void resetAppsFromNet() {
        BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
            @Override
            public void run() {
                //inter app
                getInterApps();

                //third app
                StringChainData miniApps = getMiniApps();
                if (null == miniApps || StringUtil.isNullOrEmpty(miniApps.value)) return;
                List<String> appIds = new Gson().fromJson(miniApps.value, new TypeToken<List<String>>() {
                }.getType());
                if (null == appIds) return;
                for (String appId : appIds) {
                    if (StringUtil.isNullOrEmpty(appId)) break;
                    if (appId.equals(BRConstants.REA_PACKAGE_ID) ||
                            appId.equals(BRConstants.DPOS_VOTE_ID) ||
                            appId.equals(BRConstants.EXCHANGE_ID)) break;
                    StringChainData appStatus = getAppStatus(appId);
                    if (null == appStatus ||
                            StringUtil.isNullOrEmpty(appStatus.value) ||
                            appStatus.value.equals("normal")) {
                        StringChainData appUrlEntity = getAppsUrl(appId);
                        if (null == appUrlEntity || StringUtil.isNullOrEmpty(appUrlEntity.value))
                            return;
                        downloadCapsule(appUrlEntity.value);
                    }
                }
            }
        });
    }

    private DownloadManager manager;
    private String mDoloadFileName;
    public long downloadCapsule(String url) {
        Log.d(TAG, "capsule url:" + url);
        if (StringUtil.isNullOrEmpty(url)) return -1;
        DownloadManager.Request request;
        try {
            Uri uri = Uri.parse(url);
            mDoloadFileName = uri.getLastPathSegment();
            if (StringUtil.isNullOrEmpty(mDoloadFileName)) return -1;
            //TODO
            File downloadFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile(), mDoloadFileName);
            if (downloadFile.exists()) {
                downloadFile.delete();
            }

            request = new DownloadManager.Request(uri);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, mDoloadFileName);
            manager = (DownloadManager) getApplicationContext().getSystemService(Context.DOWNLOAD_SERVICE);
            long downloadId = manager.enqueue(request);
            registerDownloadReceiver();
            return downloadId;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void registerDownloadReceiver() {
        IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            File srcPath = new File(ExploreActivity.this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile(), mDoloadFileName).getAbsoluteFile();
                            File outPath = new File(ExploreActivity.this.getExternalCacheDir().getAbsoluteFile(), "capsule/" + mDoloadFileName).getAbsoluteFile();
                            if (srcPath == null || outPath == null || !srcPath.exists()) return;
                            decompression(srcPath.getAbsolutePath(), outPath.getAbsolutePath());
//                            logFile("srcPath", this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile());
//                            logFile("outPath", new File(this.getExternalCacheDir().getAbsoluteFile(), "capsule").getAbsoluteFile());

                            File appJsonPath = new File(outPath, "/app.json");
                            String json = getJsonFromCapsule(appJsonPath);
                            MyAppItem item = new Gson().fromJson(json, MyAppItem.class);
                            item.path = new File(outPath, mDoloadFileName).getAbsolutePath();

                            String hash = CryptoHelper.getShaChecksum(srcPath.getAbsolutePath());
                            Log.d(TAG, "mDoloadFileName:"+mDoloadFileName+" hash:"+hash);

                            String key = "Dev/"+item.name+"/Release/"+item.platform+"/"+item.version;
                            RegisterChainData appSetting = ProfileDataSource.getInstance(ExploreActivity.this).getMiniAppSetting(item.did, key);

                            if (StringUtil.isNullOrEmpty(hash) ||
                                    null == appSetting ||
                                    StringUtil.isNullOrEmpty(appSetting.hash) ||
                                    !appSetting.hash.equals(hash)) {
                                Toast.makeText(ExploreActivity.this, "Illegal capsule ", Toast.LENGTH_SHORT).show();
                                deleteFile(srcPath);
                                deleteFile(outPath);
                                return;
                            }
                            Log.d(TAG, "capsule legitimacy");

                            if (item != null) {
                                for (String appId : mAppIds) {
                                    if (item.appId.equals(appId)) return;
                                }
                                mAppIds.add(item.appId);
                                mItems.add(item);
                                mHandler.sendEmptyMessage(UPDATE_APPS_MSG);

                                upAppStatus(item.appId, "normal");
                                upAppUrlData(item.appId, item.url);
                                upAppIds(mAppIds);
                            }
                            deleteFile(srcPath);
                            deleteFile(outPath);
//
//                            logFile("srcPath", this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile());
//                            logFile("outPath", new File(this.getExternalCacheDir().getAbsoluteFile(), "capsule").getAbsoluteFile());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                context.unregisterReceiver(this);
            }
        };
        this.registerReceiver(broadcastReceiver, intentFilter);
    }

    public static void shareApps(Context context, String title, String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");
        context.startActivity(Intent.createChooser(sendIntent, title));
    }

    private void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                deleteFile(f);
            }
            file.delete();
        } else if (file.exists()) {
            file.delete();
        }
    }

    private void upAppIds(List<String> appIds) {
        String ids = new Gson().toJson(appIds);
        String path = mDidStr + "/Apps";
        String data = getKeyVale(path, ids);
        String info = mDid.signInfo(mSeed, data, false);
        if(!StringUtil.isNullOrEmpty(info)) {
            ProfileDataSource.getInstance(this).upchainSync(info);
        }
    }

    private void upAppUrlData(final String miniAppId, final String value) {
        if (StringUtil.isNullOrEmpty(miniAppId) || StringUtil.isNullOrEmpty(value)) return;
        String path = mDidStr + "/Apps/" + miniAppId;
        String data = getKeyVale(path, value);
        String info = mDid.signInfo(mSeed, data, false);
        if(!StringUtil.isNullOrEmpty(info)) {
            ProfileDataSource.getInstance(this).upchainSync(info);
        }
    }

    private void getInterApps() {
        String downloadFile = this.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        StringChainData redPackageStatus = getAppStatus(BRConstants.REA_PACKAGE_ID);
        if (null == redPackageStatus ||
                StringUtil.isNullOrEmpty(redPackageStatus.value) ||
                redPackageStatus.value.equals("normal")) {
            copyCapsuleToDownloadCache(this, downloadFile, "redpacket.capsule");
        }
        StringChainData exchangeStatus = getAppStatus(BRConstants.DPOS_VOTE_ID);
        if (null == exchangeStatus ||
                StringUtil.isNullOrEmpty(exchangeStatus.value) ||
                exchangeStatus.value.equals("normal")) {
            copyCapsuleToDownloadCache(this, downloadFile, "vote.capsule");
        }
        StringChainData dposVoteStatus = getAppStatus(BRConstants.EXCHANGE_ID);
        if (null == dposVoteStatus ||
                StringUtil.isNullOrEmpty(dposVoteStatus.value) ||
                dposVoteStatus.value.equals("normal")) {
            copyCapsuleToDownloadCache(this, downloadFile, "swft.capsule");
        }
    }

    private void copyCapsuleToDownloadCache(Context context, String fileOutputPath, String capsuleName) {
        if (StringUtil.isNullOrEmpty(fileOutputPath) || StringUtil.isNullOrEmpty(capsuleName))
            return;
        try {
            File capsuleFile = new File(fileOutputPath, capsuleName);
            if (capsuleFile.exists()) {
                capsuleFile.delete();
            }
            mDoloadFileName = capsuleName;
            InputStream inputStream;
            OutputStream outputStream = new FileOutputStream(capsuleFile);
            inputStream = context.getAssets().open("apps/" + capsuleName);
            byte[] buffer = new byte[1024];
            int length = inputStream.read(buffer);
            while (length > 0) {
                outputStream.write(buffer, 0, length);
                length = inputStream.read(buffer);
            }
            outputStream.flush();
            inputStream.close();
            outputStream.close();

            registerDownloadReceiver();
            Intent intent = new Intent();
            intent.setAction(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            this.sendBroadcast(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private StringChainData getAppsUrl(String miniAppId) {
        String path = mDidStr + "/Apps/" + miniAppId;
        mDid.syncInfo();
        String url = mDid.getInfo(path, false, mSeed);
        if (!StringUtil.isNullOrEmpty(url)) {
            return new Gson().fromJson(url, StringChainData.class);
        }
        return null;
    }

    private StringChainData getAppStatus(String miniAppId) {
        String path = mDidStr + "/Apps/" + BRConstants.ELAPHANT_APP_ID + "/MiniPrograms/" + miniAppId + "/Status";
//        String path = mDidStr + "/Apps/" + miniAppId + "/Status";
//        String path = "iXiqJ7brdiH18vrHiTo9WiAHh692xgXi5y/Apps/8816ed501878a9f4404f5926c4fb2df56239424e41da9c449b4db35e9a8b99d5f976a0537858d709511b5b41cf11c0e88be8778008eb5f918a6aa712ac20c421/MiniPrograms/317DD1D2188C459EB24EAEBC81932F6ADB305FF66F073AB1DC767869EF2B1A04273A8A875";
        mDid.syncInfo();
        String value = mDid.getInfo(path, false, mSeed);
        if (!StringUtil.isNullOrEmpty(value)) {
            return new Gson().fromJson(value, StringChainData.class);
        }
        return null;
    }

    private StringChainData getMiniApps() {
        String path = mDidStr + "/Apps";
        mDid.syncInfo();
        String appIds = mDid.getInfo(path, false, mSeed);
        if (!StringUtil.isNullOrEmpty(appIds)) {
            return new Gson().fromJson(appIds, StringChainData.class);
        }
        return null;
    }

    private void changeView(boolean isEdit) {
        mAddBtn.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        mEditBtn.setVisibility(isEdit ? View.GONE : View.VISIBLE);
        mCancelBtn.setVisibility(isEdit ? View.VISIBLE : View.GONE);
        mDoneBtn.setVisibility(isEdit ? View.VISIBLE : View.GONE);
    }

    private List<MyAppItem> mItems = new ArrayList<>();
    private void initAdapter() {
        mMyAppsRv.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new ExploreAppsAdapter(this, mItems);
        mAdapter.isDelete(false);
        mMyAppsRv.setAdapter(mAdapter);

        ItemTouchHelper.Callback callback = new FragmentExplore.MySimpleItemTouchHelperCallback(mAdapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(mMyAppsRv);
    }

    class KeyValue {
        public String Key;
        public String Value;
    }

    private String getKeyVale(String path, String value) {
        KeyValue key = new KeyValue();
        key.Key = path;
        key.Value = value;
        List<KeyValue> keys = new ArrayList<>();
        keys.add(key);

        return new Gson().toJson(keys, new TypeToken<List<KeyValue>>() {
        }.getType());
    }

    private void upAppStatus(String miniAppId, String status) {
        if (StringUtil.isNullOrEmpty(mDidStr) || StringUtil.isNullOrEmpty(miniAppId)) return;
        String path =  mDidStr + "/Apps/" + BRConstants.ELAPHANT_APP_ID + "/MiniPrograms/" + miniAppId + "/Status";
//        String path = mDidStr + "/Apps/" + miniAppId + "/Status";
        String data = getKeyVale(path, status);
        String info = mDid.signInfo(mSeed, data, false);
        if(!StringUtil.isNullOrEmpty(info)) {
            ProfileDataSource.getInstance(this).upchainSync(info);
        }
    }

    private String getJsonFromCapsule(File filePath) {
        FileInputStream inputStream;
        StringBuilder sb = new StringBuilder();
        try {
            inputStream = new FileInputStream(filePath);
            byte buffer[] = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, len));
            }
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private void decompression(String srcPath, String outPath) {
        try {
            unZipFolder(srcPath, outPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void unZipFolder(String zipFileString, String outPathString) throws Exception {
        boolean isFirstName = true;
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(zipFileString));
        ZipEntry zipEntry;
        String szName = "";
        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();
            if (isFirstName) {
                isFirstName = false;
            }
            if (zipEntry.isDirectory()) {
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(outPathString + File.separator + szName);
                folder.mkdirs();
            } else {
                File file = new File(outPathString + File.separator + szName);
                if (!file.exists()) {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                }
                FileOutputStream out = new FileOutputStream(file);
                int len;
                byte[] buffer = new byte[1024];
                while ((len = inZip.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
                out.close();
            }
        }
        inZip.close();
    }

    private File getZipFile() {
        try {
            File downloadFile = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsoluteFile();
            if (downloadFile.exists()) {
                File[] files = downloadFile.listFiles();
                if (files == null || files.length == 0) return null;
                for (File file : files) {
                    String name = file.getName();
                    if (!StringUtil.isNullOrEmpty(name) && name.contains(".capsule")) {
                        return file;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }



    @Override
    public void onMove(int from, int to) {

    }

    @Override
    public void onDelete(MyAppItem item, int position) {

    }

    @Override
    public void onAbout(MyAppItem item, int position) {

    }

    @Override
    public void onItemClick(MyAppItem item, int position) {

    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {

    }
}
