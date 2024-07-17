package cn.dolphinstar.player.demo;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.mydlna.dlna.core.RenderDevice;

import cn.dolphinstar.lib.ctrlCore.MYOUController;
import cn.dolphinstar.lib.wozkit.WozLogger;
import cn.dolphinstar.player.demo.other.DramaAdapter;
import cn.dolphinstar.player.demo.other.DramaBase;
import cn.dolphinstar.player.demo.other.DramaSet;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity2 extends AppCompatActivity {


    ListView listView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        init();
    }

    Disposable createDisposable;
    private void keepCreateDisposableSafe(){
        if(createDisposable!=null && !createDisposable.isDisposed()){
            createDisposable.dispose();
            createDisposable = null;
        }
    }
    public void init(){
        MYOUController.of(MainActivity2.this);

         listView = findViewById(R.id.listView);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            go2player(GlobalData.Dramas.get(position));
        });

        createDisposable = Observable.fromCallable(() -> {
                    //初始化数据
                    GlobalData.iniData();
                    return GlobalData.Dramas;
                }) .doOnDispose(()-> Log.i("init","createDisposable is dispose"))
                .subscribeOn(Schedulers.io()) // 指定在 IO 线程中执行
                .observeOn(AndroidSchedulers.mainThread()) // 指定在主线程中观察（处理结果）
                .subscribe(result -> {
                    // 创建 ArrayAdapter
                    DramaAdapter<DramaSet> adapter = new DramaAdapter(this, R.layout.list_item, result );
                    listView.setAdapter(adapter);
                    keepCreateDisposableSafe();
                }, throwable -> {
                    keepCreateDisposableSafe();
                });
    }

    private  void go2player(DramaSet dramaSet){
        Intent intent = new Intent(MainActivity2.this,PlayerActivity.class);
        intent.putExtra("id",dramaSet.Id);
        startActivity(intent);
    }
}