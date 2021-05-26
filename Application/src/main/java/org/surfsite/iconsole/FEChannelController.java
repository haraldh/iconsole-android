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

import android.os.RemoteException;
import android.util.Log;

import com.dsi.ant.channel.AntChannel;
import com.dsi.ant.channel.AntCommandFailedException;
import com.dsi.ant.channel.Capabilities;
import com.dsi.ant.channel.IAntChannelEventHandler;
import com.dsi.ant.message.ChannelId;
import com.dsi.ant.message.ChannelType;
import com.dsi.ant.message.EventCode;
import com.dsi.ant.message.fromant.AcknowledgedDataMessage;
import com.dsi.ant.message.fromant.ChannelEventMessage;
import com.dsi.ant.message.fromant.MessageFromAntType;
import com.dsi.ant.message.ipc.AntMessageParcel;

import java.util.Random;

public class FEChannelController {
    public static final int FE_SENSOR_ID = 0x9e3d4b67;
    // The device type and transmission type to be part of the channel ID message
    private static final int CHANNEL_FE_DEVICE_TYPE = 0x11;
    private static final int CHANNEL_FE_TRANSMISSION_TYPE = 5;
    // The period and frequency values the channel will be configured to
    private static final int CHANNEL_POWER_PERIOD = 8182; // 1 Hz
    private static final int CHANNEL_POWER_FREQUENCY = 57;
    private static final String TAG = FEChannelController.class.getSimpleName();
    private static Random randGen = new Random();
    int power = 0;
    int cadence = 0;
    private AntChannel mAntChannel;
    private ChannelEventCallback mChannelEventCallback = new ChannelEventCallback();
    private boolean mIsOpen;

    public FEChannelController(AntChannel antChannel) {
        mAntChannel = antChannel;
        openChannel();
    }

    boolean openChannel() {
        if (null != mAntChannel) {
            if (mIsOpen) {
                Log.w(TAG, "Channel was already open");
            } else {
                // Channel ID message contains device number, type and transmission type. In
                // order for master (TX) channels and slave (RX) channels to connect, they
                // must have the same channel ID, or wildcard (0) is used.
                ChannelId channelId = new ChannelId(FE_SENSOR_ID & 0xFFFF,
                        CHANNEL_FE_DEVICE_TYPE, CHANNEL_FE_TRANSMISSION_TYPE);

                try {
                    // Setting the channel event handler so that we can receive messages from ANT
                    mAntChannel.setChannelEventHandler(mChannelEventCallback);

                    // Performs channel assignment by assigning the type to the channel. Additional
                    // features (such as, background scanning and frequency agility) can be enabled
                    // by passing an ExtendedAssignment object to assign(ChannelType, ExtendedAssignment).
                    mAntChannel.assign(ChannelType.BIDIRECTIONAL_MASTER);

                    /*
                     * Configures the channel ID, messaging period and rf frequency after assigning,
                     * then opening the channel.
                     *
                     * For any additional ANT features such as proximity search or background scanning, refer to
                     * the ANT Protocol Doc found at:
                     * http://www.thisisant.com/resources/ant-message-protocol-and-usage/
                     */
                    mAntChannel.setChannelId(channelId);
                    mAntChannel.setPeriod(CHANNEL_POWER_PERIOD);
                    mAntChannel.setRfFrequency(CHANNEL_POWER_FREQUENCY);
                    mAntChannel.open();
                    mIsOpen = true;

                    Log.d(TAG, "Opened channel with device number: " + FE_SENSOR_ID);
                } catch (RemoteException e) {
                    channelError(e);
                } catch (AntCommandFailedException e) {
                    // This will release, and therefore unassign if required
                    channelError("Open failed", e);
                }
            }
        } else {
            Log.w(TAG, "No channel available");
        }

        return mIsOpen;
    }


    void channelError(RemoteException e) {
        String logString = "Remote service communication failed.";

        Log.e(TAG, logString);

    }

