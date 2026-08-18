package com.spendwise.ui.search;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.databinding.ItemSearchResultBinding;
import com.spendwise.databinding.ItemSearchSectionBinding;
import com.spendwise.domain.Category;
import com.spendwise.ui.CategoryIcons;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Draws the grouped search results, choosing a layout per row type. */
public class SearchResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    public interface OnResultClickListener {
        void onResultClick(SearchResult result);
    }

    private final List<SearchResult> items = new ArrayList<>();
    private final OnResultClickListener clickListener;
    private String query = "";

    public SearchResultAdapter(OnResultClickListener clickListener) {
        this.clickListener = clickListener;
    }

    /** Hands the adapter the new grouped results. */
    @SuppressWarnings("NotifyDataSetChanged")
    public void submit(List<SearchResult> results, String query) {
        this.query = query == null ? "" : query;
        items.clear();
        if (results != null) {
            items.addAll(results);
        }
        notifyDataSetChanged();
    }

    /** Returns the item view type. */
    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    /** Returns the item count. */
    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == SearchResult.TYPE_SECTION) {
            return new SectionViewHolder(
                    ItemSearchSectionBinding.inflate(inflater, parent, false));
        }
        return new ResultViewHolder(
                ItemSearchResultBinding.inflate(inflater, parent, false), clickListener);
    }

    /** Called by the framework to fill one row with data. */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchResult item = items.get(position);
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).bind(item);
        } else {
            ((ResultViewHolder) holder).bind(item, query);
        }
    }

    /** Highlight. */
    static CharSequence highlight(Context context, @Nullable String text, String query) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (query == null || query.isEmpty()) {
            return text;
        }
        int start = text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        if (start < 0) {
            return text;
        }
        int end = Math.min(text.length(), start + query.length());
        SpannableString span = new SpannableString(text);
        span.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(context, R.color.brand_primary)),
                start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return span;
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSectionBinding binding;

        SectionViewHolder(ItemSearchSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SearchResult item) {
            binding.textSectionTitle.setText(item.getTitle());
            String count = item.getTrailing();
            binding.textSectionCount.setText(count == null ? "" : count);
            binding.textSectionCount.setVisibility(
                    count == null || count.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    static class ResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;
        private final OnResultClickListener clickListener;

        ResultViewHolder(ItemSearchResultBinding binding, OnResultClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(SearchResult item, String query) {
            Context context = binding.getRoot().getContext();
            Category category = item.getCategory();

            binding.textTitle.setText(highlight(context, item.getTitle(), query));

            String subtitle = item.getSubtitle();
            if (subtitle == null || subtitle.isEmpty()) {
                binding.textSubtitle.setVisibility(View.GONE);
            } else {
                binding.textSubtitle.setText(highlight(context, subtitle, query));
                binding.textSubtitle.setVisibility(View.VISIBLE);
            }

            String trailing = item.getTrailing();
            if (trailing == null || trailing.isEmpty()) {
                binding.textTrailing.setVisibility(View.GONE);
            } else {
                binding.textTrailing.setText(trailing);
                binding.textTrailing.setTextColor(ContextCompat.getColor(context,
                        item.getTrailingColourRes() == SearchResult.NO_COLOUR
                                ? R.color.text_primary
                                : item.getTrailingColourRes()));
                binding.textTrailing.setVisibility(View.VISIBLE);
            }

            binding.imageChevron.setVisibility(
                    item.getType() == SearchResult.TYPE_TRANSACTION ? View.GONE : View.VISIBLE);

            binding.imageCategoryIcon.setImageResource(CategoryIcons.iconFor(category));
            GradientDrawable circle = (GradientDrawable) ContextCompat.getDrawable(
                    context, R.drawable.bg_category_circle);
            if (circle != null && category != null) {
                circle = (GradientDrawable) circle.mutate();
                circle.setColor(Color.parseColor(category.getColourHex()));
                binding.imageCategoryIcon.setBackground(circle);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onResultClick(item);
                }
            });
        }
    }
}
