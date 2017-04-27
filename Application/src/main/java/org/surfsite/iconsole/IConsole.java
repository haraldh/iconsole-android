package org.surfsite.iconsole;

import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

/**
 * Created by harald on 25.04.17.
 */

class IConsole {
    private static final String TAG = "IConsole";

    private static final byte[] PING     = {(byte) 0xf0, (byte) 0xa0, (byte) 0x01, (byte) 0x01, (byte) 0x92 };
    private static final byte[] INIT_A0  = {(byte) 0xf0, (byte) 0xa0, 0x02, 0x02, (byte) 0x94};
    //private static final byte[] PONG     = {(byte) 0xf0, (byte) 0xb0, 0x01, 0x01, (byte) 0xa2};
    private static final byte[] STATUS   = {(byte) 0xf0, (byte) 0xa1, 0x01, 0x01, (byte) 0x93};
    private static final byte[] INIT_A3  = {(byte) 0xf0, (byte) 0xa3, 0x01, 0x01, 0x01, (byte) 0x96};
    private static final byte[] INIT_A4  = {(byte) 0xf0, (byte) 0xa4, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, (byte) 0xa0};
    private static final byte[] START    = {(byte) 0xf0, (byte) 0xa5, 0x01, 0x01, 0x02, (byte) 0x99};
    private static final byte[] STOP     = {(byte) 0xf0, (byte) 0xa5, 0x01, 0x01, 0x04, (byte) 0x9b};
    private static final byte[] READ     = {(byte) 0xf0, (byte) 0xa2, 0x01, 0x01, (byte) 0x94};
    private static final byte[] SETLEVEL = {(byte) 0xf0, (byte) 0xa6, 0x01, 0x01, 0x01, (byte)((0xf0+0xa6+3) & 0xFF)};

    private enum State {
        BEGIN,
        PING,
        A0,
        A1,
        A1_POST_PING,
        A3,
        A4,
        START,
        STOP,
        READ,
        SETLEVEL,
    }

    private State mCurrentState = State.BEGIN;
    private State mNextState = State.PING;
    private boolean mWaitAck = false;
    private int mSetLevel = 1;
    private long mTimesent = 0;
    private int mExpectLen = 0;
    private byte[] mExpectPacket;
    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private final DataListener mDataListener;
    private final DebugListener mDebugListener;

    IConsole(InputStream inputStream, OutputStream outputStream,
             @Nullable DataListener dataListener, @Nullable DebugListener debugListener) {
        this.mInputStream = inputStream;
        this.mOutputStream = outputStream;
        this.mDataListener = dataListener;
        this.mDebugListener = debugListener;
    }

    class Data implements java.io.Serializable {
        long mTime;         // in seconds
        int mSpeed10;
        int mRPM;
        int mDistance10;
        int mCalories;
        int mHF;
        int mPower10;
        int mLevel;

        Data(long mTime, int mSpeed10, int mRPM, int mDistance10, int mCalories, int mHF, int mPower10, int mLevel) {
            this.mTime = mTime;
            this.mSpeed10 = mSpeed10;
            this.mRPM = mRPM;
            this.mDistance10 = mDistance10;
            this.mCalories = mCalories;
            this.mHF = mHF;
            this.mPower10 = mPower10;
            this.mLevel = mLevel;
        }

        Data(byte[] bytes) {
            this.mTime = (((bytes[2]-1) * 24 + bytes[3]-1) * 60 +  bytes[4]-1) * 60 + bytes[5]-1 ;
            this.mSpeed10    = 100 * (bytes[ 6] - 1) + bytes[ 7] - 1;
            this.mRPM        = 100 * (bytes[ 8] - 1) + bytes[ 9] - 1;
            this.mDistance10 = 100 * (bytes[10] - 1) + bytes[11] - 1;
            this.mCalories   = 100 * (bytes[12] - 1) + bytes[13] - 1;
            this.mHF         = 100 * (bytes[14] - 1) + bytes[15] - 1;
            this.mPower10    = 100 * (bytes[16] - 1) + bytes[17] - 1;
            this.mLevel      = bytes[18] -1;
        }

        String getTimeStr() {
            long day, hour, min, sec;
            StringBuilder b = new StringBuilder();
            day = mTime / 60 / 60 / 24;
            if (day > 0)
                b.append(String.format(Locale.US, "%02d:", day));
            hour = (mTime % (60 * 60 * 24)) / 60 / 60;
            if (hour > 0)
                if (day > 0)
                    b.append(String.format(Locale.US, "%02d:", hour));
                else
                    b.append(String.format(Locale.US, "%d:", hour));
            min = (mTime % (60 * 60)) / 60;
            sec = mTime % 60;
            b.append(String.format(Locale.US, "%02d:%02d", min, sec));
            return b.toString();
        }

    }

    interface DataListener {
        void onData(Data data);
        void onError(Exception e);
    }

    interface DebugListener {
        void onRead(byte[] bytes);
        void onWrite(byte[] bytes);
    }

    boolean start() {
        synchronized (this) {
            this.mNextState = State.A0;
        }
        return true;
    }

    boolean stop() {
        synchronized (this) {
            this.mNextState = State.STOP;
        }
        return true;
    }

    boolean setLevel(int level) {
        synchronized (this) {
            if (mCurrentState != State.READ)
                return false;

            this.mNextState = State.SETLEVEL;
            this.mSetLevel = level;
        }
        return true;
    }

