package com.omniai.assistant.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.chat.CodeBlockParser;
import com.omniai.assistant.chat.MarkdownRenderer;
import com.omniai.assistant.model.ChatMessage;
import com.omniai.assistant.ui.CodeBlockView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_AI = 1;

    private List<ChatMessage> messages;
    private MarkdownRenderer renderer;

    public ChatMessageAdapter(MarkdownRenderer renderer) {
        this.messages = new ArrayList<>();
        this.renderer = renderer;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? messages : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(ChatMessage message) {
        if (messages.isEmpty()) return;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getId() == message.getId()) {
                messages.set(i, message);
                notifyItemChanged(i);
                return;
            }
        }
        int lastPos = messages.size() - 1;
        messages.set(lastPos, message);
        notifyItemChanged(lastPos);
    }

    public void removeMessage(long id) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() == id) {
                messages.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? TYPE_USER : TYPE_AI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_message_ai, parent, false);
            return new AiMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserMessageViewHolder) {
            bindUserMessage((UserMessageViewHolder) holder, message);
        } else if (holder instanceof AiMessageViewHolder) {
            bindAiMessage((AiMessageViewHolder) holder, message);
        }
    }

    private void bindUserMessage(UserMessageViewHolder holder, ChatMessage message) {
        if (message.isText()) {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.contentText.setText(message.getContent());
            holder.attachmentImage.setVisibility(View.GONE);
        } else if (message.isImage()) {
            holder.contentText.setVisibility(View.GONE);
            holder.attachmentImage.setVisibility(View.VISIBLE);
            if (message.getAttachmentPath() != null) {
                holder.attachmentImage.setImageURI(Uri.parse(message.getAttachmentPath()));
            }
        } else if (message.isVoice()) {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.contentText.setText("🎤 " + message.getContent());
            holder.attachmentImage.setVisibility(View.GONE);
        } else if (message.isDocument()) {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.contentText.setText("📄 " + message.getContent());
            holder.attachmentImage.setVisibility(View.GONE);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(new Date(message.getTimestamp())));
    }

    private void bindAiMessage(AiMessageViewHolder holder, ChatMessage message) {
        if (message.isText()) {
            String content = message.getContent();
            List<CodeBlockParser.ContentSegment> segments = CodeBlockParser.parseContentSegments(content);

            boolean hasCode = false;
            StringBuilder textBuilder = new StringBuilder();

            for (CodeBlockParser.ContentSegment segment : segments) {
                if (segment.isCode()) {
                    hasCode = true;
                } else if (segment.isText()) {
                    textBuilder.append(segment.getContent());
                }
            }

            if (hasCode) {
                String textContent = textBuilder.toString().trim();
                if (textContent.isEmpty()) {
                    holder.contentText.setVisibility(View.GONE);
                    holder.cardBubble.setVisibility(View.GONE);
                } else {
                    holder.contentText.setVisibility(View.VISIBLE);
                    holder.cardBubble.setVisibility(View.VISIBLE);
                    if (renderer != null) {
                        renderer.renderMarkdown(holder.contentText, textContent);
                    } else {
                        holder.contentText.setText(textContent);
                    }
                }

                holder.codeBlocksContainer.removeAllViews();
                for (CodeBlockParser.ContentSegment segment : segments) {
                    if (segment.isCode()) {
                        CodeBlockView codeBlockView = new CodeBlockView(holder.itemView.getContext());
                        codeBlockView.setLanguage(segment.getLanguage());
                        codeBlockView.setCode(segment.getContent());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );
                        int margin = (int) (4 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
                        params.setMargins(0, margin, 0, margin);
                        codeBlockView.setLayoutParams(params);
                        holder.codeBlocksContainer.addView(codeBlockView);
                    }
                }
                holder.codeBlocksContainer.setVisibility(View.VISIBLE);
            } else {
                holder.contentText.setVisibility(View.VISIBLE);
                holder.cardBubble.setVisibility(View.VISIBLE);
                if (renderer != null) {
                    renderer.renderMarkdown(holder.contentText, content);
                } else {
                    holder.contentText.setText(content);
                }
                holder.codeBlocksContainer.setVisibility(View.GONE);
            }

            holder.attachmentImage.setVisibility(View.GONE);
        } else if (message.isImage()) {
            holder.contentText.setVisibility(View.GONE);
            holder.cardBubble.setVisibility(View.GONE);
            holder.codeBlocksContainer.setVisibility(View.GONE);
            holder.attachmentImage.setVisibility(View.VISIBLE);
            if (message.getAttachmentPath() != null) {
                holder.attachmentImage.setImageURI(Uri.parse(message.getAttachmentPath()));
            }
        } else if (message.isVoice()) {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.cardBubble.setVisibility(View.VISIBLE);
            holder.contentText.setText("🎤 " + message.getContent());
            holder.codeBlocksContainer.setVisibility(View.GONE);
            holder.attachmentImage.setVisibility(View.GONE);
        } else if (message.isDocument()) {
            holder.contentText.setVisibility(View.VISIBLE);
            holder.cardBubble.setVisibility(View.VISIBLE);
            holder.contentText.setText("📄 " + message.getContent());
            holder.codeBlocksContainer.setVisibility(View.GONE);
            holder.attachmentImage.setVisibility(View.GONE);
        }

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(new Date(message.getTimestamp())));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserMessageViewHolder extends RecyclerView.ViewHolder {

        TextView contentText;
        TextView timeText;
        ImageView attachmentImage;

        UserMessageViewHolder(View itemView) {
            super(itemView);
            contentText = itemView.findViewById(R.id.text_message_content);
            timeText = itemView.findViewById(R.id.text_message_time);
            attachmentImage = itemView.findViewById(R.id.image_attachment);
        }
    }

    static class AiMessageViewHolder extends RecyclerView.ViewHolder {

        TextView contentText;
        TextView timeText;
        ImageView attachmentImage;
        View cardBubble;
        LinearLayout codeBlocksContainer;

        AiMessageViewHolder(View itemView) {
            super(itemView);
            contentText = itemView.findViewById(R.id.tv_message_content);
            timeText = itemView.findViewById(R.id.text_message_time);
            attachmentImage = itemView.findViewById(R.id.image_attachment);
            cardBubble = itemView.findViewById(R.id.card_bubble);
            codeBlocksContainer = itemView.findViewById(R.id.layout_code_blocks);
        }
    }
}
