package com.omniai.assistant.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.model.Conversation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private List<Conversation> conversations;
    private OnConversationClickListener listener;
    private String selectedId;

    public ConversationAdapter(OnConversationClickListener listener) {
        this.conversations = new ArrayList<>();
        this.listener = listener;
        this.selectedId = null;
    }

    public void setConversations(List<Conversation> conversations) {
        this.conversations = conversations != null ? conversations : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedId(String selectedId) {
        String previousId = this.selectedId;
        this.selectedId = selectedId;
        if (previousId != null) {
            int prevPos = findPosition(previousId);
            if (prevPos != -1) notifyItemChanged(prevPos);
        }
        if (selectedId != null) {
            int newPos = findPosition(selectedId);
            if (newPos != -1) notifyItemChanged(newPos);
        }
    }

    public void updateConversation(Conversation conversation) {
        int pos = findPosition(conversation.getId());
        if (pos != -1) {
            conversations.set(pos, conversation);
            notifyItemChanged(pos);
        }
    }

    private int findPosition(String id) {
        for (int i = 0; i < conversations.size(); i++) {
            if (conversations.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Conversation conversation = conversations.get(position);
        holder.titleText.setText(conversation.getTitle());

        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(new Date(conversation.getUpdatedAt())));

        if (conversation.getModelId() != null && !conversation.getModelId().isEmpty()) {
            holder.modelTag.setVisibility(View.VISIBLE);
            holder.modelTag.setText(conversation.getModelId());
        } else {
            holder.modelTag.setVisibility(View.GONE);
        }

        holder.pinIcon.setVisibility(conversation.isPinned() ? View.VISIBLE : View.GONE);

        boolean isSelected = conversation.getId().equals(selectedId);
        holder.itemView.setSelected(isSelected);
        holder.itemView.setActivated(isSelected);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(conversation);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(conversation);
            }
            return true;
        });

        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(conversation);
            }
        });

        holder.pinIcon.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPin(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView titleText;
        TextView timeText;
        TextView modelTag;
        ImageView pinIcon;
        ImageView deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_conversation_title);
            timeText = itemView.findViewById(R.id.text_conversation_time);
            modelTag = itemView.findViewById(R.id.text_model_tag);
            pinIcon = itemView.findViewById(R.id.icon_pin);
            deleteButton = itemView.findViewById(R.id.btn_delete_conversation);
        }
    }

    public interface OnConversationClickListener {
        void onClick(Conversation conversation);
        void onLongClick(Conversation conversation);
        void onDelete(Conversation conversation);
        void onPin(Conversation conversation);
    }
}