    private boolean send(byte[] packet, byte expect, int plen) throws IOException {
        long now = System.currentTimeMillis();

        if ((now - mTimesent) < ((mCurrentState == State.READ) ? 500 : 200)) {
            return false;
        }

        // Flush input stream
        try {
            //noinspection ResultOfMethodCallIgnored
            mInputStream.skip(mInputStream.available());
        } catch (IOException e) {
            ; // ignore
        }

        // Send packet
        mOutputStream.write(packet);

        if (null != mDebugListener)
            mDebugListener.onWrite(packet);
        //Log.d(TAG, "sent: " + byteArrayToHex(packet));

        mTimesent = System.currentTimeMillis();
        mExpectPacket = packet.clone();
        mExpectPacket[1] = expect;
        mExpectLen = plen;
        mWaitAck = true;
        return true;
    }

    private boolean send(byte[] packet) throws IOException {
        return send(packet, (byte)(0xb0 | (packet[1] & 0xF)), packet.length);
    }

    private boolean send(byte[] packet, int plen) throws IOException {
        return send(packet, (byte)(0xb0 | (packet[1] & 0xF)), plen);
    }

    private boolean send_level(int level) throws IOException {
        byte[] packet = SETLEVEL.clone();
        packet[4] = (byte) (packet[4] + level);
        packet[5] = (byte) ((packet[5] + level) & 0xFF);
        return send(packet);
    }

    private byte[] wait_ack() throws IOException, TimeoutException {
        byte[] buffer = new byte[mExpectLen];
        int bytes;

        long now = System.currentTimeMillis();

        if ((now - mTimesent) > 10000) {
            mWaitAck = false;
            return null;
        }

        if (mInputStream.available() < mExpectLen) {
            //Log.d(TAG, String.format(Locale.US, "Avail: %d   Expected: %d", mInputStream.available(), mExpectLen));
            return null;
        }

        bytes = mInputStream.read(buffer);

        if (null != mDebugListener) {
            mDebugListener.onRead(Arrays.copyOfRange(buffer, 0, bytes));
            //Log.d(TAG, "wait ack got: " + byteArrayToHex(Arrays.copyOfRange(buffer, 0, bytes)));
        }

        //Log.d(TAG, "wait ack checking");

        if (bytes != mExpectLen) {
            throw new IOException("Wrong number of bytes read. Expected " + mExpectLen + ", got " + bytes);
        }

        if (buffer[0] != mExpectPacket[0]) {
            throw new IOException("Byte 0 wrong. Expected " + String.format("%02x", mExpectPacket[0]) + ", got " + String.format("%02x", buffer[0]));
        }

        if (buffer[1] != mExpectPacket[1]) {
            throw new IOException("Byte 1 wrong. Expected " + String.format("%02x", mExpectPacket[1]) + ", got " + String.format("%02x", buffer[1]));
        }

        //Log.d(TAG, "wait ack success");
        mWaitAck = false;

        return buffer;
    }

    private boolean processIOSend() throws IOException {
        switch (mCurrentState) {
            case BEGIN:
                send(PING);
                break;
            case PING:
                send(PING);
                break;
            case A0:
                send(INIT_A0, (byte)0xb7, 6);
                break;
            case A1:
                send(STATUS, 6);
                break;
            case A1_POST_PING:
                send(PING);
                break;
            case A3:
                send(INIT_A3);
                break;
            case A4:
                send(INIT_A4);
                break;
            case START:
                send(START);
                break;
            case STOP:
                send(STOP);
                break;
            case READ:
                send(READ, 21);
                break;
            case SETLEVEL:
                send_level(mSetLevel);
                break;
        }
        return true;
    }

    private boolean processIOAck() throws IOException, TimeoutException {
        byte[] got = null;
        got = wait_ack();
        if (null == got) {
            return true;
        }

        //Log.d(TAG, "processIOAck next state");

        if(mCurrentState == State.READ)
            if (null != mDataListener)
                mDataListener.onData(new Data(got));

        mCurrentState = mNextState;
        switch (mNextState) {
            case BEGIN:
                mNextState = State.PING;
                break;
            case PING:
                mNextState = State.PING;
                break;
            case A0:
                mNextState = State.A1;
                break;
            case A1:
                mNextState = State.A1_POST_PING;
                break;
            case A1_POST_PING:
                mNextState = State.A3;
                break;
            case A3:
                mNextState = State.A4;
                break;
            case A4:
                mNextState = State.START;
                break;
            case START:
                mNextState = State.READ;
                break;
            case STOP:
                mNextState = State.PING;
                break;
            case READ:
                mNextState = State.READ;
                break;
            case SETLEVEL:
                mNextState = State.READ;
                break;
        }
        return true;
    }

    boolean processIO() {
        //Log.i(TAG, "Begin processIO");

        synchronized (this) {
            try {
                if (!mWaitAck) {
                    //Log.i(TAG, "processIOSend");
                    return processIOSend();
                } else {
                    //Log.i(TAG, "processIOAck");
                    return processIOAck();
                }
            } catch (Exception e) {
                Log.e(TAG, "processIO", e);
                if (null != mDataListener)
                    mDataListener.onError(e);
                return false;
            }
        }
    }

    static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
