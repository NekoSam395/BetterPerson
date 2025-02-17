package com.aliucord.plugins;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.aliucord.CollectionUtils;
import com.aliucord.Utils;
import com.aliucord.plugins.ReviewListModal.Adapter;
import com.aliucord.plugins.ReviewListModal.CustomEditText;
import com.aliucord.plugins.dataclasses.Review;
import com.aliucord.utils.DimenUtils;
import com.aliucord.utils.RxUtils;
import com.aliucord.widgets.LinearLayout;
import com.discord.models.user.CoreUser;
import com.discord.models.user.User;
import com.discord.stores.StoreStream;
import com.discord.utilities.color.ColorCompat;
import com.discord.utilities.rest.RestAPI;

import java.util.ArrayList;
import java.util.List;

public class ServerReviewsView extends LinearLayout {
    Adapter adapter;
    List<Review> reviews = new ArrayList<>();
    CustomEditText et;
    ImageView submit;
    int padding;
    LinearLayout sendCommentLayout;
    RecyclerView recycler;
    TextView title;
    TextView nobodyReviewed;
    Long guildID;
    Cache cache = new Cache();
    Runnable loadData = (() -> {

        reviews.clear();
        var data = ServerReviewsAPI.getReviews(guildID);

        if (data != null) {
            reviews.addAll(data);
        } else {
            reviews.clear();
            reviews.add(new Review("There was an error while getting reviews", 0L, 0L, -1, ""));
        }

        Utils.mainThread.post(() -> {
            if (reviews.size() == 0) nobodyReviewed.setVisibility(VISIBLE);
            else nobodyReviewed.setVisibility(GONE);

            adapter.notifyDataSetChanged();
        });

    });

    public ServerReviewsView(Context ctx, Long guildID) {
        super(ctx);
        setOrientation(android.widget.LinearLayout.VERTICAL);
        this.guildID = guildID;

        title = new TextView(ctx, null, 0, com.lytefast.flexinput.R.i.UserProfile_Section_Header);
        recycler = new RecyclerView(ctx);
        sendCommentLayout = new LinearLayout(ctx);
        et = new CustomEditText(ctx);
        submit = new ImageView(ctx);
        nobodyReviewed = new TextView(ctx);
        padding = DimenUtils.getDefaultPadding();
        var reporting = new TextView(ctx);
        var buttonFrameLayout = new FrameLayout(ctx);

        //etLayout.setGravity(Gravity.CENTER_VERTICAL);
        reporting.setText("Small Note: To Report someones review long click to review and click 'Report Review'");
        reporting.setTextSize(9f);
        reporting.setPadding(0,padding/3,0,0);

        sendCommentLayout.addView(et);
        sendCommentLayout.addView(buttonFrameLayout);
        sendCommentLayout.setOrientation(HORIZONTAL);

        nobodyReviewed.setText("Looks like nobody reviewed this server, You can be first");
        nobodyReviewed.setPadding(0,padding/3,0,padding);
        nobodyReviewed.setVisibility(GONE);
        nobodyReviewed.setTypeface(null, Typeface.BOLD_ITALIC);
        nobodyReviewed.setTextSize(20f);

        addView(title);
        addView(reporting);
        addView(recycler);
        addView(nobodyReviewed);
        addView(sendCommentLayout);

        var etLayoutParams = (android.widget.LinearLayout.LayoutParams) et.getLayoutParams();
        etLayoutParams.width = 0;
        etLayoutParams.height = DimenUtils.dpToPx(40);
        etLayoutParams.weight = 1;
        etLayoutParams.rightMargin = padding / 3;
        et.setLayoutParams(etLayoutParams);

        var buttonLayoutParams = (LinearLayout.LayoutParams) buttonFrameLayout.getLayoutParams();
        buttonLayoutParams.width = DimenUtils.dpToPx(40);
        buttonLayoutParams.height = DimenUtils.dpToPx(40);
        buttonFrameLayout.setLayoutParams(buttonLayoutParams);

        title.setText("Server Reviews");
        title.setPadding(0,padding,0,0);

        recycler.setLayoutManager(new LinearLayoutManager(ctx, RecyclerView.VERTICAL, false));
        recycler.setPadding(0,padding,0,0);
        adapter = new com.aliucord.plugins.ReviewListModal.Adapter(reviews);
        recycler.setAdapter(adapter);

        Utils.threadPool.execute(loadData);

        et.setHint("Enter Your Comment ");
        et.setBackgroundResource(android.R.color.transparent);

        buttonFrameLayout.setBackgroundResource(Utils.getResId("drawable_circle_black", "drawable"));

        buttonFrameLayout.setBackgroundTintList(ColorStateList.valueOf(ColorCompat.getColor(ctx, com.lytefast.flexinput.R.c.accent_material_light)));
        buttonFrameLayout.addView(submit);
        buttonFrameLayout.setOnClickListener(this::onSubmit);
        submit.setImageResource(Utils.getResId("ic_send_24dp", "drawable"));
        submit.setPadding(padding / 3 * 2, 0, padding / 2, 0);
    }

    public void onSubmit(View v) {
        var message = et.getText().toString().trim();

        if (ServerReviews.staticSettings.getString("token", "").equals("")) {
            Utils.showToast("You need to authorazite to send comment");
            Utils.openPageWithProxy(Utils.getAppActivity(), new AuthorazationPage());
        } else {
            if (message.isEmpty()) {
                Utils.showToast("Enter some comment and try again");
                return;
            }
            else if(message.length()>500) {
                Utils.showToast("Comment Too Long");
                return;
            }

            submit.setClickable(false);
            et.clearFocus();
            Utils.threadPool.execute(() -> {
                var response = ServerReviewsAPI.addReview(message, guildID, ServerReviews.staticSettings.getString("token", ""));
                Utils.showToast(response.getMessage());
                Utils.mainThread.post(() -> submit.setClickable(true));

                if (response.isSuccessful()) {
                    var currentUsername = StoreStream.getUsers().getMe().getUsername() + "#" + StoreStream.getUsers().getMe().getDiscriminator();
                    var currentUserID = StoreStream.getUsers().getMe().getId();
                    Utils.mainThread.post(() -> {
                        et.setText("");

                        if (response.isUpdated()) {
                            var ix = CollectionUtils.findIndex(reviews, review -> review.getSenderdiscordid() == currentUserID);
                            if (ix == -1) return;
                            var rev = reviews.get(ix);
                            rev.comment = message;
                            reviews.set(ix, rev);
                            adapter.notifyItemChanged(ix);
                        } else {
                            reviews.add(0, new Review(message, 0L, currentUserID, -1, currentUsername));
                            adapter.notifyItemInserted(0);
                            nobodyReviewed.setVisibility(GONE);
                        }
                    });
                } else {
                    Utils.showToast("An Error Occured");
                }

            });

        }

    }

}
