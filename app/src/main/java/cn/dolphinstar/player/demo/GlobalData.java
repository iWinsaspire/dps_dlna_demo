package cn.dolphinstar.player.demo;

import com.mydlna.dlna.core.RenderDevice;

import java.util.ArrayList;
import java.util.List;

import cn.dolphinstar.player.demo.other.DramaInfo;
import cn.dolphinstar.player.demo.other.DramaSet;
import io.reactivex.disposables.Disposable;

//全局静态数据
public class GlobalData {

    public  static  void iniData(){
        if(Dramas == null){
            Dramas = new ArrayList<>();
            for (int i = 0; i < 55; i++) {
                List<DramaInfo> infos =new ArrayList<>();
                for (int j = 0; j < 20; j++) {
                    infos.add(new DramaInfo("http://192.168.3.133:8888/long.mp4", j+""));
                }
                Dramas.add(new DramaSet("剧名"+i , infos));
            }
        }
    }

    //剧集
    public  static List<DramaSet> Dramas  ;

    //当前使用的播放器
    public static  RenderDevice currentRenderDevice;

    public static Disposable deviceDisposable = null;

    public static void keepDeviceDisposableSafe() {
        if (deviceDisposable != null ) {
            deviceDisposable.dispose();
            deviceDisposable = null;
        }
    }
}