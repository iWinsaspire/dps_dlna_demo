# dps://dlna_demo

海豚星空投屏 DLNA 投屏示例

#### (0) 注意 gradle\wrapper\gradle-wrapper.properties
demo采用本地目录使用 gradle-8.6-all, 根据自己的的情况修改
```bash 
#distributionUrl=https\://services.gradle.org/distributions/gradle-8.6-bin.zip
# gradle 下载 https://services.gradle.org/distributions/
distributionUrl=file:///D:/Android/gradle-8.6-all.zip
```

## （1）跟目录下的settings.gradle添加私有mevan仓库
```groovy
 maven {
            allowInsecureProtocol true  //比较高的 gradle 要允许 http
            url 'http://nexus.dolphinstar.cn/repo/openmavenx'
        }
```


## （2）app/build.gradle 文件

### 2.1 添加依赖
```groovy
//海豚星空投屏核心库 建议使用后台显示的最新本
implementation 'cn.dolphinstar:ctrlCore:x.x.x'
```

### 2.2 其他配置

```groovy
compileOptions {
    sourceCompatibility JavaVersion.VERSION_1_8
    targetCompatibility JavaVersion.VERSION_1_8
}
```
```groovy
//另外注意sdk 版本小于 5.1.1编译SDK版本不要大于28
compileSdkVersion 28
defaultConfig { 
    //另外注意sdk 版本小于 5.1.1目标SDK版本不要大于28
    targetSdkVersion 28
}

//sdk 版本从 5.1.1 开始，可以大于30，建议升级最新版本SDK
```

## (3) APP权限
```xml
<!-- 网络访问全系 必须权限-->
<uses-permission android:name="android.permission.INTERNET" /> 
```

## (4) 网络
注意 android 9后强制https，为了支持http。应在AndroidManifest.xml的Application节点添加
```groovy
android:networkSecurityConfig="@xml/network_security_config"
```
app\src\main\res\xml 中添加文件 network_security_config.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

## (5) 申请海豚星空投屏SDK APPID

前往 海豚星空平台 [控制中心-dps://sdk](https://client.dolphinstar.cn/) 创建应用获取appId，appSecret。

## (6) SDK 接口

### 启动服务
```java

//启动海豚星空SDK投屏服务 确保设备连接外网情况下启动
@SuppressLint("CheckResult")
private void dpsSdkStartUp() {
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
    
    MYOUController.of(MainActivity.this)
        .StartService(cfg)    // 启动服务
        .observeOn(AndroidSchedulers.mainThread()) //操作UI要切主线程
        .subscribe(
                y -> {
                    toast("Dps SDK启动成功");
                    },
            e -> toast(e.getLocalizedMessage()));
}


//注意APP关闭时 要关闭服务
protected void onDestroy() {
    //APP关闭要关闭服务
    MYOUController.of(MainActivity.this).Close();
    super.onDestroy();
}

```

### 投屏接口
```java
public class VideoActivity extends DemoActivityBase {
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

    //记得释放
    private Disposable deviceDisposable = null;

    //监听播放状态
    IDpsOpenPushReady dpsOpenPushReady = new IDpsOpenPushReady() {

        @Override
        public void ready(RenderStatus renderStatus) {
            // 状态为播放的时候 开始主动查询进度条
            if (renderStatus.state == 1 && deviceDisposable == null) {
                //主动查询进度条
                deviceDisposable = MYOUController.of(VideoActivity.this)
                        .getDpsPlayer().Query().subscribe(s -> {
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
                            //播放进度
                            WozLogger.w("当前电视状态:" + stateText + "( " + s.state + " )"
                                    .concat("  总时长(秒)：" + s.duration)
                                    .concat("  当前进度(秒):" + s.progress)
                                    .concat("  当前音量:" + s.volume)
                            );
                        });

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);
        //注意 MYOUController是个单实例，设置监听器将覆盖之前设置的。
        MYOUController.of(VideoActivity.this)
                .SetDmcBrowserListener(dpsOpenDmcBrowser) //监听设备
                .SetPushReady(dpsOpenPushReady); //投放链接时，电视端准备就绪，可以开始读播放进度


        MYOUController.of(VideoActivity.this).getDpsPlayer();
    }
    
    //搜索设备 获取当前发现在线的接收端设备列表 
    private void searchDevices() {
        //获取设备列表
        ArrayList<RenderDevice> list = MYOUController.of(VideoActivity.this)
                .getRenderDevice().GetAllOnlineDevices(); 
    }
 
    
    @Override
    protected void onDestroy() {
        //关闭服务
        if (deviceDisposable != null) {
            deviceDisposable.dispose();
        }
        super.onDestroy();
    }
    /*
     * 获取控制电视接口
     * IDpsCtrlPlayer dpsCtrlPlayer = MYOUController.of(VideoActivity.this).getDpsPlayer();
     * 
     * 投放视频
     * dpsCtrlPlayer.PushVideo("https://dolphinstar.cn/fs/video/auth/auth_succes.mp4", "标题", device);
     * 投放音频
     * dpsCtrlPlayer.PushAudio("","",device);
     * 投放图片
     * dpsCtrlPlayer.PushImage("","",device);
     * 播放
     * dpsCtrlPlayer.Play();
     * 暂停
     * dpsCtrlPlayer.Pause();
     * 结束
     * dpsCtrlPlayer.Stop();
     * 拖动进度条到20秒为止，参数单位秒 0~视频时长
     * dpsCtrlPlayer.SetSeek(20);
     * 设置音量大小10，参数范围 0~100
     * dpsCtrlPlayer.SetVolume(10);
     * */
}
```

## 注意：
使用前，先改动 cn.dolphinstar.player.demo.GlobalData.videoLink 为自己的视频链接


### Demo下载
[github下载: https://github.com/iWinsaspire/dps_dlna_demo](https://github.com/iWinsaspire/dps_dlna_demo)

[dps_dlna_demo.zip 下载](https://dolphinstar.cn/fs/demo/dps_dlna_demo.zip)

[百度网盘下载 dps_dlna_demo.zip](https://pan.baidu.com/s/1QAIQtLu394F-xc6BYTty8g?pwd=idps)