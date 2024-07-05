package cn.dolphinstar.ctrl.demo;


import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.mydlna.dlna.core.RenderDevice;
import com.mydlna.dlna.service.DlnaDevice;
import com.mydlna.dlna.service.RenderStatus;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import cn.dolphinstar.ctrl.demo.utility.DemoActivityBase;
import cn.dolphinstar.ctrl.demo.utility.DemoConst;
import cn.dolphinstar.ctrl.demo.utility.DeviceListAdapter;
import cn.dolphinstar.lib.IDps.IDpsCtrlPlayer;
import cn.dolphinstar.lib.IDps.IDpsOpenDmcBrowser;
import cn.dolphinstar.lib.IDps.IDpsOpenPushReady;
import cn.dolphinstar.lib.POCO.ReturnMsg;
import cn.dolphinstar.lib.POCO.StartUpCfg;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import cn.dolphinstar.lib.wozkit.NetHelper;
import cn.dolphinstar.lib.wozkit.WozLogger;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class VideoActivity extends DemoActivityBase {

    private IDpsCtrlPlayer dpsCtrlPlayer;

    private Button btnCtrlPlay;
    private Button btnCtrlStop;
    private Button btnCtrlPause;

    private LinearLayout llDeviceLayout;
    private Button btnCastV;
    //设备列表
    private ListView lvDevice;
    private DeviceListAdapter lvAdapter;
    private ArrayList<RenderDevice> renderDeviceList;

    //监听设备状态
    IDpsOpenDmcBrowser dpsOpenDmcBrowser = new IDpsOpenDmcBrowser() {
        @Override
        public void DMCServiceStatusNotify(int status) {
        }

        //状态
        @Override
        public void DlnaDeviceStatusNotify(DlnaDevice device) {
            if (RenderDevice.isRenderDevice(device)) {
                WozLogger.e("DlnaDeviceStatusNotify 事件触发");
                switch (device.stateNow) {
                    // 有新的接收端设备上线 触发
                    case DemoConst.DEVICE_STATE_ONLINE:
                        searchDevices();
                        break;

                    // 有接收端设备离线 触发
                    case DemoConst.DEVICE_STATE_OFFLINE:

                        searchDevices();
                        break;
                    default:
                        //unknown render device state
                        break;
                }
            }
        }

        @Override
        public void DlnaFilesNotify(String udn, int videoCount, int audioCount, int imageCount, int fileCount) {
        }

    };


    private Disposable deviceDisposable = null;

    //监听播放状态
    IDpsOpenPushReady dpsOpenPushReady = new IDpsOpenPushReady() {

        @Override
        public void ready(RenderStatus renderStatus) {
            // 状态为播放的时候 开始主动查询进度条
            if (renderStatus.state == 1 && deviceDisposable == null) {
                //主动查询进度条
                deviceDisposable = MYOUController.of(VideoActivity.this)
                        .getDpsPlayer().Query()
                        .observeOn(AndroidSchedulers.mainThread()) //切主线程
                        .subscribe(s -> {
                            String stateText = "";
                            switch (s.state) {
                                case 0:
                                    stateText = "停止";
                                    deviceDisposable.dispose();
                                    deviceDisposable = null;
                                    break;
                                case 1:
                                    stateText = "播放中...";
                                    break;
                                case 2:
                                    stateText = "暂停";
                                    break;
                                default:
                                    break;
                            }
                            Log.e("video","当前电视状态:" + stateText + "( " + s.state + " )"
                                    .concat("  总时长(秒)：" + s.duration)
                                    .concat("  当前进度(秒):" + s.progress)
                                    .concat("  当前音量:" + s.volume)  );
                        });

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        llDeviceLayout = findViewById(R.id.ll_device_list);


        //投屏按钮
        btnCastV = findViewById(R.id.btn_cast_v);
        btnCastV.setOnClickListener(v -> {
            //点击投屏按钮显示设备列表
            llDeviceLayout.setVisibility(View.VISIBLE);
            //可以主动发起搜索接收端设备
            searchDevices();
        });

        //设备列表
        renderDeviceList = new ArrayList<>();
        lvDevice = findViewById(R.id.lv_device_list);
        lvDevice.requestLayout();
        lvAdapter = new DeviceListAdapter(this, R.layout.device_list_item, renderDeviceList);
        lvDevice.setAdapter(lvAdapter);
        lvDevice.setOnItemClickListener((parent, view, position, id) -> {
            //点击设备列表中的电视，投屏到该电视上
            if (renderDeviceList != null && renderDeviceList.size() > position) {
                RenderDevice device = renderDeviceList.get(position);
                push2Device(device);
                llDeviceLayout.setVisibility(View.GONE);
            }
        });

        //注意 MYOUController是个单实例，设置监听器将覆盖之前设置的。
        MYOUController.of(VideoActivity.this)
                .SetDmcBrowserListener(dpsOpenDmcBrowser)
                .SetPushReady(dpsOpenPushReady);

        dpsCtrlPlayer = MYOUController.of(VideoActivity.this).getDpsPlayer();

        //控制按钮
        btnCtrlPlay = findViewById(R.id.btn_ctrl_play);
        btnCtrlPlay.setOnClickListener(v->{
            dpsCtrlPlayer.Play();
            toast("播放");
        });

        btnCtrlPause = findViewById(R.id.btn_ctrl_pause);
        btnCtrlPause.setOnClickListener(v->{
            dpsCtrlPlayer.Pause();
            toast("暂停");
        });

        btnCtrlStop = findViewById(R.id.btn_ctrl_stop);
        btnCtrlStop.setOnClickListener(v->{
            dpsCtrlPlayer.Stop();
            toast("结束播放");
        });

        //其他可用接口
        //dpsCtrlPlayer.SetSeek(20); //拖动进度条到20秒为止，参数单位秒 0~视频时长
        //dpsCtrlPlayer.SetVolume(10); //设置音量大小10，参数范围 0~100
    }


    //搜索设备 获取当前发现在线的接收端设备列表
    @SuppressLint("CheckResult")
    private void searchDevices() {
        ArrayList<RenderDevice> list = MYOUController.of(VideoActivity.this)
                .getRenderDevice().GetAllOnlineDevices();
        if (list.size() > 0) {
            // Observable主要切主线程 操作UI
            Observable.fromArray(list)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(item -> {
                        renderDeviceList.clear();
                        renderDeviceList.addAll(item);
                        lvAdapter.notifyDataSetChanged();
                        toast("获取设备数量: " + list.size());
                    });
        } else {
            // Observable主要切主线程 操作UI
            Observable.timer(1, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(i -> {
                        renderDeviceList.clear();
                        lvAdapter.notifyDataSetChanged();
                        toast("获取设备数量: " + list.size());
                    });
        }
    }


    //链接投屏实例
    @SuppressLint("CheckResult")
    private void push2Device(RenderDevice device) {

        //投放视频
        ReturnMsg msg = dpsCtrlPlayer
                .PushVideo("https://dolphinstar.cn/fs/video/auth/auth_succes.mp4", "标题", device);

        //.PushVideo("http://192.168.3.133:8888/long.mp4","11",device);
        //投放音频
        //dpsCtrlPlayer.PushAudio("","",device);
        //投放图片
        //dpsCtrlPlayer.PushImage("","",device);

        if (msg.isOk) {
            toast("成功投屏到 -> " + device.nameString);
        } else {
            toast("失败信息 ：" + msg.errMsg);
        }
    }


    @Override
    protected void onDestroy() {
        //关闭服务
        if (deviceDisposable != null) {
            deviceDisposable.dispose();
        }
        super.onDestroy();
    }

}