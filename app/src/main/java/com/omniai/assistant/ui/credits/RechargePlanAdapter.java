package com.omniai.assistant.ui.credits;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;
import com.omniai.assistant.credits.CreditsManager;

import java.util.List;

public class RechargePlanAdapter extends RecyclerView.Adapter<RechargePlanAdapter.PlanViewHolder> {

    private List<CreditsManager.RechargePlan> plans;
    private OnRechargePlanClickListener listener;

    public interface OnRechargePlanClickListener {
        void onRechargePlanClick(CreditsManager.RechargePlan plan);
    }

    public RechargePlanAdapter(List<CreditsManager.RechargePlan> plans, OnRechargePlanClickListener listener) {
        this.plans = plans;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recharge_plan, parent, false);
        return new PlanViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlanViewHolder holder, int position) {
        CreditsManager.RechargePlan plan = plans.get(position);
        holder.planNameText.setText(plan.getName());
        holder.priceText.setText(holder.itemView.getContext().getString(
                R.string.credits_price_format, plan.getPrice()));
        holder.creditsText.setText(holder.itemView.getContext().getString(
                R.string.credits_amount_format, plan.getCredits()));

        if (plan.isPopular()) {
            holder.popularBadge.setVisibility(View.VISIBLE);
            GradientDrawable border = new GradientDrawable();
            border.setCornerRadius(holder.itemView.getContext().getResources()
                    .getDimension(R.dimen.card_radius));
            border.setStroke(2, Color.parseColor("#2563EB"));
            border.setColor(Color.WHITE);
            holder.cardContainer.setBackground(border);
        } else {
            holder.popularBadge.setVisibility(View.GONE);
            holder.cardContainer.setBackground(null);
        }

        holder.selectBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRechargePlanClick(plan);
            }
        });
    }

    @Override
    public int getItemCount() {
        return plans != null ? plans.size() : 0;
    }

    public void updatePlans(List<CreditsManager.RechargePlan> newPlans) {
        this.plans = newPlans;
        notifyDataSetChanged();
    }

    static class PlanViewHolder extends RecyclerView.ViewHolder {

        View cardContainer;
        TextView planNameText;
        TextView priceText;
        TextView creditsText;
        TextView popularBadge;
        Button selectBtn;

        PlanViewHolder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.card_container);
            planNameText = itemView.findViewById(R.id.tv_plan_name);
            priceText = itemView.findViewById(R.id.tv_plan_price);
            creditsText = itemView.findViewById(R.id.tv_plan_credits);
            popularBadge = itemView.findViewById(R.id.tv_popular_badge);
            selectBtn = itemView.findViewById(R.id.btn_select_plan);
        }
    }
}
