package com.miner.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


import com.miner.R;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;


/**
 * Created by fanshengyue on 2017/11/21.
 */

public class TimeListAdapter extends BaseAdapter {
    private Context mContext;
    private List<Integer> data;

    public TimeListAdapter(Context mContext, List<Integer> data) {
        this.mContext = mContext;
        this.data = data;
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int i) {
        return data.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder holder = null;
        if (view == null) {
            view = LayoutInflater.from(mContext).inflate(R.layout.item_timelist, null);
            holder = new ViewHolder();
            holder.tvTime = view.findViewById(R.id.tv_time);
            view.setTag(holder);
        } else {
            holder = (ViewHolder) view.getTag();
        }
        holder.tvTime.setText(data.get(i) + "s");

        return view;
    }


    static class ViewHolder {
        @BindView(R.id.tv_time)
        TextView tvTime;

        ViewHolder(View view) {
            ButterKnife.bind(this, view);
        }

        public ViewHolder() {

        }
    }
}
