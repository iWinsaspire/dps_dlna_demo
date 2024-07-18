package cn.dolphinstar.player.demo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mydlna.dlna.core.RenderDevice;
import com.mydlna.dlna.service.RenderStatus;

import java.util.ArrayList;

import cn.dolphinstar.lib.IDps.IDpsOpenPushReady;
import cn.dolphinstar.lib.POCO.ReturnMsg;
import cn.dolphinstar.lib.POCO.StartUpCfg;
import cn.dolphinstar.lib.RxEventBus;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import cn.dolphinstar.lib.wozkit.NetHelper;
import cn.dolphinstar.lib.wozkit.WozLogger;
import cn.dolphinstar.player.demo.other.DramaAdapter;
import cn.dolphinstar.player.demo.other.DramaInfo;
import cn.dolphinstar.player.demo.other.DramaSet;
import cn.dolphinstar.player.demo.other.NetworkUtils;
import cn.dolphinstar.player.demo.other.pUpdateUIState;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class PlayerActivity extends AppCompatActivity {

    DramaSet dramaSet;
    DramaInfo currentDramaInfo;

    ListView listView;
    Button tvBtn;
    Button sdBtn;

    TextView textStatus;
    private StartUpCfg cfg;

    private NetworkChangeReceiver networkChangeReceiver;
    private boolean isNetworkChangeFirst = true;

    Disposable push2DeviceDisposable;
    private void keepPush2DeviceDisposableSafe(){
        if(push2DeviceDisposable!=null && !push2DeviceDisposable.isDisposed()){
            push2DeviceDisposable.dispose();
            push2DeviceDisposable = null;
        }
    }
    private void push2Device(String link, RenderDevice device) {
        push2DeviceDisposable = Observable.fromCallable(() -> {
                    ReturnMsg msg = MYOUController.of(getApplication()).getDpsPlayer()
                            .PushVideo(link, "标题", device);
                    return msg;
                }) .doOnDispose(()-> Log.i("init","createDisposable is dispose"))
                .subscribeOn(Schedulers.io()) // 指定在 IO 线程中执行
                .observeOn(AndroidSchedulers.mainThread()) // 指定在主线程中观察（处理结果）
                .subscribe(result -> {
                    keepPush2DeviceDisposableSafe();
                }, throwable -> {
                    keepPush2DeviceDisposableSafe();
                });
        GlobalData.currentRenderDevice = device;
    }


    public static String formatSeconds(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    //监听播放状态
    IDpsOpenPushReady dpsOpenPushReady = renderStatus -> {
        // 状态为播放的时候 开始主动查询进度条
        if (renderStatus.state == 1) {
            /*
            * 没有定时器去查询进度就创建一个，有了就忽略
            * 监听器是dup协议，不可靠
            * 创建一个定时器，每1秒去查询1次进度 TCP可靠
            *  */
            if( GlobalData.deviceDisposable == null) {

                //主动查询进度条
                GlobalData.deviceDisposable = MYOUController.of(getApplication())
                        .getDpsPlayer().Query()
                        .observeOn(AndroidSchedulers.mainThread()) //切主线程
                        .subscribe(s -> {
                            String stateText = "";
                            switch (s.state) {
                                case 0:
                                    stateText = "停止中";
                                    break;
                                case 1:
                                    stateText = "播放中...";
                                    break;
                                case 2:
                                    stateText = "暂停";
                                    break;
                                case -1:
                                    stateText = "没有播放";
                                    break;
                                default:
                                    break;
                            }
                            Log.e("video", "当前电视状态:" + stateText + "( " + s.state + " )"
                                    .concat("  总时长(秒)：" + s.duration)
                                    .concat("  当前进度(秒):" + s.progress)
                                    .concat("  当前音量:" + s.volume));

                            RxEventBus.of().emit(new pUpdateUIState(
                                    stateText
                                            .concat("\n")
                                            .concat(formatSeconds(s.progress))
                                            .concat("  -  ")
                                            .concat(formatSeconds(s.duration)
                            )));

                        });

            }
        }else {
            WozLogger.json(renderStatus);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_player2);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Intent intent = getIntent();
        String did = intent.getStringExtra("id");
        dramaSet = GlobalData.Dramas.stream().filter((i -> i.Id.equals(did))).findFirst().get();
        currentDramaInfo = dramaSet.Data.stream().findFirst().get();
        networkChangeReceiver = new NetworkChangeReceiver();
        isNetworkChangeFirst = true;

        listView = findViewById(R.id.list_info_View);
        // 创建 ArrayAdapter
        DramaAdapter<DramaSet> adapter = new DramaAdapter(this, R.layout.list_item, dramaSet.Data);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            currentDramaInfo = dramaSet.Data.get(position);
            //确保之前缓存设备有效
            if (checkDevice()) {
                push2Device(currentDramaInfo.ResLink, GlobalData.currentRenderDevice);
            } else {
                //否则进入设备列表选设备
                go2device(currentDramaInfo);
            }

        });
        textStatus = findViewById(R.id.text_status);
        RxEventBus.of().on(pUpdateUIState.class,(s)->{
            textStatus.setText(s.msg);
        });
        sdBtn = findViewById(R.id.btn_sd);
        sdBtn.setOnClickListener(view -> {
            go2device(currentDramaInfo);
        });

        tvBtn = findViewById(R.id.btn_tv);
        tvBtn.setOnClickListener(view -> {
            dpsSdkStartUp();
        });

        MYOUController.of(getApplication())
                .SetPushReady(dpsOpenPushReady);//注册状态查询覆盖作用
    }

    private boolean checkDevice() {
        if (GlobalData.currentRenderDevice == null)
            return false;

        ArrayList<RenderDevice> list = MYOUController.of(getApplication())
                .getRenderDevice().GetAllOnlineDevices();

        int length = list.stream().filter(f -> f.udnString.equals(GlobalData.currentRenderDevice.udnString)).toArray().length;
        return length > 0;
    }

    private void go2device(DramaInfo info) {
        if (MYOUController.of(getApplication()).IsStartUp()) {
            Intent intent = new Intent(PlayerActivity.this, DeviceActivity.class);
            intent.putExtra("link", info.ResLink);
            startActivity(intent);
        } else {
            toast("先点击TV按钮启动投屏服务");
        }
    }

    //启动海豚星空SDK投屏服务
    @SuppressLint("CheckResult")
    private void dpsSdkStartUp() {

        //如果是wifi 启动
        if (NetworkUtils.isWifiConnected(getApplication())) {
            cfg = new StartUpCfg();
            cfg.IsShowLogger = BuildConfig.DEBUG;
            cfg.MediaServerName = "海豚星空DMS-" + (int) (Math.random() * 900 + 100);
            cfg.AppSecret = ""; //这里填入你的秘钥
            cfg.AppId = ""; //这里填入你的秘钥
            cfg.IsEnableLocaldms = false;

            //demo 特殊配置信息 ，非必要。按自己想要的方式给 AppId AppSecret赋值就好
            if (!BuildConfig.dpsAppId.isEmpty() && !BuildConfig.dpsAppSecret.isEmpty() ) {
                //虽然这里可以配置AppId，
                //但app/src/main/assets/dpsAppInfo文件还是必须存在，可以不配置真的值。
                cfg.AppId = BuildConfig.dpsAppId;

                cfg.AppSecret = BuildConfig.dpsAppSecret;
            } 

            if (!MYOUController.of(getApplication()).IsStartUp()) {

                MYOUController.of(getApplication())
                        .StartService(cfg)    // 启动服务
                        .observeOn(AndroidSchedulers.mainThread()) //操作UI要切主线程
                        .subscribe(
                                y -> {
                                    toast("Dps SDK启动成功");
                                    setTitle(cfg.MediaServerName);
                                    tvBtn.setVisibility(View.GONE);
                                    sdBtn.setVisibility(View.VISIBLE);
                                    go2device(currentDramaInfo);
                                },
                                e -> toast(e.getLocalizedMessage()));

            }else {
                tvBtn.setVisibility(View.GONE);
                sdBtn.setVisibility(View.VISIBLE);
                go2device(currentDramaInfo);
            }
        }
    }

    public void toast(String msg) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show();
        Log.i("Toast", msg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册网络变化广播接收器
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkChangeReceiver, intentFilter);
    }

    @Override
    protected void onDestroy() {
        WozLogger.e("PlayerActivi被销毁了");
        RxEventBus.of().un(pUpdateUIState.class);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 取消注册网络变化广播接收器
        unregisterReceiver(networkChangeReceiver);
    }


    public class NetworkChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action) && !isNetworkChangeFirst
            ) {
                ConnectivityManager connectivityManager = (ConnectivityManager)
                        context.getSystemService(Context.CONNECTIVITY_SERVICE);

                if (connectivityManager != null) {
                    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

                    if (activeNetworkInfo != null && activeNetworkInfo.isConnected()) {
                        if (!MYOUController.of(PlayerActivity.this).IsStartUp()) {
                        toast("网络连接，再次启动投屏: " + activeNetworkInfo.getTypeName());
                            tvBtn.setVisibility(View.VISIBLE);
                        }
                    } else {

                        if (MYOUController.of(getApplication()).IsStartUp()) {
                            MYOUController.of(getApplication()).Close();
                            toast("网络断开连接,并关闭投屏服务");
                            sdBtn.setVisibility(View.GONE);
                        } else {
                            toast("网络断开连接");
                        }
                    }
                }
            }else {
                isNetworkChangeFirst = false;
            }

        }
    }
}