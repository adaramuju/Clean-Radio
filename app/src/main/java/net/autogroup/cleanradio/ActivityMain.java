package net.autogroup.cleanradio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.NavigationView;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.rustamg.filedialogs.FileDialog;
import com.rustamg.filedialogs.OpenFileDialog;
import com.rustamg.filedialogs.SaveFileDialog;

import net.autogroup.cleanradio.data.MPDServer;
import net.autogroup.cleanradio.interfaces.IFragmentRefreshable;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ActivityMain extends AppCompatActivity implements SearchView.OnQueryTextListener, IMPDClientStatusChange, NavigationView.OnNavigationItemSelectedListener, BottomNavigationView.OnNavigationItemSelectedListener, FileDialog.OnFileSelectedListener {

    public static final String EXTRA_SEARCH_TAG = "search_tag";

    public static final int LAUNCH_EQUALIZER_REQUEST = 1;

    public static final int FRAGMENT_FROM_BACKSTACK = 777;

    public static final String ACTION_SHOW_LOADING = "net.autogroup.cleanradio.show_loading";
    public static final String ACTION_HIDE_LOADING = "net.autogroup.cleanradio.hide_loading";

    private static final String TAG = "RadioDroid";

    private final String TAG_SEARCH_URL = "json/stations/bytagexact";
    private final String SAVE_LAST_MENU_ITEM = "LAST_MENU_ITEM";

    private SearchView mSearchView;

    DrawerLayout mDrawerLayout;
    NavigationView mNavigationView;
    BottomNavigationView mBottomNavigationView;
    FragmentManager mFragmentManager;
    ImageView img_toolbar;

    BroadcastReceiver broadcastReceiver;

    MenuItem menuItemSearch;
    MenuItem menuItemDelete;
    MenuItem menuItemAlarm;
    MenuItem menuItemSave;
    MenuItem menuItemLoad;

    private SharedPreferences sharedPref;

    private int selectedMenuItem;

    private boolean instanceStateWasSaved;

    private Date lastExitTry;

    @Override
    public void changed() {
        Handler mainHandler = new Handler(getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                invalidateOptionsMenu();
            }
        };
        mainHandler.post(myRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (sharedPref == null) {
            PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
            sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        }
        setTheme(Utils.getThemeResId(this));
        setContentView(R.layout.layout_main);
        MobileAds.initialize(this, "ca-app-pub-1851158852084319~7574742070");

        AdView mAdView = findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder().build();
        mAdView.loadAd(adRequest);

        try {
            File dir = new File(getFilesDir().getAbsolutePath());
            if (dir.isDirectory()) {
                String[] children = dir.list();
                for (String aChildren : children) {
                    if (BuildConfig.DEBUG) {
                        Log.d("MAIN", "delete file:" + aChildren);
                    }
                    try {
                        new File(dir, aChildren).delete();
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
        }

        final Toolbar myToolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        setSupportActionBar(myToolbar);

        PlayerServiceUtil.bind(this);
        setupBroadcastReceiver();

        selectedMenuItem = sharedPref.getInt("last_selectedMenuItem", -1);
        instanceStateWasSaved = savedInstanceState != null;
        mFragmentManager = getSupportFragmentManager();

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawerLayout);
        mNavigationView = (NavigationView) findViewById(R.id.my_navigation_view);
        mBottomNavigationView = (BottomNavigationView) findViewById(R.id.bottom_navigation);
        img_toolbar = (ImageView) findViewById(R.id.img_toolbar);

        //if (Utils.bottomNavigationEnabled(this)) {
        //    mBottomNavigationView.setOnNavigationItemSelectedListener(this);
            mNavigationView.setVisibility(View.GONE);
            mNavigationView.getLayoutParams().width = 0;
        //} else {
            //mNavigationView.setNavigationItemSelectedListener(this);
            mBottomNavigationView.setVisibility(View.GONE);

            //ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.app_name, R.string.app_name);
            //mDrawerLayout.addDrawerListener(mDrawerToggle);
            //mDrawerToggle.syncState();

            //getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            //getSupportActionBar().setHomeButtonEnabled(true);
        //}

        FragmentPlayer f = new FragmentPlayer();
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.playerView, f).commit();

        CastHandler.onCreate(this);

        setupStartUpFragment();
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem menuItem) {
        // If menuItem == null method was executed manually
        if (menuItem != null)
            selectedMenuItem = menuItem.getItemId();

        mDrawerLayout.closeDrawers();
        android.support.v4.app.Fragment f = null;
        String backStackTag = String.valueOf(selectedMenuItem);

        switch (selectedMenuItem) {
            case R.id.nav_item_stations:
                f = new FragmentTabs();
                break;
            case R.id.nav_item_starred:
                f = new FragmentStarred();
                break;
            case R.id.nav_item_history:
                f = new FragmentHistory();
                break;
            case R.id.nav_item_recordings:
                f = new FragmentRecordings();
                break;
            case R.id.nav_item_alarm:
                f = new FragmentAlarm();
                break;
            case R.id.nav_item_settings:
                f = new FragmentSettings();
                break;
            default:
        }

        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        if (Utils.bottomNavigationEnabled(this))
            fragmentTransaction.replace(R.id.containerView, f).commit();
        else
            fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit();

        // User selected a menuItem. Let's hide progressBar
        sendBroadcast(new Intent(ActivityMain.ACTION_HIDE_LOADING));
        invalidateOptionsMenu();
        checkMenuItems();

        return false;
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
        /*
        int backStackCount = mFragmentManager.getBackStackEntryCount();
        FragmentManager.BackStackEntry backStackEntry;

        if (backStackCount > 0) {
            // FRAGMENT_FROM_BACKSTACK value added as a backstack name for non-root fragments like Recordings, About, etc
            backStackEntry = mFragmentManager.getBackStackEntryAt(mFragmentManager.getBackStackEntryCount() - 1);
            int parsedId = Integer.parseInt(backStackEntry.getName());
            if (parsedId == FRAGMENT_FROM_BACKSTACK) {
                super.onBackPressed();
                invalidateOptionsMenu();
                return;
            }
        }

        // Don't support backstack with BottomNavigationView
        if (Utils.bottomNavigationEnabled(this)) {
            // I'm giving 3 seconds on making a choice
            if (lastExitTry != null && new Date().getTime() < lastExitTry.getTime() + 3 * 1000) {
                PlayerServiceUtil.shutdownService();
                finish();
            } else {
                Toast.makeText(this, R.string.alert_press_back_to_exit, Toast.LENGTH_SHORT).show();
                lastExitTry = new Date();
                return;
            }
        }

        if (backStackCount > 1) {
            backStackEntry = mFragmentManager.getBackStackEntryAt(mFragmentManager.getBackStackEntryCount() - 2);

            selectedMenuItem = Integer.parseInt(backStackEntry.getName());

            if (!Utils.bottomNavigationEnabled(this)) {
                mNavigationView.setCheckedItem(selectedMenuItem);
            }
            invalidateOptionsMenu();

        } else {
            finish();
            return;
        }
        super.onBackPressed();
        */
    }

    @Override
    protected void onNewIntent(Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        final String searchTag = extras.getString(EXTRA_SEARCH_TAG);
        if (searchTag != null) {
            try {
                String queryEncoded = URLEncoder.encode(searchTag, "utf-8");
                queryEncoded = queryEncoded.replace("+", "%20");
                Search(RadioBrowserServerManager.getWebserviceEndpoint(this, TAG_SEARCH_URL + "/" + queryEncoded + "?limit=20"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "on request permissions result:" + requestCode);
        }
        switch (requestCode) {
            case Utils.REQUEST_EXTERNAL_STORAGE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Fragment currentFragment = mFragmentManager.getFragments().get(mFragmentManager.getFragments().size() - 1);
                    if (currentFragment instanceof IFragmentRefreshable) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "REFRESH VIEW");
                        }
                        ((IFragmentRefreshable) currentFragment).Refresh();
                    }
                } else {
                    Toast toast = Toast.makeText(this, getResources().getString(R.string.error_record_needs_write), Toast.LENGTH_SHORT);
                    toast.show();
                }
                return;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PlayerServiceUtil.unBind(this);
        MPDClient.StopDiscovery();
        unregisterReceiver(broadcastReceiver);
    }

    @Override
    protected void onPause() {

        SharedPreferences.Editor ed = sharedPref.edit();
        ed.putInt("last_selectedMenuItem", selectedMenuItem);
        ed.apply();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "PAUSED");
        }

        super.onPause();

        if (!PlayerServiceUtil.isPlaying()) {
            PlayerServiceUtil.shutdownService();
        } else {
            CastHandler.onPause();
        }

        MPDClient.StopDiscovery();
        MPDClient.setStateChangeListener(null);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "RESUMED");
        }

        PlayerServiceUtil.bind(this);
        CastHandler.onResume();

        MPDClient.setStateChangeListener(this);
        MPDClient.StartDiscovery(new WeakReference<Context>(this));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        final Toolbar myToolbar = (Toolbar) findViewById(R.id.my_awesome_toolbar);
        menuItemAlarm = menu.findItem(R.id.action_set_alarm);
        menuItemSearch = menu.findItem(R.id.action_search);
        menuItemDelete = menu.findItem(R.id.action_delete);
        menuItemSave = menu.findItem(R.id.action_save);
        menuItemLoad = menu.findItem(R.id.action_load);
        mSearchView = (SearchView) MenuItemCompat.getActionView(menuItemSearch);
        mSearchView.setOnSearchClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                img_toolbar.setVisibility(View.GONE);
            }
        });
        mSearchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                img_toolbar.setVisibility(View.VISIBLE);
                return false;
            }
        });
        mSearchView.setOnQueryTextListener(this);
        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        EditText searchEditText = (EditText) mSearchView.findViewById(android.support.v7.appcompat.R.id.search_src_text);
        searchEditText.setTextColor(Color.WHITE);
        searchEditText.setHintTextColor(Color.WHITE);

        MenuItem menuItemMPDNok = menu.findItem(R.id.action_mpd_nok);
        MenuItem menuItemMPDOK = menu.findItem(R.id.action_mpd_ok);

        menuItemAlarm.setVisible(false);
        menuItemSearch.setVisible(false);
        menuItemDelete.setVisible(false);
        menuItemSave.setVisible(false);
        menuItemLoad.setVisible(false);
        menuItemMPDOK.setVisible(MPDClient.Discovered() && MPDClient.Connected());
        menuItemMPDNok.setVisible(MPDClient.Discovered() && !MPDClient.Connected());

        switch (selectedMenuItem) {
            case R.id.nav_item_stations: {
                //menuItemAlarm.setVisible(true);
                menuItemSearch.setVisible(true);
                myToolbar.setTitle("");
                break;
            }
            case R.id.nav_item_starred: {
                //menuItemAlarm.setVisible(true);
                menuItemSearch.setVisible(true);
                menuItemSave.setVisible(true);
                menuItemLoad.setVisible(true);

                RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
                if (radioDroidApp.getFavouriteManager().isEmpty()) {
                    menuItemDelete.setVisible(false);
                } else {
                    menuItemDelete.setVisible(true).setTitle(R.string.action_delete_favorites);
                }
                myToolbar.setTitle("");
                break;
            }
            case R.id.nav_item_history: {
                menuItemSearch.setVisible(true);

                RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
                if (!radioDroidApp.getHistoryManager().isEmpty()) {
                    menuItemDelete.setVisible(true).setTitle(R.string.action_delete_history);
                }
                myToolbar.setTitle("");
                break;
            }
            case R.id.nav_item_recordings: {
                myToolbar.setTitle("");
                break;
            }
            case R.id.nav_item_alarm: {
                myToolbar.setTitle("");
                break;
            }
            case R.id.nav_item_settings: {
                myToolbar.setTitle("");
                break;
            }
        }

        MenuItem mediaRouteMenuItem = CastHandler.getRouteItem(getApplicationContext(), menu);
        return true;
    }

    /*@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
            RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
            FavouriteManager favouriteManager = radioDroidApp.getFavouriteManager();
            favouriteManager.SaveM3U(filePath, "radiodroid.m3u");
        }
    }*/

    @Override
    public void onFileSelected(FileDialog dialog, File file) {
        try {
            Log.i("MAIN", "save to " + file.getParent() + "/" + file.getName());
            RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
            FavouriteManager favouriteManager = radioDroidApp.getFavouriteManager();

            if (dialog instanceof SaveFileDialog){
                favouriteManager.SaveM3U(file.getParent(), file.getName());
            }else if (dialog instanceof OpenFileDialog) {
                favouriteManager.LoadM3U(file.getParent(), file.getName());
            }
        }
        catch(Exception e){
            Log.e("MAIN",e.toString());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);  // OPEN DRAWER
                return true;
            case R.id.action_save:
                try{
                    if (Utils.verifyStoragePermissions(this)) {
                        SaveFileDialog dialog = new SaveFileDialog();
                        dialog.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(this));
                        Bundle args = new Bundle();
                        args.putString(FileDialog.EXTENSION, ".m3u"); // file extension is optional
                        dialog.setArguments(args);
                        dialog.show(getSupportFragmentManager(), SaveFileDialog.class.getName());
                    }
                }
                catch(Exception e){
                    Log.e("MAIN",e.toString());
                }

                return true;
            case R.id.action_load:
                try {
                    if (Utils.verifyStoragePermissions(this)) {
                        OpenFileDialog dialogOpen = new OpenFileDialog();
                        dialogOpen.setStyle(DialogFragment.STYLE_NO_TITLE, Utils.getThemeResId(this));
                        Bundle argsOpen = new Bundle();
                        argsOpen.putString(FileDialog.EXTENSION, ".m3u"); // file extension is optional
                        dialogOpen.setArguments(argsOpen);
                        dialogOpen.show(getSupportFragmentManager(), OpenFileDialog.class.getName());
                    }
                }
                catch(Exception e){
                    Log.e("MAIN",e.toString());
                }
                return true;
            case R.id.action_set_alarm:
                changeTimer();
                return true;
            case R.id.action_mpd_nok:
                selectMPDServer();
                return true;
            case R.id.action_mpd_ok:
                MPDClient.Disconnect();
                return true;
            case R.id.action_delete:
                if (selectedMenuItem == R.id.nav_item_history) {
                    new AlertDialog.Builder(this)
                            .setMessage(this.getString(R.string.alert_delete_history))
                            .setCancelable(true)
                            .setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
                                    HistoryManager historyManager = radioDroidApp.getHistoryManager();

                                    historyManager.clear();

                                    Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.notify_deleted_history), Toast.LENGTH_SHORT);
                                    toast.show();
                                    recreate();
                                }
                            })
                            .setNegativeButton(this.getString(R.string.no), null)
                            .show();
                }
                if (selectedMenuItem == R.id.nav_item_starred) {
                    new AlertDialog.Builder(this)
                            .setMessage(this.getString(R.string.alert_delete_favorites))
                            .setCancelable(true)
                            .setPositiveButton(this.getString(R.string.yes), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
                                    FavouriteManager favouriteManager = radioDroidApp.getFavouriteManager();

                                    favouriteManager.clear();

                                    Toast toast = Toast.makeText(getApplicationContext(), getString(R.string.notify_deleted_favorites), Toast.LENGTH_SHORT);
                                    toast.show();
                                    recreate();
                                }
                            })
                            .setNegativeButton(this.getString(R.string.no), null)
                            .show();
                }
                return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void setupStartUpFragment() {
        // This will restore fragment that was shown before activity was recreated
        if (instanceStateWasSaved) {
            invalidateOptionsMenu();
            checkMenuItems();
            return;
        }

        RadioDroidApp radioDroidApp = (RadioDroidApp) getApplication();
        HistoryManager hm = radioDroidApp.getHistoryManager();
        FavouriteManager fm = radioDroidApp.getFavouriteManager();
        selectMenuItem(R.id.nav_item_stations);
    }

    private void selectMenuItem(int itemId) {
        MenuItem item;
        if (Utils.bottomNavigationEnabled(this))
            item = mBottomNavigationView.getMenu().findItem(itemId);
        else
            item = mNavigationView.getMenu().findItem(itemId);

        if (item != null) {
            onNavigationItemSelected(item);
        } else {
            selectedMenuItem = R.id.nav_item_stations;
            onNavigationItemSelected(null);
        }
    }

    private void checkMenuItems() {
        if (mBottomNavigationView.getMenu().findItem(selectedMenuItem) != null)
            mBottomNavigationView.getMenu().findItem(selectedMenuItem).setChecked(true);

        if (mNavigationView.getMenu().findItem(selectedMenuItem) != null)
            mNavigationView.getMenu().findItem(selectedMenuItem).setChecked(true);
    }

    public void Search(String query) {
        Fragment currentFragment = mFragmentManager.getFragments().get(mFragmentManager.getFragments().size() - 1);
        if (currentFragment instanceof FragmentTabs) {
            ((FragmentTabs) currentFragment).Search(query);
        } else {
            String backStackTag = String.valueOf(R.id.nav_item_stations);
            FragmentTabs f = new FragmentTabs();
            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
            if (Utils.bottomNavigationEnabled(this)) {
                fragmentTransaction.replace(R.id.containerView, f).commit();
                mBottomNavigationView.getMenu().findItem(R.id.nav_item_stations).setChecked(true);
            } else {
                fragmentTransaction.replace(R.id.containerView, f).addToBackStack(backStackTag).commit();
                mNavigationView.getMenu().findItem(R.id.nav_item_stations).setChecked(true);
            }

            f.Search(query);
            selectedMenuItem = R.id.nav_item_stations;
            invalidateOptionsMenu();
        }

    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        String queryEncoded;
        try {
            img_toolbar.setVisibility(View.VISIBLE);
            mSearchView.setQuery("", false);
            mSearchView.clearFocus();
            mSearchView.setIconified(true);

            queryEncoded = URLEncoder.encode(query, "utf-8");
            queryEncoded = queryEncoded.replace("+", "%20");
            Search(RadioBrowserServerManager.getWebserviceEndpoint(this, "json/stations/byname/" + queryEncoded + "?limit=10"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (MPDClient.connected && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP))
            return true;

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        String mpd_hostname = "";
        int mpd_port = 0;
        for (MPDServer server : Utils.getMPDServers(this)) {
            if (server.selected) {
                mpd_hostname = server.hostname.trim();
                mpd_port = server.port;
                break;
            }
        }
        if (mpd_port == 0 || !MPDClient.connected)
            return super.onKeyUp(keyCode, event);

        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                MPDClient.SetVolume(mpd_hostname, mpd_port, -5);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                MPDClient.SetVolume(mpd_hostname, mpd_port, 5);
                return true;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void setupBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_HIDE_LOADING);
        filter.addAction(ACTION_SHOW_LOADING);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Intent mIntent = intent;
                if (mIntent.getAction().equals(ACTION_HIDE_LOADING))
                    hideLoadingIcon();
                else if (mIntent.getAction().equals(ACTION_SHOW_LOADING))
                    showLoadingIcon();
            }
        };
        registerReceiver(broadcastReceiver, filter);
    }

    // Loading listener
    private void showLoadingIcon() {
        findViewById(R.id.progressBarLoading).setVisibility(View.VISIBLE);
    }

    private void hideLoadingIcon() {
        findViewById(R.id.progressBarLoading).setVisibility(View.GONE);
    }

    private void changeTimer() {
        final AlertDialog.Builder seekDialog = new AlertDialog.Builder(this);
        View seekView = View.inflate(this, R.layout.layout_timer_chooser, null);

        seekDialog.setTitle(R.string.sleep_timer_title);
        seekDialog.setView(seekView);

        final TextView seekTextView = (TextView) seekView.findViewById(R.id.timerTextView);
        final SeekBar seekBar = (SeekBar) seekView.findViewById(R.id.timerSeekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                seekTextView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        long currentTimer = PlayerServiceUtil.getTimerSeconds() > 60 ? Math.abs(PlayerServiceUtil.getTimerSeconds() / 60) : 1;
        seekBar.setProgress((int) currentTimer);
        seekDialog.setPositiveButton(R.string.sleep_timer_apply, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PlayerServiceUtil.clearTimer();
                PlayerServiceUtil.addTimer(seekBar.getProgress() * 60);
            }
        });

        seekDialog.setNegativeButton(R.string.sleep_timer_clear, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                PlayerServiceUtil.clearTimer();
            }
        });

        seekDialog.create();
        seekDialog.show();
    }

    private void selectMPDServer() {
        final List<MPDServer> servers = Utils.getMPDServers(this);
        final List<MPDServer> connectedServers = new ArrayList<>();
        List<String> serversNames = new ArrayList<>();
        for (MPDServer server : servers) {
            server.selected = false;
            if (server.connected) {
                serversNames.add(server.name);
                connectedServers.add(server);
            }
        }
        if (connectedServers.size() == 1) {
            MPDServer selectedServer = servers.get(connectedServers.get(0).id);
            selectedServer.selected = true;
            Utils.saveMPDServers(servers, getApplicationContext());
            MPDClient.Connect();

            // Since we connected to one server but have many user should know server's name
            if (servers.size() > 1)
                Toast.makeText(this, getString(R.string.action_mpd_connected, selectedServer.name), Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setItems(serversNames.toArray(new String[serversNames.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        for (MPDServer server : servers) {
                            server.selected = false;
                        }
                        MPDServer selectedServer = servers.get(connectedServers.get(which).id);
                        selectedServer.selected = true;
                        Utils.saveMPDServers(servers, getApplicationContext());
                        MPDClient.Connect();
                    }
                })
                .setTitle(R.string.alert_select_mpd_server)
                .show();
    }

    public final Toolbar getToolbar() {
        return (Toolbar) findViewById(R.id.my_awesome_toolbar);
    }
}
