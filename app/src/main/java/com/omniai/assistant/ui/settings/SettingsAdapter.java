package com.omniai.assistant.ui.settings;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.omniai.assistant.R;

import java.util.List;

public class SettingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private List<SettingItem> items;
    private OnSettingChangeListener listener;

    public static class SettingItem {
        public String title;
        public String subtitle;
        public int iconRes;
        public String value;
        public boolean isSwitch;
        public boolean switchValue;
        public boolean hasArrow;
        public String group;

        public SettingItem(String title, String subtitle, int iconRes, String value,
                           boolean isSwitch, boolean switchValue, boolean hasArrow, String group) {
            this.title = title;
            this.subtitle = subtitle;
            this.iconRes = iconRes;
            this.value = value;
            this.isSwitch = isSwitch;
            this.switchValue = switchValue;
            this.hasArrow = hasArrow;
            this.group = group;
        }
    }

    public interface OnSettingChangeListener {
        void onSwitchChanged(String key, boolean value);
        void onClicked(String key);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView groupTitle;

        HeaderViewHolder(View itemView) {
            super(itemView);
            groupTitle = itemView.findViewById(R.id.tv_group_title);
        }
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView title;
        TextView subtitle;
        Switch toggle;
        TextView valueText;
        View arrow;

        ItemViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.iv_icon);
            title = itemView.findViewById(R.id.tv_title);
            subtitle = itemView.findViewById(R.id.tv_subtitle);
            toggle = itemView.findViewById(R.id.switch_toggle);
            valueText = itemView.findViewById(R.id.tv_value);
            arrow = itemView.findViewById(R.id.iv_arrow);
        }
    }

    public SettingsAdapter(List<SettingItem> items, OnSettingChangeListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).iconRes == 0 && items.get(position).isSwitch == false && items.get(position).hasArrow == false && items.get(position).value == null
                ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settings_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_settings_item, parent, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SettingItem item = items.get(position);

        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.groupTitle.setText(item.title);
        } else if (holder instanceof ItemViewHolder) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;

            if (item.iconRes != 0) {
                itemHolder.icon.setVisibility(View.VISIBLE);
                itemHolder.icon.setImageResource(item.iconRes);
            } else {
                itemHolder.icon.setVisibility(View.GONE);
            }

            itemHolder.title.setText(item.title);

            if (item.subtitle != null) {
                itemHolder.subtitle.setVisibility(View.VISIBLE);
                itemHolder.subtitle.setText(item.subtitle);
            } else {
                itemHolder.subtitle.setVisibility(View.GONE);
            }

            if (item.isSwitch) {
                itemHolder.toggle.setVisibility(View.VISIBLE);
                itemHolder.toggle.setOnCheckedChangeListener(null);
                itemHolder.toggle.setChecked(item.switchValue);
                itemHolder.toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (listener != null) {
                        listener.onSwitchChanged(item.title, isChecked);
                    }
                });
            } else {
                itemHolder.toggle.setVisibility(View.GONE);
            }

            if (item.value != null && !item.isSwitch) {
                itemHolder.valueText.setVisibility(View.VISIBLE);
                itemHolder.valueText.setText(item.value);
            } else {
                itemHolder.valueText.setVisibility(View.GONE);
            }

            itemHolder.arrow.setVisibility(item.hasArrow ? View.VISIBLE : View.GONE);

            itemHolder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onClicked(item.title);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void updateData(List<SettingItem> newItems) {
        items = newItems;
        notifyDataSetChanged();
    }
}
