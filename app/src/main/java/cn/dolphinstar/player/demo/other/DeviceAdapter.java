package cn.dolphinstar.player.demo.other;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.mydlna.dlna.core.RenderDevice;

import java.util.List;

import cn.dolphinstar.player.demo.R;

public class DeviceAdapter extends ArrayAdapter {
    private final int resourceId ;
    public DeviceAdapter(Context context, int resource, List<RenderDevice> obj) {
        super(context, resource,obj);
        resourceId = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RenderDevice device = (RenderDevice)getItem(position);

        View view = LayoutInflater.from(getContext()).inflate(resourceId,null);
        TextView tv = view.findViewById(R.id.list_item_text);
        tv.setText(" >> " + device.nameString );

        return  view;
    }
}
