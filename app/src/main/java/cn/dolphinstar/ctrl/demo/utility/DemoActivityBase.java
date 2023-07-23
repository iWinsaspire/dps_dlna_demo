package cn.dolphinstar.ctrl.demo.utility;

import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import cn.dolphinstar.ctrl.demo.MainActivity;

public class DemoActivityBase extends AppCompatActivity {

    public void toast(String msg) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show();
        Log.i("Toast", msg);
    }
}