    void channelError(String error, AntCommandFailedException e) {
        StringBuilder logString;

        if (e.getResponseMessage() != null) {
            String initiatingMessageId = "0x" + Integer.toHexString(
                    e.getResponseMessage().getInitiatingMessageId());
            String rawResponseCode = "0x" + Integer.toHexString(
                    e.getResponseMessage().getRawResponseCode());

            logString = new StringBuilder(error)
                    .append(". Command ")
                    .append(initiatingMessageId)
                    .append(" failed with code ")
                    .append(rawResponseCode);
        } else {
            String attemptedMessageId = "0x" + Integer.toHexString(
                    e.getAttemptedMessageType().getMessageId());
            String failureReason = e.getFailureReason().toString();

            logString = new StringBuilder(error)
                    .append(". Command ")
                    .append(attemptedMessageId)
                    .append(" failed with reason ")
                    .append(failureReason);
        }

        Log.e(TAG, logString.toString());

        mAntChannel.release();
    }

    public void close() {
        // TODO kill all our resources
        if (null != mAntChannel) {
            mIsOpen = false;

            // Releasing the channel to make it available for others.
            // After releasing, the AntChannel instance cannot be reused.
            mAntChannel.release();
            mAntChannel = null;
        }

        Log.e(TAG, "Channel Closed");
    }

    /**
     * Implements the Channel Event Handler Interface so that messages can be
     * received and channel death events can be handled.
     */
    public class ChannelEventCallback implements IAntChannelEventHandler {

        int cnt = 0;
        int eventCount = 0;
        int cumulativePower = 0;

        @Override
        public void onChannelDeath() {
            // Display channel death message when channel dies
            Log.e(TAG, "Channel Death");
        }

