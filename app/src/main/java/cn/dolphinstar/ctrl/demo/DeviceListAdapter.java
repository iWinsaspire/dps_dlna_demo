package cn.dolphinstar.ctrl.demo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.mydlna.dlna.core.RenderDevice;

import java.util.List;

public class DeviceListAdapter extends ArrayAdapter {
    private final int resourceId ;
    public DeviceListAdapter(Context context, int resource, List<RenderDevice> obj) {
        super(context, resource,obj);
        resourceId = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RenderDevice device = (RenderDevice)getItem(position);

        View view = LayoutInflater.from(getContext()).inflate(resourceId,null);
        TextView tv = view.findViewById(R.id.dv_list_item_text);
        tv.setText(" >> " + device.nameString );

        return  view;
    }
}
