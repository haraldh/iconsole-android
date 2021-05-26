/*
 * Copyright 2012 Dynastream Innovations Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.surfsite.iconsole;

import com.dsi.ant.AntService;
import com.dsi.ant.channel.AntChannel;
import com.dsi.ant.channel.AntChannelProvider;
import com.dsi.ant.channel.ChannelNotAvailableException;
import com.dsi.ant.channel.PredefinedNetwork;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;

public class ChannelService extends Service {
    private static final String TAG = "ChannelService";

    private boolean mAntRadioServiceBound;
    private AntService mAntRadioService = null;
    private AntChannelProvider mAntChannelProvider = null;
    private boolean mAllowAddChannel = false;
    PowerChannelController powerChannelController = null;
    SpeedChannelController speedChannelController = null;

    private ServiceConnection mAntRadioServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // Must pass in the received IBinder object to correctly construct an AntService object
            mAntRadioService = new AntService(service);

            try {
                // Getting a channel provider in order to acquire channels
                mAntChannelProvider = mAntRadioService.getChannelProvider();

                // Initial check for number of channels available
                boolean mChannelAvailable = mAntChannelProvider.getNumChannelsAvailable() > 0;
                // Initial check for if legacy interface is in use. If the
                // legacy interface is in use, applications can free the ANT
                // radio by attempting to acquire a channel.
                boolean legacyInterfaceInUse = mAntChannelProvider.isLegacyInterfaceInUse();

                // If there are channels OR legacy interface in use, allow adding channels
                if (mChannelAvailable || legacyInterfaceInUse) {
                    mAllowAddChannel = true;
                } else {
                    // If no channels available AND legacy interface is not in use, disallow adding channels
                    mAllowAddChannel = false;
                }


            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            die("Binder Died");

            mAntChannelProvider = null;
            mAntRadioService = null;

            mAllowAddChannel = false;
        }

    };

    /**
     * The interface used to communicate with the ChannelService
     */
    public class ChannelServiceComm extends Binder {

        void setSpeed(double speed) {
            if (null != speedChannelController) {
                speedChannelController.speed = speed;
            }
        }

        void setPower(int power) {
            if (null != powerChannelController) {
                powerChannelController.power = power;
            }
        }

        void setCadence(int cadence) {
            if (null != powerChannelController) {
                powerChannelController.cadence = cadence;
            }
        }

        /**
         * Closes all channels currently added.
         */
        void clearAllChannels() {
            closeAllChannels();
        }
        }

    public void openAllChannels() throws ChannelNotAvailableException {
            powerChannelController = new PowerChannelController(acquireChannel());
            speedChannelController = new SpeedChannelController(acquireChannel());
    }

    private void closeAllChannels() {
            if (powerChannelController != null)
            powerChannelController.close();
            if (speedChannelController != null)
            speedChannelController.close();
            powerChannelController = null;
            speedChannelController = null;
    }

    AntChannel acquireChannel() throws ChannelNotAvailableException {
        AntChannel mAntChannel = null;
        if (null != mAntChannelProvider) {
            try {
                /*
                 * If applications require a channel with specific capabilities
                 * (event buffering, background scanning etc.), a Capabilities
                 * object should be created and then the specific capabilities
                 * required set to true. Applications can specify both required
                 * and desired Capabilities with both being passed in
                 * acquireChannel(context, PredefinedNetwork,
                 * requiredCapabilities, desiredCapabilities).
                 */
                mAntChannel = mAntChannelProvider.acquireChannel(this, PredefinedNetwork.ANT_PLUS_1);
                /*
                NetworkKey mNK = new NetworkKey(new byte[] { (byte)0xb9, (byte)0xa5, (byte)0x21, (byte)0xfb,
                                                             (byte)0xbd, (byte)0x72, (byte)0xc3, (byte)0x45 });
                Log.v(TAG, mNK.toString());
                mAntChannel = mAntChannelProvider.acquireChannelOnPrivateNetwork(this, mNK);
                */
            } catch (RemoteException e) {
                die("ACP Remote Ex");
            }
        }
        return mAntChannel;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return new ChannelServiceComm();
    }

    /**
     * Receives AntChannelProvider state changes being sent from ANT Radio Service
     */
    private final BroadcastReceiver mChannelProviderStateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED.equals(intent.getAction())) {
                boolean update = false;
                // Retrieving the data contained in the intent
                int numChannels = intent.getIntExtra(AntChannelProvider.NUM_CHANNELS_AVAILABLE, 0);
                boolean legacyInterfaceInUse = intent.getBooleanExtra(AntChannelProvider.LEGACY_INTERFACE_IN_USE, false);

                if (mAllowAddChannel) {
                    // Was a acquire channel allowed
                    // If no channels available AND legacy interface is not in use, disallow acquiring of channels
                    if (0 == numChannels && !legacyInterfaceInUse) {
                        mAllowAddChannel = false;
                        update = true;
                        closeAllChannels();
                    }
                } else {
                    // Acquire channels not allowed
                    // If there are channels OR legacy interface in use, allow acquiring of channels
                    if (numChannels > 0 || legacyInterfaceInUse) {
                        mAllowAddChannel = true;
                        update = true;
                        try {
                            openAllChannels();
                        } catch (ChannelNotAvailableException exception) {
                            Log.e(TAG, "Channel not available!!");
                        }
                    }
                }
            }
        }
    };

    private void doBindAntRadioService() {
        if (BuildConfig.DEBUG) Log.v(TAG, "doBindAntRadioService");

        // Start listing for channel available intents
        registerReceiver(mChannelProviderStateChangedReceiver, new IntentFilter(AntChannelProvider.ACTION_CHANNEL_PROVIDER_STATE_CHANGED));

        // Creating the intent and calling context.bindService() is handled by
        // the static bindService() method in AntService
        mAntRadioServiceBound = AntService.bindService(this, mAntRadioServiceConnection);
    }

    private void doUnbindAntRadioService() {
        if (BuildConfig.DEBUG) Log.v(TAG, "doUnbindAntRadioService");

        // Stop listing for channel available intents
        try {
            unregisterReceiver(mChannelProviderStateChangedReceiver);
        } catch (IllegalArgumentException exception) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Attempting to unregister a never registered Channel Provider State Changed receiver.");
        }

        if (mAntRadioServiceBound) {
            try {
                unbindService(mAntRadioServiceConnection);
            } catch (IllegalArgumentException e) {
                // Not bound, that's what we want anyway
            }

            mAntRadioServiceBound = false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAntRadioServiceBound = false;

        doBindAntRadioService();

    }

    @Override
    public void onDestroy() {
        closeAllChannels();

        doUnbindAntRadioService();
        mAntChannelProvider = null;

        super.onDestroy();
    }

    static void die(String error) {
        Log.e(TAG, "DIE: " + error);
    }

}
