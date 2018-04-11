package net.opendasharchive.openarchive;


import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.opendasharchive.openarchive.db.Media;
import net.opendasharchive.openarchive.db.MediaDeserializer;
import net.opendasharchive.openarchive.nearby.AyandaClient;
import net.opendasharchive.openarchive.nearby.AyandaServer;
import net.opendasharchive.openarchive.util.Globals;
import net.opendasharchive.openarchive.util.Utility;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import sintulabs.p2p.Ayanda;
import sintulabs.p2p.ILan;
import sintulabs.p2p.IWifiDirect;
import sintulabs.p2p.NearbyMedia;
import sintulabs.p2p.Neighbor;
import sintulabs.p2p.Server;


public class NearbyActivity extends AppCompatActivity {

    private final static String TAG = "Nearby";

    private TextView mTvNearbyLog;
    private DonutProgress mProgress;
    private LinearLayout mViewNearbyDevices;
    private boolean mIsServer = false;

    private Media mMedia = null;
    private NearbyMedia mNearbyMedia = null;

    private Ayanda mAyanda;
    private AyandaServer mAyandaServer;
    private HashMap<String,String> mPeers = new HashMap();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mAyanda = new Ayanda(this, null, mNearbyWifiLan, mNearbyWifiDirect);

        mTvNearbyLog = findViewById(R.id.tvnearbylog);
        mViewNearbyDevices = findViewById(R.id.nearbydevices);

        mProgress = findViewById(R.id.donut_progress);
        mProgress.setMax(100);

        mIsServer = getIntent().getBooleanExtra("isServer", false);

        if (mIsServer) {
            mProgress.setInnerBottomText(">>>>>>>>");
            try {
                startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            mProgress.setInnerBottomText("<<<<<<<<");

            getSupportActionBar().setTitle("Receiving...");

        }

        askForPermission("android.permission.BLUETOOTH", 1);
        askForPermission("android.permission.BLUETOOTH_ADMIN", 2);
        askForPermission("android.permission.ACCESS_COARSE_LOCATION", 3);
        askForPermission("android.permission.ACCESS_WIFI_STATE", 4);
        askForPermission("android.permission.CHANGE_WIFI_STATE", 5);
        askForPermission("android.permission.ACCESS_NETWORK_STATE", 6);
        askForPermission("android.permission.CHANGE_NETWORK_STATE", 7);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                cancelNearby();
                finish();
                break;
        }