        @Override
        public void onReceiveMessage(MessageFromAntType messageType, AntMessageParcel antParcel) {
            Log.d(TAG, "Rx: " + antParcel);
            Log.d(TAG, "Message Type: " + messageType);
            byte[] payload = new byte[8];

            byte fe_state = 0; // RESERVED
            fe_state = 1; // ASLEEP
            fe_state = 2; // READY
            fe_state = 3; // IN_USE
            fe_state = 4; // FINISHED / PAUSE

            // Switching on message type to handle different types of messages
            switch (messageType) {
                // If data message, construct from parcel and update channel data
                case BROADCAST_DATA:
                    // Rx Data
                    //updateData(new BroadcastDataMessage(antParcel).getPayload());
                    break;
                case ACKNOWLEDGED_DATA:
                    // Rx Data
                    //updateData(new AcknowledgedDataMessage(antParcel).getPayload());
                    payload = new AcknowledgedDataMessage(antParcel).getPayload();
                    Log.d(TAG, "AcknowledgedDataMessage: " + payload);

                    if ((payload[0] == 0) && (payload[1] == 1) && (payload[2] == (byte)0xAA)) {
                        payload[0] = (byte) 0x01;
                        payload[1] = (byte) 0xAC;
                        payload[2] = (byte) 0xFF;
                        payload[3] = (byte) 0xFF;
                        payload[4] = (byte) 0xFF;
                        payload[5] = (byte) 0xFF;
                        payload[6] = (byte) 0x00;
                        payload[7] = (byte) 0x00;
                        try {
                            // Setting the data to be broadcast on the next channel period
                            mAntChannel.setBroadcastData(payload);
                        } catch (RemoteException e) {
                            channelError(e);
                        }
                    }
                    break;
                case CHANNEL_EVENT:
                    // Constructing channel event message from parcel
                    ChannelEventMessage eventMessage = new ChannelEventMessage(antParcel);
                    EventCode code = eventMessage.getEventCode();
                    Log.d(TAG, "Event Code: " + code);

                    // Switching on event code to handle the different types of channel events
                    switch (code) {
                        case TX:
                            cnt += 1;

                            if (cnt % 66 == 64) {
                                payload[0] = (byte) 0x50;
                                payload[1] = (byte) 0xFF;
                                payload[2] = (byte) 0xFF;
                                payload[3] = (byte) 0x01;
                                payload[4] = (byte) 0xFF;
                                payload[5] = (byte) 0x00;
                                payload[6] = (byte) 0x01;
                                payload[7] = (byte) 0x00;
                            } else if (cnt % 66 == 65) {
                                payload[0] = (byte) 0x51;
                                payload[1] = (byte) 0xFF;
                                payload[2] = (byte) 0xFF;
                                payload[3] = (byte) 0x01;
                                payload[4] = (byte) ((FE_SENSOR_ID) & 0xFF);
                                payload[5] = (byte) ((FE_SENSOR_ID >> 8) & 0xFF);
                                payload[6] = (byte) ((FE_SENSOR_ID >> 16) & 0xFF);
                                payload[7] = (byte) ((FE_SENSOR_ID >> 24) & 0xFF);
                            } else if (cnt % 2 == 0)  {
                                // PAGE 16
                                payload[0] = (byte) 0x10;
                                payload[1] = (byte) 25; // Equipment Type: 25 == Trainer / Stationary Bike
                                payload[2] = (byte) elapsedTime_in_0_25_seconds;
                                payload[3] = (byte) 0; // Distance traveled
                                payload[4] = (byte) 0xFF; // Speed LSB
                                payload[5] = (byte) 0xFF; // Speed MSB
                                payload[6] = (byte) 0xFF; // heart rate
                                payload[7] = (byte) (fe_state >> 4); // Capabilities 0:3 ; FE State Bit Field 4:7
                            } else {
                                // PAGE 25
                                eventCount = (eventCount + 1) & 0xFF;
                                cumulativePower = (cumulativePower + power) & 0xFFFF;
                                byte flags_bit_field;
                                flags_bit_field = 1; // too slow to achieve target power
                                flags_bit_field = 2; // too fast to achieve target power
                                flags_bit_field = 0; // operating at target power

                                payload[0] = (byte) 0x19;
                                payload[1] = (byte) eventCount;
                                payload[2] = (byte) cadence;
                                payload[3] = (byte) ((cumulativePower) & 0xFF);
                                payload[4] = (byte) ((cumulativePower >> 8) & 0xFF);
                                payload[5] = (byte) ((power) & 0xFF);
                                payload[6] = (byte) (((power >> 8) & 0xFF) | (trainerStatusBits >> 4));
                                payload[7] = (byte) ((flags_bit_field & 0xF) | ((fe_state >> 4) & 0xF0)); // Capabilities 0:3 ; FE State Bit Field 4:7
                            }
                        }

                            if (mIsOpen) {
                                try {
                                    // Setting the data to be broadcast on the next channel period
                                    mAntChannel.setBroadcastData(payload);
                                } catch (RemoteException e) {
                                    channelError(e);
                                }
                            }
                            break;
                        case CHANNEL_COLLISION:
                            cnt += 1;
                            break;
                        case RX_SEARCH_TIMEOUT:
                            // TODO May want to keep searching
                            Log.e(TAG, "No Device Found");
                            break;
                        case CHANNEL_CLOSED:
                        case RX_FAIL:
                        case RX_FAIL_GO_TO_SEARCH:
                        case TRANSFER_RX_FAILED:
                        case TRANSFER_TX_COMPLETED:
                        case TRANSFER_TX_FAILED:
                        case TRANSFER_TX_START:
                        case UNKNOWN:
                            // TODO More complex communication will need to handle these events
                            break;
                    }
                    break;
                case ANT_VERSION:
                case BURST_TRANSFER_DATA:
                case CAPABILITIES:
                case CHANNEL_ID:
                case CHANNEL_RESPONSE:
                case CHANNEL_STATUS:
                case SERIAL_NUMBER:
                case OTHER:
                    // TODO More complex communication will need to handle these message types
                    break;
            }
        }
    }
}
