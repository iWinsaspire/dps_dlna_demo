package cn.dolphinstar.ctrl.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import cn.dolphinstar.ctrl.demo.utility.DemoActivityBase;
import cn.dolphinstar.lib.POCO.StartUpCfg;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class MainActivity extends DemoActivityBase {

    private static final int REQUEST_PERMISSION_CODE = 100;

    private StartUpCfg cfg;
    private Button btnLink;


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
        cfg.AppSecret = "xxxxxxx"; //这里填入你的秘钥


        //demo 特殊配置信息 ，非必要。按自己想要的方式给 AppId AppSecret赋值就好
        if(!BuildConfig.dpsAppId.isEmpty()){
            //虽然这里可以配置AppId，但app/src/main/assets/dpsAppInfo文件还是必须存在，可以不配置真的值。
            cfg.AppId = BuildConfig.dpsAppId;
        }
        if(!BuildConfig.dpsAppSecret.isEmpty()){
            cfg.AppSecret = BuildConfig.dpsAppSecret;
        }


        MYOUController.of(MainActivity.this)
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

    //region 动态权限申请

    private void checkAndRequestPermission() {

        List<String> lackedPermission = new ArrayList<>();

      /*  if (!(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        if (!(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)) {
            lackedPermission.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }*/

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
