package cn.dolphinstar.ctrl.demo;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import cn.dolphinstar.ctrl.demo.utility.DemoActivityBase;
import cn.dolphinstar.lib.POCO.StartUpCfg;
import cn.dolphinstar.lib.ctrlCore.MYOUController;
import cn.dolphinstar.lib.wozkit.NetHelper;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class MainActivity extends DemoActivityBase {

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

        //启动海豚星空SDK投屏服务
        dpsSdkStartUp();
    }

    //启动海豚星空SDK投屏服务
    @SuppressLint("CheckResult")
    private void dpsSdkStartUp() {

        NetHelper netHelper = new NetHelper(getApplicationContext());
        int netType = netHelper.getConnectedType();
       if (netType == -1) {
            toast("未连接网络，投屏服务未启动!");
        } else {
        cfg = new StartUpCfg();
        cfg.IsShowLogger = BuildConfig.DEBUG;
        cfg.MediaServerName = "海豚星空DMS-" + (int) (Math.random() * 900 + 100);
        cfg.AppSecret = "xxxxxxx"; //这里填入你的秘钥
        //demo 特殊配置信息 ，非必要。按自己想要的方式给 AppId AppSecret赋值就好
        if(!BuildConfig.dpsAppId.isEmpty()){
            //虽然这里可以配置AppId，
            //但app/src/main/assets/dpsAppInfo文件还是必须存在，可以不配置真的值。
            cfg.AppId = BuildConfig.dpsAppId;
        }
        if(!BuildConfig.dpsAppSecret.isEmpty()){
            cfg.AppSecret = BuildConfig.dpsAppSecret;
        }


        MYOUController.of(MainActivity.this)
                .StartService(cfg)    // 启动服务
                .observeOn(AndroidSchedulers.mainThread()) //操作UI要切主线程
                .subscribe(
                        y -> {
                            toast("Dps SDK启动成功");
                            setTitle(cfg.MediaServerName);
                        },
                        e -> toast(e.getLocalizedMessage()));
        }
    }

    @Override
    protected void onDestroy() {
        //关闭服务
        MYOUController.of(MainActivity.this).Close();
        super.onDestroy();
    }
}