        return true;
    }

    private boolean askForPermission(String permission, Integer requestCode) {
        if (ContextCompat.checkSelfPermission(NearbyActivity.this, permission) != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(NearbyActivity.this, permission)) {

                //This is called if user has denied the permission before
                //In this case I am just asking the permission again
                ActivityCompat.requestPermissions(NearbyActivity.this, new String[]{permission}, requestCode);

            } else {

                ActivityCompat.requestPermissions(NearbyActivity.this, new String[]{permission}, requestCode);
            }

            return true;
        }

        return false;

    }

    
      private synchronized void addMedia (final NearbyMedia nearbyMedia)
      {
          Media media = null;

          if (nearbyMedia.mMetadataJson == null) {
              media = new Media();
              media.setMimeType(nearbyMedia.mMimeType);
              media.setCreateDate(new Date());
              media.setUpdateDate(new Date());

              media.setTitle(nearbyMedia.mTitle);
          }
          else
          {
              GsonBuilder gsonBuilder = new GsonBuilder();
              gsonBuilder.registerTypeAdapter(Media.class, new MediaDeserializer());
              gsonBuilder.setDateFormat(DateFormat.FULL, DateFormat.FULL);
              Gson gson = gsonBuilder.create();
              media = gson.fromJson(nearbyMedia.mMetadataJson, Media.class);
          }

          if (media.getMediaHash() == null)
              media.setMediaHash(nearbyMedia.mDigest);

              //set the local file path for both
              media.setOriginalFilePath(nearbyMedia.mUriMedia.toString());

              //need better way to check original file
              List<Media> results = Media.find(Media.class, "title = ? AND author = ?", media.title,media.author);

          if (results == null || results.isEmpty()) {

              //it is new!
              media.save();

              Snackbar snackbar = Snackbar
              .make(findViewById(R.id.main_nearby), media.getTitle(), Snackbar.LENGTH_LONG);

              final Long snackMediaId = media.getId();

            snackbar.setAction(getString(R.string.action_open), new View.OnClickListener() {

                  @Override public void onClick(View v) {


                      Intent reviewMediaIntent = new Intent(NearbyActivity.this, ReviewMediaActivity.class);
                      reviewMediaIntent.putExtra(Globals.EXTRA_CURRENT_MEDIA_ID, snackMediaId);
                      reviewMediaIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                      startActivity(reviewMediaIntent);
                  }
              });

              snackbar.show();
          }
          else
          {
              Snackbar snackbar = Snackbar
                      .make(findViewById(R.id.main_nearby), "Duplicate received: " + media.getTitle(), Snackbar.LENGTH_LONG);

          }

      }

    private void restartNearby() {
        mAyanda.lanDiscover();
    }

    private void cancelNearby() {

        if (mAyandaServer != null)
            mAyandaServer.stop();

        mAyanda.lanStopAnnouncement();
        mAyanda.lanStopDiscovery();

        //stop wifi p2p?
    }

    private void log(String msg) {
        if (mTvNearbyLog != null)
            mTvNearbyLog.setText(msg);

        Log.d("Nearby", msg);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAyanda.wdRegisterReceivers();
        restartNearby();
    }

    /* unregister the broadcast receiver */
    @Override
    protected void onPause() {
        super.onPause();
        mAyanda.wdUnregisterReceivers();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cancelNearby();
    }

    private void startServer() throws IOException {

        mNearbyMedia = new NearbyMedia();

        long currentMediaId = getIntent().getLongExtra(Globals.EXTRA_CURRENT_MEDIA_ID, -1);

        if (currentMediaId >= 0)
            mMedia = Media.findById(Media.class, currentMediaId);

        Uri uriMedia = Uri.parse(mMedia.getOriginalFilePath());
        mNearbyMedia.mUriMedia = uriMedia;

        InputStream is = getContentResolver().openInputStream(uriMedia);
        byte[] digest = Utility.getDigest(is);
        mNearbyMedia.mDigest = digest;

        String title = mMedia.getTitle();
        if (TextUtils.isEmpty(title))
            title = uriMedia.getLastPathSegment();

        Gson gson = new GsonBuilder()
                .setDateFormat(DateFormat.FULL, DateFormat.FULL).create();
        mNearbyMedia.mMetadataJson = gson.toJson(mMedia);

        mNearbyMedia.mTitle = title;
        mNearbyMedia.mMimeType = mMedia.getMimeType();

        getSupportActionBar().setTitle("Sharing: " + title);

        try {
            int defaultPort = 8080;
            mAyandaServer = new AyandaServer(this, defaultPort);
            mAyanda.setServer(mAyandaServer);

            mAyanda.wdShareFile(mNearbyMedia);
            mAyanda.lanShare(mNearbyMedia);


        } catch (IOException e) {
            Log.e(TAG,"error setting server and sharing file",e);
        }


    }

    private void addPeerToView(String peerName) {

        LinearLayout.LayoutParams imParams =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView tv = new TextView(this);
        tv.setLayoutParams(imParams);
        tv.setText(peerName);

        mViewNearbyDevices.addView(tv, imParams);


    }



    ILan mNearbyWifiLan = new ILan() {

        @Override
        public void deviceListChanged() {

            ArrayList<Ayanda.Device> devices = new ArrayList<Ayanda.Device>(mAyanda.lanGetDeviceList());

            for (Ayanda.Device device: devices)
            {
                if ((!TextUtils.isEmpty(device.getName()))
                        && (!mPeers.containsKey(device.getHost().toString()))) {

                    mPeers.put(device.getHost().toString(), device.getName());
                    addPeerToView("LAN: " + device.getName());
                }
            }

        }

        @Override
        public void transferComplete(Neighbor neighbor, NearbyMedia nearbyMedia) {

        }

        @Override
        public void transferProgress(Neighbor neighbor, File file, String s, String s1, long l, long l1) {

        }

        @Override
        public void serviceRegistered(String s) {

        }

        @Override
        public void serviceResolved(NsdServiceInfo serviceInfo) {

             // Connected to desired service, so now make socket connection to peer
             final Ayanda.Device device = new Ayanda.Device(serviceInfo);

             new Thread(new Runnable() {
                @Override public void run() {
                    AyandaClient client = new AyandaClient(NearbyActivity.this);
                    String serverHost = null; //device.getHost().getHostName() + ":" + 8080;

                    try {


                        InetAddress hostInet =InetAddress.getByName(device.getHost().getHostAddress());

                        byte [] addressBytes = hostInet.getAddress();

                       // Inet6Address dest6 = Inet6Address.getByAddress(Data.get(position).getHost().GetHostAddress(), addressBytes, NetworkInterface.getByInetAddress(hostInet));
                        Inet4Address dest4 = (Inet4Address) Inet4Address.getByAddress (device.getHost().getHostAddress(), addressBytes);
                        serverHost = dest4.getHostAddress() + ":" + 8080;

                        final String response = client.get(serverHost);

                    } catch (IOException e) {
                        Log.e(TAG,"error LAN get: " + e);
                        return;
                    }

                    try {
                        final NearbyMedia nMedia = client.getNearbyMedia(serverHost);

                        if (nMedia != null)
                            addMedia(nMedia);

                    } catch (IOException e) {
                        Log.e(TAG,"error LAN get: " + e);
                    }

                }
            }).start();

        }
    };

    IWifiDirect mNearbyWifiDirect = new IWifiDirect() {

        @Override
        public void onConnectedAsClient(final InetAddress groupOwnerAddress) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    AyandaClient client = new AyandaClient(NearbyActivity.this);
                    try {

                        final String response = client
                                .get(groupOwnerAddress.getHostAddress() + ":" + Integer.toString(8080));

                        //couldn't connect
                        return;


                    } catch (IOException e) {
                        Log.e(TAG,"nearby added",e);
                    }

                    try {
                        final NearbyMedia nearbyMedia = client.getNearbyMedia(groupOwnerAddress.getHostAddress() + ":" + Integer.toString(8080));

                        if (nearbyMedia != null)
                            addMedia (nearbyMedia);

                    } catch (IOException e) {
                        Log.e(TAG,"nearby added",e);
                    }

                    if (mMedia != null)
                    {
                        client.uploadFile(groupOwnerAddress.getHostAddress() + ":" + Integer.toString(8080),mNearbyMedia);
                    }

                }
            }).start();
        }

        @Override
        public void wifiP2pStateChangedAction(Intent intent) {

            Log.d(TAG, "wifiP2pStateChangedAction: " + intent.getAction() + ": " + intent.getData());

            String action = intent.getAction();
            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    // Wifi P2P is enabled
                    mAyanda.wdDiscover();
                    mNearbyWifiDirect.wifiP2pPeersChangedAction();

                } else {
                    // Wi-Fi P2P is not enabled
                }
            }


        }

        @Override
        public void wifiP2pPeersChangedAction() {

            ArrayList<WifiP2pDevice> devices = new ArrayList<WifiP2pDevice>(mAyanda.wdGetDevicesDiscovered());

            for (WifiP2pDevice device: devices)
            {
                if ((!TextUtils.isEmpty(device.deviceName))
                        && (!mPeers.containsKey(device.deviceAddress))) {

                    mPeers.put(device.deviceAddress, device.deviceName);
                    addPeerToView("Wifi: " + device.deviceName);

                }

                mAyanda.wdConnect(device);

            }
        }

        @Override
        public void wifiP2pConnectionChangedAction(Intent intent) {
            Log.d(TAG, "wifiP2pConnectionChangedAction: " + intent.getAction() + ": " + intent.getData());

        }

        @Override
        public void wifiP2pThisDeviceChangedAction(Intent intent) {
            Log.d(TAG, "wifiP2pThisDeviceChangedAction: " + intent.getAction() + ": " + intent.getData());

        }

        @Override
        public void onConnectedAsServer(Server server) {

        }


    };

}