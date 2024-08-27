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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mydlna.dlna.core.RenderDevice;
import com.mydlna.dlna.service.RenderStatus;

import java.util.ArrayList;

import cn.dolphinstar.lib.IDps.IDpsOpenPushReady;
import cn.dolphinstar.lib.POCO.ReturnMsg;
import cn.dolphinstar.lib.POCO.StartUpAuthType;
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

    private ActivityResultLauncher<Intent> activityResultLauncher;


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

    //推送链接到接收端设备上播放
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

        startTime = System.currentTimeMillis();
        endTime = 0;
        /*
        主动查询状态
        如果要求UI响应更新，
        尽快获取播放状态，
        可以立即主动查询状态
         */
        queryState();
    }

    // 记录开始时间
    long startTime = 0;
    // 记录结束时间
    long endTime =0;

    //事件格式化显示
    public static String formatSeconds(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    //状态转化
    public  String transformationState(int state){
        String stateText = "";
        switch (state) {
            case 0:
                stateText = "停止";
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
        return  stateText;
    }

    /*
    * 主动轮询电视状态
    * */
    public  void queryState (){
        /*
         * 没有定时器去查询进度就创建一个，有了就忽略
         * 监听器是dup协议，不可靠
         * 创建一个定时器，每1秒去查询1次进度 TCP可靠 保证进度条有序 稳定
         *  */
        if( GlobalData.deviceDisposable == null) {

            //主动查询进度条
            GlobalData.deviceDisposable = MYOUController.of(getApplication())
                    .getDpsPlayer().Query()
                    .observeOn(AndroidSchedulers.mainThread()) //切主线程
                    .subscribe(s -> {
                        if(s.state == 1 && endTime == 0){
                            endTime = System.currentTimeMillis();
                            // 计算执行时间
                            long executionTime = endTime - startTime;
                            WozLogger.e("投放链接到准备就绪: " + executionTime + " 毫秒");
                        }
                        String stateText = transformationState(s.state);
                        Log.e("query主动查询", "当前电视状态:" + stateText + "( " + s.state + " )"
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
    }
    /*
    * 监听播放状态
    * udp 时效性差
    * */
    IDpsOpenPushReady dpsOpenPushReady = renderStatus -> {
        String stateText = transformationState(renderStatus.state);
        Log.w("被动接受电视状态", "当前电视状态:" + stateText + "( " + renderStatus.state + " )");
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

        //获取播放剧集参数
        Intent intent = getIntent();
        String did = intent.getStringExtra("id");
        dramaSet = GlobalData.Dramas.stream().filter((i -> i.Id.equals(did))).findFirst().get();
        currentDramaInfo = dramaSet.Data.stream().findFirst().get();

        //监听网络变换
        networkChangeReceiver = new NetworkChangeReceiver();
        isNetworkChangeFirst = true;

        //显示集数
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

        //更新播放器状态
        textStatus = findViewById(R.id.text_status);
        RxEventBus.of().on(pUpdateUIState.class,(s)->{
            textStatus.setText(s.msg);
        });

        //选择设备按钮
        sdBtn = findViewById(R.id.btn_sd);
        sdBtn.setOnClickListener(view -> {
            //去设备列表搜索和选择投放设备
            go2device(currentDramaInfo);
        });

        //tv按钮
        tvBtn = findViewById(R.id.btn_tv);
        tvBtn.setOnClickListener(view -> {
            //启动sdk
            dpsSdkStartUp();
        });

        //注册状态查询被动查询，反复注册可覆盖
        MYOUController.of(getApplication()) .SetPushReady(dpsOpenPushReady);

        //控制按钮
        findViewById(R.id.ctl_play).setOnClickListener(view -> {
            Log.i("控制按键","播放");
            MYOUController.of(getApplication()).getDpsPlayer().Play();
        });
        findViewById(R.id.ctl_pause).setOnClickListener(view -> {
            Log.i("控制按键","暂停");
            MYOUController.of(getApplication()).getDpsPlayer().Pause();
        });

        findViewById(R.id.ctl_stop).setOnClickListener(view -> {
            Log.i("控制按键","结束");
            MYOUController.of(getApplication()).getDpsPlayer().Stop();
        });

        /*
        注册一个deviceActivity 返回
        当在device列表点击推送，返回通知启动查询进度
         */
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result ->{
                    if(result.getResultCode() == GlobalData.SELECT_DEVICE_GO_BACK){
                        Intent data =result.getData();
                        if(data!=null){
                            boolean isStartQuery = data.getBooleanExtra("start_query",false);
                            if(isStartQuery){
                                startTime = System.currentTimeMillis();
                                endTime = 0;
                                queryState();
                            }
                        }
                    }
                }
        );
    }

    //检查缓存设备信息是否还有效。
    private boolean checkDevice() {
        if (GlobalData.currentRenderDevice == null) {
            return false;
        }

        //获取当前在线接收端设备
        ArrayList<RenderDevice> list = MYOUController.of(getApplication())
                .getRenderDevice().GetAllOnlineDevices();

        int length = list.stream().filter(f -> f.udnString.equals(GlobalData.currentRenderDevice.udnString)).toArray().length;
        return length > 0;
    }


    //前往设备列表页面
    private void go2device(DramaInfo info) {
        if (MYOUController.of(getApplication()).IsStartUp()) {
            Intent intent = new Intent(PlayerActivity.this, DeviceActivity.class);
            intent.putExtra("link", info.ResLink);
            activityResultLauncher.launch(intent);
        } else {
            toast("先点击TV按钮启动投屏服务");
        }
    }

    //启动海豚星空SDK投屏服务
    @SuppressLint("CheckResult")
    private void dpsSdkStartUp() {

        //如果是 wifi 启动，投屏一般在局域网内使用，建议确定是wifi在启动
        if (NetworkUtils.isWifiConnected(getApplication())) {
            cfg = new StartUpCfg();
            cfg.IsShowLogger = BuildConfig.DEBUG;
            cfg.MediaServerName = "海豚星空DMS-" + (int) (Math.random() * 900 + 100);
            cfg.AppSecret = ""; //这里填入你的秘钥
            cfg.AppId = ""; //这里填入你的秘钥
             /*
            使用android id 作为设备标识,不方便使用android id 可以考虑使用随机数 AUTH_BY_RANDOM_ID。
            参考阅读 https://dolphinstar.cn/doc/#other/deviceid
            手机发送端仅推荐使用 AUTH_BY_ANDROID_ID 或 AUTH_BY_RANDOM_ID。
            */
            cfg.AuthType = StartUpAuthType.AUTH_BY_ANDROID_ID;

            //demo 特殊配置信息 ，非必要。按自己想要的方式给 AppId AppSecret赋值就好
            if (!BuildConfig.dpsAppId.isEmpty() && !BuildConfig.dpsAppSecret.isEmpty() ) {
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