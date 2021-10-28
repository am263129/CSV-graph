package com.example.bluetooth.petvoiceviewer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.ViewHolder> {

    private ArrayList<DataItem> dataList;
    /**
     * Provide a reference to the type of views that you are using
     * (custom ViewHolder).
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView label, startTime, endTime;
        public ViewHolder(View view) {
            super(view);
            // Define click listener for the ViewHolder's View
            label = (TextView) view.findViewById(R.id.label);
            startTime = (TextView) view.findViewById(R.id.label_start);
            endTime = (TextView) view.findViewById(R.id.label_end);
         }
    }

    /**
     * Initialize the dataset of the Adapter.
     *
     * @param devicelist Arraylist<Sensor></> containing the data to populate views to be used
     * by RecyclerView.
     */
    public DataAdapter(ArrayList<DataItem> devicelist) {
        dataList = devicelist;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_data, viewGroup, false);

        return new ViewHolder(view);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {

        // Get element from your dataset at this position and replace the
        // contents of the view with that element
        viewHolder.label.setText(dataList.get(position).getLabel());
        viewHolder.startTime.setText(String.valueOf(dataList.get(position).getStartTime()));
        viewHolder.endTime.setText(String.valueOf(dataList.get(position).getEndTime()));
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return dataList.size();
    }
}