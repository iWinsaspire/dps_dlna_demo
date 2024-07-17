package cn.dolphinstar.player.demo.other;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import cn.dolphinstar.player.demo.R;

public class DramaAdapter<T> extends ArrayAdapter {
    private final int resourceId ;
    public DramaAdapter(Context context, int resource, List<T> obj) {
        super(context, resource,obj);
        resourceId = resource;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        DramaBase data = (DramaBase)getItem(position);

        View view = LayoutInflater.from(getContext()).inflate(resourceId,null);
        TextView tv = view.findViewById(R.id.list_item_text);
        tv.setText(" >> " + data.Title );

        return  view;
    }
}
