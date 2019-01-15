package net.autogroup.cleanradio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import net.autogroup.cleanradio.data.DataRadioStation;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;


public class RadioDroidBrowser {
    private static final String MEDIA_ID_ROOT = "__ROOT__";
    private static final String MEDIA_ID_MUSICS_FAVORITE = "__FAVORITE__";
    private static final String MEDIA_ID_MUSICS_HISTORY = "__HISTORY__";
    private static final String MEDIA_ID_MUSICS_TOP = "__TOP__";
    private static final String MEDIA_ID_MUSICS_TOP_TAGS = "__TOP_TAGS__";

    private static final char LEAF_SEPARATOR = '|';

    private static final int IMAGE_LOAD_TIMEOUT_MS = 2000;

    private RadioDroidApp radioDroidApp;

    private Map<String, DataRadioStation> stationIdToStation = new HashMap<>();

    private static class RetrieveStationsIconAndSendResult extends AsyncTask<Void, Void, Void> {
        private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result;
        private List<DataRadioStation> stations;
        private WeakReference<Context> contextRef;

        private Map<String, Bitmap> stationIdToIcon = new HashMap<>();
        private CountDownLatch countDownLatch;

        // Picasso stores weak references to targets
        List<Target> imageLoadTargets = new ArrayList<>();

        RetrieveStationsIconAndSendResult(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result, List<DataRadioStation> stations, Context context) {
            this.result = result;
            this.stations = stations;
            this.contextRef = new WeakReference<>(context);
        }

        @Override
        protected void onPreExecute() {
            countDownLatch = new CountDownLatch(stations.size());

            for (final DataRadioStation station : stations) {
                Context context = contextRef.get();
                if (context == null) {
                    break;
                }

                Target imageLoadTarget = new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        stationIdToIcon.put(station.ID, bitmap);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {
                        Context context = contextRef.get();
                        if (context != null) {
                            Bitmap placeholderIcon = BitmapFactory.decodeResource(context.getResources(),
                                    R.drawable.ic_photo_black_24dp);
                            stationIdToIcon.put(station.ID, placeholderIcon);
                        }

                        countDownLatch.countDown();
                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                };
                imageLoadTargets.add(imageLoadTarget);

                Picasso.with(context).load(station.IconUrl).into(imageLoadTarget);
            }

            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                countDownLatch.await(IMAGE_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            return null;
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            Context context = contextRef.get();
            if (context != null) {
                for (Target target : imageLoadTargets) {
                    Picasso.with(context).cancelRequest(target);
                }
            }

            List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

            for (DataRadioStation station : stations) {
                Bitmap stationIcon = stationIdToIcon.get(station.ID);

                mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                        .setMediaId(MEDIA_ID_MUSICS_HISTORY + LEAF_SEPARATOR + station.ID)
                        .setTitle(station.Name)
                        .setIconBitmap(stationIcon)
                        .build(),
                        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            }

            result.sendResult(mediaItems);

            super.onPostExecute(aVoid);
        }
    }

    public RadioDroidBrowser(RadioDroidApp radioDroidApp) {
        this.radioDroidApp = radioDroidApp;
    }

    @Nullable
    public MediaBrowserServiceCompat.BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new MediaBrowserServiceCompat.BrowserRoot(MEDIA_ID_ROOT, null);
    }

    public void onLoadChildren(@NonNull String parentId, @NonNull MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> result) {
        Resources resources = radioDroidApp.getResources();
        if (MEDIA_ID_ROOT.equals(parentId)) {
            result.sendResult(createBrowsableMediaItemsForRoot(resources));
            return;
        }

        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();

        List<DataRadioStation> stations = null;

        switch (parentId) {
            case MEDIA_ID_MUSICS_FAVORITE: {
                stations = radioDroidApp.getFavouriteManager().getList();
                break;
            }
            case MEDIA_ID_MUSICS_HISTORY: {
                stations = radioDroidApp.getHistoryManager().getList();
                break;
            }
            case MEDIA_ID_MUSICS_TOP: {

                break;
            }
        }

        if (stations != null && !stations.isEmpty()) {
            stationIdToStation.clear();
            for (DataRadioStation station : stations) {
                stationIdToStation.put(station.ID, station);
            }
            result.detach();
            new RetrieveStationsIconAndSendResult(result, stations, radioDroidApp).execute();
        } else {
            result.sendResult(mediaItems);
        }

    }

    @Nullable
    public DataRadioStation getStationById(@NonNull String stationId) {
        return stationIdToStation.get(stationId);
    }

    private List<MediaBrowserCompat.MediaItem> createBrowsableMediaItemsForRoot(Resources resources) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_FAVORITE)
                .setTitle(resources.getString(R.string.nav_item_starred))
                .setIconUri(resourceToUri(resources, R.drawable.ic_star_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_HISTORY)
                .setTitle(resources.getString(R.string.nav_item_history))
                .setIconUri(resourceToUri(resources, R.drawable.ic_restore_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

        mediaItems.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(MEDIA_ID_MUSICS_TOP)
                .setTitle(resources.getString(R.string.action_top_click))
                .setIconUri(resourceToUri(resources, R.drawable.ic_restore_black_24dp))
                .build(),
                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        return mediaItems;
    }

    public static String stationIdFromMediaId(final String mediaId) {
        if (mediaId == null) {
            return "";
        }

        final int separatorIdx = mediaId.indexOf(LEAF_SEPARATOR);

        if (separatorIdx <= 0) {
            return "";
        }

        return mediaId.substring(separatorIdx + 1);
    }

    private static Uri resourceToUri(Resources resources, int resID) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                resources.getResourcePackageName(resID) + '/' +
                resources.getResourceTypeName(resID) + '/' +
                resources.getResourceEntryName(resID));
    }
}
