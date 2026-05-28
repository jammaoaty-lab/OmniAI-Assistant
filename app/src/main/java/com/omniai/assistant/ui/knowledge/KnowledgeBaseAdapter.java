package com.omniai.assistant.ui.knowledge;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.model.KnowledgeBase;

import java.util.ArrayList;
import java.util.List;

public class KnowledgeBaseAdapter extends RecyclerView.Adapter<KnowledgeBaseAdapter.ViewHolder> {

    private List<KnowledgeBase> knowledgeBases;
    private OnKbActionListener listener;

    public interface OnKbActionListener {
        void onClick(KnowledgeBase kb);
        void onDelete(KnowledgeBase kb);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView docCount;
        TextView description;
        TextView statusTag;
        TextView size;
        ImageButton deleteBtn;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_kb_name);
            docCount = itemView.findViewById(R.id.tv_doc_count);
            description = itemView.findViewById(R.id.tv_description);
            statusTag = itemView.findViewById(R.id.tv_status_tag);
            size = itemView.findViewById(R.id.tv_kb_size);
            deleteBtn = itemView.findViewById(R.id.btn_delete);
        }
    }

    public KnowledgeBaseAdapter(List<KnowledgeBase> knowledgeBases, OnKbActionListener listener) {
        this.knowledgeBases = knowledgeBases != null ? knowledgeBases : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_knowledge_base, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        KnowledgeBase kb = knowledgeBases.get(position);

        holder.name.setText(kb.getName());
        holder.docCount.setText(holder.itemView.getContext().getString(R.string.doc_count_format, kb.getDocCount()));
        holder.description.setText(kb.getDescription());
        holder.size.setText(formatSize(kb.getSize()));

        updateStatusTag(holder.statusTag, kb);

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(kb);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(kb);
            }
        });
    }

    private void updateStatusTag(TextView statusTag, KnowledgeBase kb) {
        String status = kb.getStatus();
        switch (status) {
            case KnowledgeBase.STATUS_READY:
                statusTag.setText(R.string.kb_status_ready);
                statusTag.setTextColor(0xFF4CAF50);
                break;
            case KnowledgeBase.STATUS_INDEXING:
                statusTag.setText(R.string.kb_status_indexing);
                statusTag.setTextColor(0xFFFF9800);
                break;
            case KnowledgeBase.STATUS_ERROR:
                statusTag.setText(R.string.kb_status_error);
                statusTag.setTextColor(0xFFF44336);
                break;
            default:
                statusTag.setText(R.string.kb_status_idle);
                statusTag.setTextColor(0xFF9E9E9E);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return knowledgeBases.size();
    }

    public void updateData(List<KnowledgeBase> newData) {
        knowledgeBases = newData != null ? newData : new ArrayList<>();
        notifyDataSetChanged();
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
