package com.omniai.assistant.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.chat.QuickCommand;

import java.util.ArrayList;
import java.util.List;

public class QuickCommandAdapter extends RecyclerView.Adapter<QuickCommandAdapter.ViewHolder> {

    private List<QuickCommand> commands;
    private OnQuickCommandClickListener listener;

    public QuickCommandAdapter(OnQuickCommandClickListener listener) {
        this.commands = new ArrayList<>();
        this.listener = listener;
    }

    public void setCommands(List<QuickCommand> commands) {
        this.commands = commands != null ? commands : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quick_command, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        QuickCommand command = commands.get(position);
        holder.commandName.setText(command.getName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onClick(command);
            }
        });
    }

    @Override
    public int getItemCount() {
        return commands.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView commandName;

        ViewHolder(View itemView) {
            super(itemView);
            commandName = itemView.findViewById(R.id.text_command_name);
        }
    }

    public interface OnQuickCommandClickListener {
        void onClick(QuickCommand command);
    }
}
