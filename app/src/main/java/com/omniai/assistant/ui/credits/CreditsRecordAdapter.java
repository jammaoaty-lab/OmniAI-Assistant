package com.omniai.assistant.ui.credits;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CreditsRecordAdapter extends RecyclerView.Adapter<CreditsRecordAdapter.RecordViewHolder> {

    private List<CreditsManager.CreditsRecord> records = new ArrayList<>();

    public CreditsRecordAdapter(List<CreditsManager.CreditsRecord> records) {
        this.records = records != null ? records : new ArrayList<>();
    }

    @NonNull
    @Override
    public RecordViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credits_record, parent, false);
        return new RecordViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecordViewHolder holder, int position) {
        CreditsManager.CreditsRecord record = records.get(position);

        int amount = record.getAmount();
        if (amount > 0) {
            holder.amountText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    R.color.success));
            holder.amountText.setText("+" + amount);
        } else {
            holder.amountText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(),
                    R.color.danger));
            holder.amountText.setText(String.valueOf(amount));
        }

        holder.descriptionText.setText(record.getDescription());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(new Date(record.getTimestamp())));

        setTypeInfo(holder, record.getType());
    }

    private void setTypeInfo(RecordViewHolder holder, String type) {
        int iconRes;
        String label;
        switch (type != null ? type : "") {
            case "INVITE":
                iconRes = R.drawable.ic_invite;
                label = holder.itemView.getContext().getString(R.string.credits_type_invite);
                break;
            case "RECHARGE":
                iconRes = R.drawable.ic_recharge;
                label = holder.itemView.getContext().getString(R.string.credits_type_recharge);
                break;
            case "CONSUME":
                iconRes = R.drawable.ic_consume;
                label = holder.itemView.getContext().getString(R.string.credits_type_consume);
                break;
            case "SYSTEM":
                iconRes = R.drawable.ic_system;
                label = holder.itemView.getContext().getString(R.string.credits_type_system);
                break;
            default:
                iconRes = R.drawable.ic_system;
                label = type != null ? type : "";
                break;
        }
        holder.typeIcon.setImageResource(iconRes);
        holder.typeLabel.setText(label);
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    public void updateRecords(List<CreditsManager.CreditsRecord> newRecords) {
        this.records = newRecords != null ? newRecords : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class RecordViewHolder extends RecyclerView.ViewHolder {

        TextView amountText;
        ImageView typeIcon;
        TextView typeLabel;
        TextView descriptionText;
        TextView timeText;

        RecordViewHolder(@NonNull View itemView) {
            super(itemView);
            amountText = itemView.findViewById(R.id.tv_record_amount);
            typeIcon = itemView.findViewById(R.id.iv_record_type);
            typeLabel = itemView.findViewById(R.id.tv_record_type);
            descriptionText = itemView.findViewById(R.id.tv_record_description);
            timeText = itemView.findViewById(R.id.tv_record_time);
        }
    }
}
