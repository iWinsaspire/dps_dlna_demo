package cn.dolphinstar.ctrl.demo;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import com.mydlna.dlna.core.ContentDevice;
import com.mydlna.dlna.core.DmcClientWraper;
import com.mydlna.dlna.core.RenderDevice;
import com.mydlna.dlna.service.DlnaDevice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.dolphinstar.ctrl.demo.utility.DemoActivityBase;
import cn.dolphinstar.ctrl.demo.utility.DemoConst;
import cn.dolphinstar.lib.IDps.IDpsOpenDmcBrowser;
import cn.dolphinstar.lib.POCO.StartUpCfg;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class MainActivity extends DemoActivityBase {

    private static final int REQUEST_PERMISSION_CODE = 100;

    private StartUpCfg cfg;
    private Button btnLink;

    //监听设备状态
    IDpsOpenDmcBrowser dpsOpenDmcBrowser = new IDpsOpenDmcBrowser() {
        @Override
        public void DMCServiceStatusNotify(int status) {
        }

        //状态
        @Override
        public void DlnaDeviceStatusNotify(DlnaDevice device) {
            if (RenderDevice.isRenderDevice(device)) {
                switch (device.stateNow) {
                    case DemoConst.DEVICE_STATE_ONLINE:
                        // 有新的接收端设备上线
                        searchDevices();
                        break;
                    case DemoConst.DEVICE_STATE_OFFLINE:
                        // 有接收端设备离线
                        searchDevices();
                        break;
                    default:
                        //unknown render device state
                        break;
                }
            }
        }

        //DMS媒体文件变更通知 照成无需改动
        @Override
        public void DlnaFilesNotify(String udn, int videoCount, int audioCount, int imageCount, int fileCount) {
            if (TextUtils.isEmpty(udn)) {
                return;
            }
            final ContentDevice device = ContentDevice.sDevices.findDeviceByUdn(udn);
            if (device != null) {
                final int fAudioCount = audioCount;
                final int fVideoCount = videoCount;
                final int fImageCount = imageCount;
                final int fFileCount = fileCount;

                new Runnable() {
                    public void run() {
                        device.updateContent(DmcClientWraper.sClient, fAudioCount,
                                fImageCount, fVideoCount, fFileCount);
                    }
                };
            }
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        //视频投屏示例
        btnLink = findViewById(R.id.btn_link);
        btnLink.setOnClickListener(v->{
            Intent intent = new Intent(MainActivity.this,VideoActivity.class);
            startActivity(intent);
        });

        //版本小于 m 直接启动海豚星空投屏服务 否则检测权限后启动
        if (Build.VERSION.SDK_INT >Build.VERSION_CODES.M) {
            checkAndRequestPermission();
        }else{
            dpsSdkStartUp();
        }
    }

    //SDK启动海豚星空投屏服务
    @SuppressLint("CheckResult")
    private void dpsSdkStartUp() {
        cfg = new StartUpCfg();
        cfg.MediaServerName = "海豚星空DMS-" + (int) (Math.random() * 900 + 100);
        cfg.IsShowLogger = BuildConfig.DEBUG;
        cfg.AppSecret = "29f89b23775045a9";

        MYOUController.of(MainActivity.this)
                .SetDmcBrowserListener(dpsOpenDmcBrowser)
                // 启动服务
                .StartService(cfg)
                .subscribe(
                        y -> {
                            //只是为了切主线程 操作UI
                            Observable.timer(300, TimeUnit.MILLISECONDS)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(l -> {
                                        toast("Dps SDK启动成功");
                                        setTitle(cfg.MediaServerName);
                                    });
                        },
                        e -> toast(e.getLocalizedMessage()));
    }

    //搜索设备 获取当前发现在线的接收端设备列表
    @SuppressLint("CheckResult")
    private void searchDevices() {
        ArrayList<RenderDevice> list = MYOUController.of(MainActivity.this).getRenderDevice().GetAllOnlineDevices();
        if (list.size() > 0) {
            Observable.fromArray(list)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(item -> {
                        btnLink.setEnabled(true);
                    });
        } else {
            //没啥用 主要切主线程 操作UI
            Observable.timer(1, TimeUnit.MILLISECONDS)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(i -> {
                        btnLink.setEnabled(false);
                    });
        }
    }


    //region 动态权限申请

    private void checkAndRequestPermission() {

        List<String> lackedPermission = new ArrayList<>();

        if (!(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (lackedPermission.size() == 0) {
            onPermissionsOk();
        } else {
            String[] requestPermissions = new String[lackedPermission.size()];
            lackedPermission.toArray(requestPermissions);
            requestPermissions(requestPermissions, REQUEST_PERMISSION_CODE);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            onPermissionsOk();
        }
    }

    private  void onPermissionsOk(){
        dpsSdkStartUp();
    }
    //endregion



    @Override
    protected void onDestroy() {
        //关闭服务
        MYOUController.of(MainActivity.this).Close();

        super.onDestroy();
    }
}
