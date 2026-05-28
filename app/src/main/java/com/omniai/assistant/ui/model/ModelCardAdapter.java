package com.omniai.assistant.ui.model;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncDifferConfig;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.model.AIModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ModelCardAdapter extends RecyclerView.Adapter<ModelCardAdapter.ViewHolder> {

    private final AsyncListDiffer<AIModel> differ;
    private OnModelActionListener listener;

    public interface OnModelActionListener {
        void onEnable(AIModel model);
        void onDelete(AIModel model);
        void onClick(AIModel model);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView statusTag;
        TextView fileSize;
        TextView quantType;
        Switch enableSwitch;
        ImageButton deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_model_name);
            statusTag = itemView.findViewById(R.id.tv_status_tag);
            fileSize = itemView.findViewById(R.id.tv_file_size);
            quantType = itemView.findViewById(R.id.tv_quant_type);
            enableSwitch = itemView.findViewById(R.id.switch_enable);
            deleteBtn = itemView.findViewById(R.id.btn_delete);
        }
    }

    public ModelCardAdapter(List<AIModel> models, OnModelActionListener listener) {
        this.listener = listener;

        DiffUtil.ItemCallback<AIModel> itemCallback = new DiffUtil.ItemCallback<AIModel>() {
            @Override
            public boolean areItemsTheSame(@NonNull AIModel oldItem, @NonNull AIModel newItem) {
                return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull AIModel oldItem, @NonNull AIModel newItem) {
                return oldItem.equals(newItem);
            }
        };

        AsyncDifferConfig<AIModel> config = new AsyncDifferConfig.Builder<>(itemCallback)
                .setBackgroundThreadExecutor(Executors.newSingleThreadExecutor())
                .build();

        this.differ = new AsyncListDiffer<>(this, config);
        this.differ.submitList(models != null ? models : new ArrayList<>());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_model_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AIModel model = differ.getCurrentList().get(position);

        holder.name.setText(model.getName());
        holder.fileSize.setText(formatFileSize(model.getFileSize()));
        holder.quantType.setText(model.getQuantType());

        updateStatusTag(holder.statusTag, model);

        holder.enableSwitch.setOnCheckedChangeListener(null);
        holder.enableSwitch.setChecked(model.isEnabled());
        holder.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onEnable(model);
            }
        });

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(model);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(model);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            showModelOptions(v, model);
            return true;
        });
    }

    private void updateStatusTag(TextView statusTag, AIModel model) {
        String status = model.getStatus();
        int color;

        switch (status) {
            case AIModel.STATUS_LOADED:
                color = Color.parseColor("#4CAF50");
                statusTag.setText(R.string.status_loaded);
                break;
            case AIModel.STATUS_RUNNING:
                color = Color.parseColor("#2196F3");
                statusTag.setText(R.string.status_running);
                break;
            case AIModel.STATUS_GPU:
                color = Color.parseColor("#2196F3");
                statusTag.setText(R.string.status_gpu);
                break;
            case AIModel.STATUS_LORA:
                color = Color.parseColor("#9C27B0");
                statusTag.setText(R.string.status_lora);
                break;
            default:
                color = Color.parseColor("#9E9E9E");
                statusTag.setText(R.string.status_idle);
                break;
        }

        statusTag.setTextColor(color);
        statusTag.setBackgroundResource(R.drawable.bg_status_tag);
    }

    private void showModelOptions(View v, AIModel model) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(v.getContext(), v);
        popup.getMenu().add(v.getContext().getString(R.string.action_rename));
        popup.getMenu().add(v.getContext().getString(R.string.action_categorize));
        popup.getMenu().add(v.getContext().getString(R.string.action_encrypt));
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if (title.equals(v.getContext().getString(R.string.action_rename))) {
                showRenameDialog(v, model);
            } else if (title.equals(v.getContext().getString(R.string.action_categorize))) {
                showCategorizeDialog(v, model);
            } else if (title.equals(v.getContext().getString(R.string.action_encrypt))) {
                showEncryptDialog(v, model);
            }
            return true;
        });
        popup.show();
    }

    private void showRenameDialog(View v, AIModel model) {
        android.widget.EditText input = new android.widget.EditText(v.getContext());
        input.setText(model.getName());
        new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                .setTitle(R.string.action_rename)
                .setView(input)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    model.setName(input.getText().toString().trim());
                    notifyDataSetChanged();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCategorizeDialog(View v, AIModel model) {
        String[] categories = v.getContext().getResources().getStringArray(R.array.model_categories);
        new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                .setTitle(R.string.action_categorize)
                .setItems(categories, (dialog, which) -> {
                    model.setCategory(categories[which]);
                    notifyDataSetChanged();
                })
                .show();
    }

    private void showEncryptDialog(View v, AIModel model) {
        new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                .setTitle(R.string.action_encrypt)
                .setMessage(v.getContext().getString(R.string.encrypt_model_message, model.getName()))
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    model.setEncrypted(true);
                    notifyDataSetChanged();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    public void updateData(List<AIModel> newModels) {
        differ.submitList(newModels != null ? newModels : new ArrayList<>());
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
