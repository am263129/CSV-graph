package com.example.bluetooth.petvoiceviewer;

import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private ArrayList<DataItem> dataList;
    public static int selectedPos = RecyclerView.NO_POSITION;
    public static SparseBooleanArray selectedItems = new SparseBooleanArray();
    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        private final TextView label, startTime, endTime;
        public ViewHolder(View view) {
            super(view);
            view.setOnClickListener(this);
            label = (TextView) view.findViewById(R.id.label);
            startTime = (TextView) view.findViewById(R.id.label_start);
            endTime = (TextView) view.findViewById(R.id.label_end);
         }

        @Override
        public void onClick(View view) {
            if (selectedItems.get(getAdapterPosition(), false)) {
                selectedItems.delete(getAdapterPosition());
                view.setSelected(false);
            }
            else {
                selectedItems.put(getAdapterPosition(), true);
                view.setSelected(true);
            }
        }
    }

    /**
     * Initialize the dataset of the Adapter.
     *
     * @param dataList Arraylist<DataItem></> containing the data to populate views to be used
     * by RecyclerView.
     */
    public DataAdapter(ArrayList<DataItem> dataList) {
        this.dataList = dataList;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_data, viewGroup, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        viewHolder.itemView.setSelected(selectedItems.get(position, false));
        System.out.println(selectedItems);
        viewHolder.label.setText(dataList.get(position).getLabel());
        viewHolder.startTime.setText(String.valueOf(dataList.get(position).getStartTime()));
        viewHolder.endTime.setText(String.valueOf(dataList.get(position).getEndTime()));
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

}