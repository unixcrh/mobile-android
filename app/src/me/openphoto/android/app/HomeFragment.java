
package me.openphoto.android.app;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.openphoto.android.app.model.Photo;
import me.openphoto.android.app.net.IOpenPhotoApi;
import me.openphoto.android.app.net.Paging;
import me.openphoto.android.app.net.PhotosResponse;
import me.openphoto.android.app.twitter.TwitterProvider;
import me.openphoto.android.app.twitter.TwitterUtils;
import me.openphoto.android.app.ui.adapter.EndlessAdapter;
import me.openphoto.android.app.ui.widget.YesNoDialogFragment;
import me.openphoto.android.app.ui.widget.YesNoDialogFragment.YesNoButtonPressedHandler;
import me.openphoto.android.app.util.ImageWorker;
import me.openphoto.android.app.util.LoadingControl;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.bugsense.trace.BugSenseHandler;

public class HomeFragment extends CommonFragment implements Refreshable
{
    public static final String TAG = HomeFragment.class.getSimpleName();

    private LoadingControl loadingControl;
    private NewestPhotosAdapter mAdapter;
    private LayoutInflater mInflater;
    private ImageWorker iw;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_home, container, false);
        mInflater = inflater;

        refresh(v);
        return v;
    }

    @Override
    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
        loadingControl = ((LoadingControl) activity);
        if (iw == null)
        {
            iw = new ImageWorker(activity, loadingControl);
        }

    }

    @Override
    public void refresh()
    {
        refresh(getView());
    }

    public void refresh(View view)
    {
        mAdapter = new NewestPhotosAdapter(getActivity());
        ListView list = (ListView) view.findViewById(R.id.list_newest_photos);
        list.setAdapter(mAdapter);
    }

    @Override
    public void onDestroyView()
    {
        super.onDestroyView();
        mAdapter.forceStopLoadingIfNecessary();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
            ContextMenuInfo menuInfo)
    {
        if (v.getId() == R.id.share_button)
        {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.share, menu);
            super.onCreateContextMenu(menu, v, menuInfo);
        } else
        {
            super.onCreateContextMenu(menu, v, menuInfo);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        int menuItemIndex = item.getItemId();
        switch (menuItemIndex)
        {
            case R.id.menu_share_email:
                shareViaEMail(mAdapter.activePhoto);
                break;
            case R.id.menu_share_twitter:
                shareViaTwitter(mAdapter.activePhoto);
                break;
            case R.id.menu_share_facebook:
                // TODO
                alert("Facebook share for item: "
                        + mAdapter.activePhoto.getId());
                break;
        }
        return true;
    }

    private void shareViaEMail(Photo photo)
    {
        String mailId = "";
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts("mailto", mailId, null));
        emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                getString(R.string.share_email_default_title));
        String url = photo.getUrl(Photo.ORIGINAL_SIZE);
        String bodyText = String.format(getString(R.string.share_email_default_body),
                url, url);
        emailIntent.putExtra(
                Intent.EXTRA_TEXT,
                Html.fromHtml(bodyText)
                );
        startActivity(Intent.createChooser(emailIntent,
                getString(R.string.share_email_send_title)));
    }

    private void shareViaTwitter(Photo photo)
    {
        if (TwitterProvider.getTwitter(getActivity()) == null)
        {
            YesNoDialogFragment dialogFragment = YesNoDialogFragment
                    .newInstance(R.string.share_twitter_authorisation_question,
                            new YesNoButtonPressedHandler()
                            {
                                private static final long serialVersionUID = 1L;

                                @Override
                                public void yesButtonPressed(
                                        DialogInterface dialog)
                                {
                                    TwitterUtils.askOAuth(getActivity());
                                }

                                @Override
                                public void noButtonPressed(
                                        DialogInterface dialog)
                                {
                                    // do nothing
                                }
                            });
            dialogFragment.show(getActivity().getSupportFragmentManager(),
                    "dialog");
        } else
        {
            FragmentManager fm = getActivity().getSupportFragmentManager();
            TwitterFragment twitterDialog = new TwitterFragment();
            twitterDialog.setPhoto(photo);
            twitterDialog.show(fm, "Twitter");
        }
    }

    private class NewestPhotosAdapter extends EndlessAdapter<Photo>
    {
        private final IOpenPhotoApi mOpenPhotoApi;
        private final Context mContext;
        private Photo activePhoto;

        public NewestPhotosAdapter(Context context)
        {
            super(Integer.MAX_VALUE);
            mOpenPhotoApi = Preferences.getApi(getActivity());
            mContext = context;
            loadFirstPage();
        }

        @Override
        public long getItemId(int position)
        {
            return ((Photo) getItem(position)).getId().hashCode();
        }

        @Override
        public View getView(final Photo photo, View convertView,
                ViewGroup parent)
        {
            if (convertView == null)
            {
                convertView = mInflater.inflate(
                        R.layout.list_item_newest_photos, parent, false);
            }

            // load the image in another thread
            ImageView photoView =
                    (ImageView) convertView.findViewById(R.id.newest_image);
            photoView.setTag(photo.getUrl("700x650xCR"));
            Drawable dr =
                    iw.loadImage(this, photoView);
            photoView.setImageDrawable(dr);

            // set title or file's name
            if (photo.getTitle() != null && photo.getTitle().trim().length()
                    > 0)
            {
                ((TextView) convertView.findViewById(R.id.newest_title))
                        .setText
                        (photo.getTitle());
            } else
            {
                ((TextView) convertView.findViewById(R.id.newest_title))
                        .setText(photo
                                .getFilenameOriginal());
            }

            /*
             * set the date
             */
            Resources res = getResources();
            String text = null;

            long milliseconds = new Date().getTime()
                    - photo.getDateTaken().getTime();
            long days = milliseconds / (24 * 60 * 60 * 1000);

            if (days >= 2)
            {
                if (days > 365)
                {
                    // show in years
                    text = days / 365 == 1 ? String
                            .format(
                                    res.getString(R.string.newest_this_photo_was_taken),
                                    days / 365,
                                    res.getString(R.string.year))
                            :
                            String.format(
                                    res.getString(R.string.newest_this_photo_was_taken),
                                    days / 365, res.getString(R.string.years));
                } else
                {
                    // lets show in days
                    text = String
                            .format(res
                                    .getString(R.string.newest_this_photo_was_taken),
                                    days,
                                    res.getString(R.string.days));
                }
            } else
            {
                // lets show in hours
                Long hours = days * 24;
                if (hours < 1)
                {
                    text = String
                            .format(res
                                    .getString(R.string.newest_this_photo_was_taken_less_one_hour));
                } else
                {
                    if (hours == 1)
                    {
                        text = String
                                .format(res
                                        .getString(R.string.newest_this_photo_was_taken),
                                        1, res.getString(R.string.hour));
                    } else
                    {
                        text = String
                                .format(res
                                        .getString(R.string.newest_this_photo_was_taken),
                                        hours, res.getString(R.string.hours));
                    }
                }
            }

            // set the correct text in the textview
            ((TextView) convertView.findViewById(R.id.newest_date))
                    .setText(text);

            // tags
            List<String> tags = photo.getTags();
            if (tags != null)
            {
                ViewGroup tagsView = (ViewGroup) convertView
                        .findViewById(R.id.newest_tag_layout);
                tagsView.removeAllViews();
                for (String tag : tags)
                {
                    Button tagBtn = (Button) mInflater.inflate(
                            R.layout.tag_btn, tagsView, false);
                    tagBtn.setText(tag);
                    tagsView.addView(tagBtn);
                }
            }

            View privateButton = convertView.findViewById(R.id.button_private);
            privateButton.setVisibility(photo.isPrivate() ? View.VISIBLE
                    : View.INVISIBLE);

            ImageView geoButton = (ImageView) convertView
                    .findViewById(R.id.geo_button);
            if (photo.getLongitude() != null &&
                    photo.getLatitude() != null &&
                    photo.getLongitude().length() != 0
                    && photo.getLatitude().length() != 0)
            {
                geoButton.setImageResource(R.drawable.button_location_share);
                geoButton.setTag(photo);
                geoButton.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        Photo photo = (Photo) view.getTag();
                        Uri uri = Uri.parse("geo:" + photo.getLatitude() + ","
                                + photo.getLongitude());
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        try
                        {
                            startActivity(intent);
                        } catch (Exception e)
                        {
                            Log.e(TAG, "Could not use Intent to open maps", e);
                        }
                    }
                });
            } else
            {
                geoButton.setImageResource(R.drawable.button_nolocation_share);
            }
            final ImageView shareButton = (ImageView) convertView
                    .findViewById(R.id.share_button);
            registerForContextMenu(shareButton);
            shareButton.setOnClickListener(new OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    activePhoto = photo;
                    getActivity().openContextMenu(shareButton);
                }
            });
            return convertView;
        }

        @Override
        public LoadResponse loadItems(int page)
        {
            if (checkOnline() && Preferences.isLoggedIn(mContext))
            {
                try
                {
                    PhotosResponse response = mOpenPhotoApi
                            .getNewestPhotos(new Paging(page, 25));
                    return new LoadResponse(response.getPhotos(), false);
                } catch (Exception e)
                {
                    Log.e(TAG, "Could not load next photos in list", e);
                    Map<String, String> extraData = new HashMap<String, String>();
                    extraData
                            .put("message",
                                    "Could not load next photos in list for HomeFragment");
                    BugSenseHandler.log(TAG, extraData, e);
                    alert("Could not load next photos in list");
                }
            }
            return new LoadResponse(null, false);
        }

        @Override
        protected void onStartLoading()
        {
            loadingControl.startLoading();
        }

        @Override
        protected void onStoppedLoading()
        {
            loadingControl.stopLoading();
        }
    }

}
