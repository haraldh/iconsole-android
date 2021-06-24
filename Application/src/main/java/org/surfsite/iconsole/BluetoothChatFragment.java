/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.surfsite.iconsole;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {
    static class MyInnerHandler extends Handler{
        WeakReference<BluetoothChatFragment> mFrag;

        MyInnerHandler(BluetoothChatFragment aFragment) {
            mFrag = new WeakReference<BluetoothChatFragment>(aFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            BluetoothChatFragment theFrag = mFrag.get();
            if (theFrag == null) {
                return;
            }
            FragmentActivity activity = theFrag.getActivity();

            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            theFrag.setStatus(theFrag.getString(R.string.title_connected_to, theFrag.mConnectedDeviceName));
                            //mConversationArrayAdapter.clear();
                            theFrag.mStartButton.setEnabled(true);
                            theFrag.mStopButton.setEnabled(true);
                            theFrag.mDisconnectButton.setEnabled(true);
                            theFrag.mLevel.setEnabled(true);
                            theFrag.mLevel.setValue(1);
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            theFrag.setStatus(R.string.title_connecting);
                            theFrag.mStartButton.setEnabled(false);
                            theFrag.mStopButton.setEnabled(false);
                            theFrag.mDisconnectButton.setEnabled(false);
                            theFrag.mLevel.setEnabled(false);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                        case BluetoothChatService.STATE_NONE:
                            theFrag.setStatus(R.string.title_not_connected);
                            theFrag.mStartButton.setEnabled(false);
                            theFrag.mStopButton.setEnabled(false);
                            theFrag.mDisconnectButton.setEnabled(false);
                            theFrag.mLevel.setEnabled(false);
                            break;
                    }
                    break;
                case Constants.MESSAGE_DATA:
                    if (!(msg.obj instanceof IConsole.Data))
                        return;
                    IConsole.Data data = (IConsole.Data) msg.obj;
                    theFrag.mChannelService.setSpeed(data.mSpeed10 / 10.0);
                    theFrag.mChannelService.setPower(data.mPower10 / 10);
                    theFrag.mChannelService.setCadence(data.mRPM);

                    theFrag.mSpeedText.setText(String.format("% 3.1f", data.mSpeed10 / 10.0));
                    theFrag.mPowerText.setText(String.format("% 3.1f", data.mPower10 / 10.0));
                    theFrag.mRPMText.setText(String.format("%d", data.mRPM));
                    theFrag.mDistanceText.setText(String.format("% 3.1f", data.mDistance10 / 10.0));
                    theFrag.mCaloriesText.setText(String.format("% 3d", data.mCalories));
                    theFrag.mHFText.setText(String.format("%d", data.mHF));
                    theFrag.mTimeText.setText(String.format("%s", data.getTimeStr()));
                    //mLevel.setValue(data.mLevel);
                    break;
                case Constants.MESSAGE_WRITE:
                    //byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    //String writeMessage = new String(writeBuf);
                    //mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    //byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    //String readMessage = new String(readBuf, 0, msg.arg1);
                    //mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    theFrag.mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + theFrag.mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    }

    private final Handler mHandler = new MyInnerHandler(this);

    private static final String TAG = "BluetoothChatFragment";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    //private ListView mConversationView;
    private Button mStartButton;
    private Button mStopButton;
    private Button mDisconnectButton;
    private NumberPicker mLevel;
    private TextView mSpeedText;
    private TextView mPowerText;
    private TextView mRPMText;
    private TextView mDistanceText;
    private TextView mCaloriesText;
    private TextView mHFText;
    private TextView mTimeText;
    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     private ArrayAdapter<String> mConversationArrayAdapter;
     */

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;
    private boolean mIsBound;
    private ChannelService.ChannelServiceComm mChannelService;
    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private boolean mChannelServiceBound = false;
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mChatService = ((BluetoothChatService.BluetoothChatServiceI) service).getService();
            ((BluetoothChatService.BluetoothChatServiceI) service).setHandler(mHandler);
            Log.d(TAG, "onServiceConnected()");
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mChatService = null;

        }
    };
    private ServiceConnection mChannelServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder serviceBinder) {
            Log.v(TAG, "mChannelServiceConnection.onServiceConnected...");

            mChannelService = (ChannelService.ChannelServiceComm) serviceBinder;


            Log.v(TAG, "...mChannelServiceConnection.onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.v(TAG, "mChannelServiceConnection.onServiceDisconnected...");

            // Clearing and disabling when disconnecting from ChannelService
            mChannelService = null;

            Log.v(TAG, "...mChannelServiceConnection.onServiceDisconnected");
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (null == mBluetoothAdapter || !mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
        if (!mChannelServiceBound) doBindChannelService();

    }

    @Override
    public void onDestroy() {
        if (mChatService != null) {
            mChatService.stopBT();
        }
        Log.d(TAG, "onDestroy()");
        doUnbindService();
        doUnbindChannelService();
        mChannelServiceConnection = null;

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.startBT();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        //mConversationView = (ListView) view.findViewById(R.id.in);
        mStartButton = (Button) view.findViewById(R.id.button_start);
        mStopButton = (Button) view.findViewById(R.id.button_stop);
        mDisconnectButton = (Button) view.findViewById(R.id.button_disconnect);
        mLevel = (NumberPicker) view.findViewById(R.id.Level);
        mLevel.setMaxValue(32);
        mLevel.setMinValue(1);
        mLevel.setValue(1);
        mLevel.setWrapSelectorWheel(false);
        mLevel.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        mSpeedText = (TextView) view.findViewById(R.id.Speed);
        mPowerText = (TextView) view.findViewById(R.id.Power);
        mRPMText = (TextView) view.findViewById(R.id.RPM);
        mDistanceText = (TextView) view.findViewById(R.id.Distance);
        mCaloriesText = (TextView) view.findViewById(R.id.Calories);
        mHFText = (TextView) view.findViewById(R.id.Heart);
        mTimeText = (TextView) view.findViewById(R.id.Time);
    }

    void doBindService() {
        Log.d(TAG, "doBindService()");

        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        getActivity().bindService(new Intent(getActivity(), BluetoothChatService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            // Detach our existing connection.
            getActivity().unbindService(mConnection);
            mIsBound = false;
        }
    }

    private void doBindChannelService() {
        Log.v(TAG, "doBindChannelService...");

        // Binds to ChannelService. ChannelService binds and manages connection between the
        // app and the ANT Radio Service
        mChannelServiceBound = getActivity().bindService(new Intent(getActivity(), ChannelService.class), mChannelServiceConnection, Context.BIND_AUTO_CREATE);

        if (!mChannelServiceBound)   //If the bind returns false, run the unbind method to update the GUI
            doUnbindChannelService();

        Log.i(TAG, "  Channel Service binding = " + mChannelServiceBound);

        Log.v(TAG, "...doBindChannelService");
    }

    private void doUnbindChannelService() {
        Log.v(TAG, "doUnbindChannelService...");

        if (mChannelServiceBound) {
            getActivity().unbindService(mChannelServiceConnection);

            mChannelServiceBound = false;
        }

        Log.v(TAG, "...doUnbindChannelService");
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        if (!mIsBound)
            doBindService();

        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLevel.setValue(1);
                if (mChatService != null)
                    mChatService.startIConsole();
            }
        });

        mStopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLevel.setValue(1);
                if (mChatService != null)
                    mChatService.stopIConsole();
            }
        });

        mDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLevel.setValue(1);
                if (mChatService != null)
                    mChatService.stopBT();
            }
        });

        mStartButton.setEnabled(false);
        mStopButton.setEnabled(false);
        mDisconnectButton.setEnabled(false);
        mLevel.setEnabled(false);
        mLevel.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(NumberPicker p, int oldval, int newval) {
                //Log.e(TAG, "setLevel");
                if (mChatService != null) {
                    if (!mChatService.setLevel(newval))
                        Log.e(TAG, "setLevel failed");

                }
            }
        });
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        if (mChatService == null)
            return;

        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        mChatService.startIConsole();
    }

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        if (mChatService != null)
            mChatService.connect(device);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
        }
        return false;
    }

}
