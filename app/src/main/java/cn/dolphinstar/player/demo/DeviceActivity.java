package cn.dolphinstar.player.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mydlna.dlna.core.RenderDevice;
import com.mydlna.dlna.service.DlnaDevice;
import com.mydlna.dlna.service.RenderStatus;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import cn.dolphinstar.lib.IDps.IDpsCtrlPlayer;
import cn.dolphinstar.lib.IDps.IDpsOpenDmcBrowser;
import cn.dolphinstar.lib.IDps.IDpsOpenPushReady;
import cn.dolphinstar.lib.POCO.ReturnMsg;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import cn.dolphinstar.lib.wozkit.WozLogger;
import cn.dolphinstar.player.demo.other.DeviceAdapter;
import cn.dolphinstar.player.demo.other.DramaAdapter;
import cn.dolphinstar.player.demo.other.DramaSet;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class DeviceActivity extends AppCompatActivity {

    private ListView lvDevice;
    private DeviceAdapter lvAdapter;
    private ArrayList<RenderDevice> renderDeviceList;

    private IDpsCtrlPlayer dpsCtrlPlayer;

    String link;

    TextView tvDeviceState;
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
                    // 有新的接收端设备上线 触发  DEVICE_STATE_ONLINE = 1;
                    case 1:
                        searchDevices();
                        break;

                    // 有接收端设备离线 触发  DEVICE_STATE_OFFLINE = 0;
                    case 0:

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


    Disposable searchDisposable;
    private void keepSearchDisposableSafe(){
        if(searchDisposable!=null && !searchDisposable.isDisposed()){
            searchDisposable.dispose();
            searchDisposable = null;
        }
    }
    //搜索设备 获取当前发现在线的接收端设备列表
    @SuppressLint("CheckResult")
    private void searchDevices() {

        searchDisposable = Observable.fromCallable(() -> {
                    ArrayList<RenderDevice> list = MYOUController.of(DeviceActivity.this)
                            .getRenderDevice().GetAllOnlineDevices();
                    return list;
                }) .doOnDispose(()-> Log.i("init","searchDisposable is dispose"))
                .subscribeOn(Schedulers.io()) // 指定在 IO 线程中执行
                .observeOn(AndroidSchedulers.mainThread()) // 指定在主线程中观察（处理结果）
                .subscribe(result -> {
                    if (result.size() > 0) {
                        // Observable主要切主线程 操作UI
                        renderDeviceList.clear();
                        renderDeviceList.addAll(result);
                        lvAdapter.notifyDataSetChanged();
                        tvDeviceState.setText("");
                    } else {
                        renderDeviceList.clear();
                        lvAdapter.notifyDataSetChanged();
                        tvDeviceState.setText("");
                    }
                    keepSearchDisposableSafe();
                }, throwable -> {
                    keepSearchDisposableSafe();
                });



    }


    Disposable push2DeviceDisposable;
    private void keepPush2DeviceDisposableSafe(){
        if(push2DeviceDisposable!=null && !push2DeviceDisposable.isDisposed()){
            push2DeviceDisposable.dispose();
            push2DeviceDisposable = null;
        }
    }

    private void push2Device(RenderDevice device) {
        push2DeviceDisposable = Observable.fromCallable(() -> {
                    ReturnMsg msg = dpsCtrlPlayer
                            .PushVideo(link, "标题", device);
                    return msg;
                })
                .subscribeOn(Schedulers.io()) // 指定在 IO 线程中执行
                .observeOn(AndroidSchedulers.mainThread()) // 指定在主线程中观察（处理结果）
                .subscribe(result -> {
                    keepPush2DeviceDisposableSafe();
                    GlobalData.currentRenderDevice = device;
                    Intent intent = new Intent();
                    intent.putExtra("start_query",true);
                    setResult(GlobalData.SELECT_DEVICE_GO_BACK,intent);
                    finish();
                }, throwable -> {
                    keepPush2DeviceDisposableSafe();
                });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_device);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //注意 MYOUController是个单实例，设置监听器将覆盖之前设置的。
        MYOUController.of(DeviceActivity.this)
                .SetDmcBrowserListener(dpsOpenDmcBrowser);
        dpsCtrlPlayer = MYOUController.of(DeviceActivity.this).getDpsPlayer();

        Intent intent = getIntent();
        link = intent.getStringExtra("link");

        //设备列表
        renderDeviceList = new ArrayList<>();
        lvDevice = findViewById(R.id.list_deives_View);
        lvDevice.requestLayout();
        lvAdapter = new DeviceAdapter(this, R.layout.list_item, renderDeviceList);
        lvDevice.setAdapter(lvAdapter);

        lvDevice.setOnItemClickListener((parent, view, position, id) -> {
            //点击设备列表中的电视，投屏到该电视上
            if (renderDeviceList != null && renderDeviceList.size() > position) {
                RenderDevice device = renderDeviceList.get(position);
                push2Device(device);
            }
        });

        tvDeviceState = findViewById(R.id.device_state);

        findViewById(R.id.btn_refresh).setOnClickListener(view -> {
            tvDeviceState.setText("开始搜索");
            searchDevices();
        });

        tvDeviceState.setText("开始搜索");
        //前面已经启动了，这里主动搜索一下也可以
        Observable.timer(300, TimeUnit.MILLISECONDS)
                .subscribe(aLong -> {
                    searchDevices();
                });

    }
}