/*
 * Copyright 2019 Michael Moessner
 *
 * This file is part of Metronome.
 *
 * Metronome is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Metronome is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Metronome.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.moekadu.metronome;

import android.content.Context;
import android.content.SharedPreferences;
// import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class SavedItemDatabase extends RecyclerView.Adapter<SavedItemDatabase.ViewHolder> {

    static class SavedItem {

        SavedItem() { }

        String title;
        String date;
        String time;
        float bpm;
        String playList;
    }

    private ArrayList<SavedItem> dataBase = null;

    private OnItemClickedListener onItemClickedListener = null;

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View view;
        ViewHolder(View view) {
            super(view);
            this.view = view;
        }
    }

    interface OnItemClickedListener {
        void onItemClicked(SavedItem item, int position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.saved_item, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {

        SavedItem item = dataBase.get(position);

        TextView titleView = holder.view.findViewById(R.id.saved_item_title);
        TextView dateView = holder.view.findViewById(R.id.saved_item_date);
        TextView speedView = holder.view.findViewById(R.id.saved_item_speed);

//        titleView.setText("Some title " + dataBase.get(position));
        titleView.setText(item.title);
        dateView.setText(item.date + "\n" + item.time);
//        dateView.setText("23.03.2019\n23:43");
        speedView.setText(holder.view.getContext().getString(R.string.bpm, Utilities.getBpmString(item.bpm)));

        IconListVisualizer iconListVisualizer = holder.view.findViewById(R.id.saved_item_sounds);
        AudioMixer.PlayListItem[] metaData = SoundProperties.Companion.parseMetaDataString(item.playList);
        iconListVisualizer.setIcons(metaData);


        holder.view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Log.v("Metronome", "SavedItemDatabase:onClickListener " + holder.getAdapterPosition());
                if(onItemClickedListener != null)
                    onItemClickedListener.onItemClicked(dataBase.get(holder.getAdapterPosition()), holder.getAdapterPosition());
            }
        });
        // Log.v("Metronome", "SavedItemDatabase:onBindViewHolder (position = " + position + ")");
    }

    SavedItem remove(int position) {
        assert dataBase != null;
        if(BuildConfig.DEBUG && position >= dataBase.size())
            throw new RuntimeException("Invalid position");

        SavedItem item = dataBase.get(position);
        dataBase.remove(position);
        notifyItemRemoved(position);

        return item;
    }

    @Override
    public int getItemCount() {
        assert dataBase != null;
        return dataBase.size();
    }

    void addItem(FragmentActivity activity, SavedItem item) {
        if(dataBase == null)
            loadData(activity);
        dataBase.add(item);
        notifyItemRangeInserted(dataBase.size()-1, dataBase.size());
        // Log.v("Metronome", "SavedItemDatabase:addItem: Number of items: " + dataBase.size());
    }

    void addItem(FragmentActivity activity, SavedItem item, int position) {
        if(dataBase == null)
            loadData(activity);
        if(BuildConfig.DEBUG && position > dataBase.size())
            throw new RuntimeException("Invalid position");

        dataBase.add(position, item);
        notifyItemRangeInserted(position, 1);
//        // Log.v("Metronome", "SavedItemDatabase:addItem: Number of items: " + dataBase.size());
    }

    void saveData(FragmentActivity activity) {
        if(dataBase == null)
            return;

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(String.format(Locale.ENGLISH,"%50s", activity.getString(R.string.version)));

        for(SavedItem si : dataBase)
        {
           stringBuilder.append(String.format(Locale.ENGLISH,"%200s%10s%5s%12.5f%sEND", si.title, si.date, si.time, si.bpm, si.playList));
        }
        String dataString = stringBuilder.toString();

        // Log.v("Metronome", "SavedItemDatabase:saveData: " + dataString);

        SharedPreferences preferences = activity.getPreferences(Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("savedDatabase", dataString);
        editor.apply();
    }

    void loadData(FragmentActivity activity) {
        if(dataBase != null)
            return;

        dataBase = new ArrayList<>();

        SharedPreferences preferences = activity.getPreferences(Context.MODE_PRIVATE);
        String dataString = preferences.getString("savedDatabase", "");

        // Log.v("Metronome", "SavedItemFragment:loadData: " + dataString);
        if(dataString.equals(""))
            return;

        if(dataString.length() < 50)
            return;
        String version = dataString.substring(0, 50).trim();

        int pos = 50;
        while(pos < dataString.length())
        {
            SavedItem si = new SavedItem();
            if(pos+50 >= dataString.length())
                return;

            if(pos+200 >= dataString.length())
                return;
            si.title = dataString.substring(pos, pos+200).trim();
            pos += 200;
            if(pos+10 >= dataString.length())
                return;
            si.date = dataString.substring(pos, pos+10);
            pos += 10;
            if(pos+5 >= dataString.length())
                return;
            si.time = dataString.substring(pos, pos+5);
            pos += 5;
            if(pos+6 >= dataString.length())
                return;
            si.bpm = Float.parseFloat(dataString.substring(pos, pos+12).trim());
            pos += 12;
            int playListEnd = dataString.indexOf("END", pos);
            if(playListEnd == -1)
                return;
            si.playList = dataString.substring(pos, playListEnd);
            pos = playListEnd+3;

            dataBase.add(si);
        }
    }

    void setOnItemClickedListener(OnItemClickedListener onItemClickedListener) {
        this.onItemClickedListener = onItemClickedListener;
    }

    //    public SavedItemDatabase.ViewHolder onCreate
}
