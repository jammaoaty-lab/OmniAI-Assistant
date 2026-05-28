package com.omniai.assistant.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.agent.AgentStep;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.StepViewHolder> {

    private final List<AgentStep> steps = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public void addStep(AgentStep step) {
        steps.add(step);
        notifyItemInserted(steps.size() - 1);
    }

    public void clear() {
        steps.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StepViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_agent_step, parent, false);
        return new StepViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StepViewHolder holder, int position) {
        holder.bind(steps.get(position));
    }

    @Override
    public int getItemCount() {
        return steps.size();
    }

    class StepViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvBadge;
        private final TextView tvTime;
        private final TextView tvContent;

        StepViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBadge = itemView.findViewById(R.id.tv_step_badge);
            tvTime = itemView.findViewById(R.id.tv_step_time);
            tvContent = itemView.findViewById(R.id.tv_step_content);
        }

        void bind(AgentStep step) {
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(8);
            tvBadge.setBackground(badgeBg);
            tvTime.setText(timeFormat.format(new Date(step.getTimestamp())));

            switch (step.getType()) {
                case THINKING:
                    tvBadge.setText("💭 思考");
                    badgeBg.setColor(0xFFEFF6FF);
                    tvBadge.setTextColor(0xFF2563EB);
                    tvContent.setTextColor(0xFF334155);
                    tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvContent.setTextSize(14);
                    tvContent.setText(step.getContent());
                    break;
                case TOOL_CALL:
                    tvBadge.setText("🔧 调用 " + step.getToolName());
                    badgeBg.setColor(0xFFFFFBEB);
                    tvBadge.setTextColor(0xFFD97706);
                    tvContent.setTextColor(0xFF334155);
                    tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvContent.setTextSize(14);
                    tvContent.setText("参数: " + step.getToolInput());
                    break;
                case TOOL_RESULT:
                    tvBadge.setText("📋 结果 " + step.getToolName());
                    badgeBg.setColor(0xFFF0FDF4);
                    tvBadge.setTextColor(0xFF16A34A);
                    tvContent.setTextColor(0xFF334155);
                    tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvContent.setTextSize(14);
                    String output = step.getToolOutput();
                    if (output != null && output.length() > 500) {
                        output = output.substring(0, 500) + "...";
                    }
                    tvContent.setText(output);
                    break;
                case FINAL_ANSWER:
                    tvBadge.setText("✅ 最终答案");
                    badgeBg.setColor(0xFF2563EB);
                    tvBadge.setTextColor(0xFFFFFFFF);
                    tvContent.setTextColor(0xFF1E293B);
                    tvContent.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvContent.setTextSize(15);
                    tvContent.setText(step.getContent());
                    break;
                case ERROR:
                    tvBadge.setText("❌ 错误");
                    badgeBg.setColor(0xFFFEF2F2);
                    tvBadge.setTextColor(0xFFDC2626);
                    tvContent.setTextColor(0xFFDC2626);
                    tvContent.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tvContent.setTextSize(14);
                    tvContent.setText(step.getContent());
                    break;
            }
        }
    }
}
