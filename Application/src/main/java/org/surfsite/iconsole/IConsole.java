package org.surfsite.iconsole;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by harald on 25.04.17.
 */

public class IConsole {
    public static final byte[] PING     = {(byte) 0xf0, (byte) 0xa0, (byte) 0x01, (byte) 0x01, (byte) 0x92 };
    public static final byte[] INIT_A0  = {(byte) 0xf0, (byte) 0xa0, 0x02, 0x02, (byte) 0x94};
    public static final byte[] PONG     = {(byte) 0xf0, (byte) 0xb0, 0x01, 0x01, (byte) 0xa2};
    public static final byte[] STATUS   = {(byte) 0xf0, (byte) 0xa1, 0x01, 0x01, (byte) 0x93};
    public static final byte[] INIT_A3  = {(byte) 0xf0, (byte) 0xa3, 0x01, 0x01, 0x01, (byte) 0x96};
    public static final byte[] INIT_A4  = {(byte) 0xf0, (byte) 0xa4, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, (byte) 0xa0};
    public static final byte[] START    = {(byte) 0xf0, (byte) 0xa5, 0x01, 0x01, 0x02, (byte) 0x99};
    public static final byte[] STOP     = {(byte) 0xf0, (byte) 0xa5, 0x01, 0x01, 0x04, (byte) 0x9b};
    public static final byte[] READ     = {(byte) 0xf0, (byte) 0xa2, 0x01, 0x01, (byte) 0x94};
    public static final byte[] SETLEVEL = {(byte) 0xf0, (byte) 0xa6, 0x01, 0x01, 0x01, (byte)((0xf0+0xa6+3) & 0xFF)};

    private enum State {
        BEGIN,
        PING,
        A0,
        A1,
        A3,
        A4,
        START,
        STOP,
        READ,
        SETLEVEL,
    }

    private State mCurrentState;
    private State mNextState;
    private int mSetLevel;
    private final InputStream mInputStream;
    private final OutputStream mOutputStream;
    private final DataListener mDataListener;
    private final DebugListener mDebugListener;

    public IConsole(InputStream inputStream, OutputStream outputStream, DataListener dataListener, DebugListener debugListener) {
        this.mInputStream = inputStream;
        this.mOutputStream = outputStream;
        this.mDataListener = dataListener;
        this.mDebugListener = debugListener;
        this.mCurrentState = State.BEGIN;
        this.mNextState = State.PING;
        this.mSetLevel = 1;
    }

    public class Data {
        long mTime;         // in seconds
        int mSpeed10;
        int mRPM;
        int mDistance10;
        int mCalories;
        int mHF;
        int mPower10;
        int mLevel;

        public Data(long mTime, int mSpeed10, int mRPM, int mDistance10, int mCalories, int mHF, int mPower10, int mLevel) {
            this.mTime = mTime;
            this.mSpeed10 = mSpeed10;
            this.mRPM = mRPM;
            this.mDistance10 = mDistance10;
            this.mCalories = mCalories;
            this.mHF = mHF;
            this.mPower10 = mPower10;
            this.mLevel = mLevel;
        }

        public Data(byte[] bytes) {
            this.mTime = (((bytes[2]-1) * 24 + bytes[3]-1) * 60 +  bytes[4]-1) * 60 + bytes[5]-1 ;
            this.mSpeed10    = 100 * (bytes[ 6] - 1) + bytes[ 7] - 1;
            this.mRPM        = 100 * (bytes[ 8] - 1) + bytes[ 9] - 1;
            this.mDistance10 = 100 * (bytes[10] - 1) + bytes[11] - 1;
            this.mCalories   = 100 * (bytes[12] - 1) + bytes[13] - 1;
            this.mHF         = 100 * (bytes[14] - 1) + bytes[15] - 1;
            this.mPower10    = 100 * (bytes[16] - 1) + bytes[17] - 1;
            this.mLevel      = bytes[18] -1;
        }
    }

    public interface DataListener {
        void onData(Data data);
        void onError(Exception e);
    }

    public interface DebugListener {
        void onRead(byte[] bytes);
        void onWrite(byte[] bytes);
    }

    public boolean processIO() {
        synchronized (this) {
            Data data = new Data(0, 0, 0, 0, 0, 0, 0, 0);

            if (null != mDebugListener) {
                mDebugListener.onWrite(PING);
                mDebugListener.onRead(PONG);
            }

            mDataListener.onData(data);
        }
        return true;
    }

    public boolean stop() {
        return true;
    }

    public boolean setLevel(int level) {
        synchronized (this) {
            if (mCurrentState != State.READ)
                return false;

            this.mCurrentState = State.SETLEVEL;
            this.mNextState = State.READ;
            this.mSetLevel = level;
        }
        return true;
    }

    /*
    def send_ack(packet, expect=None, plen=0):
    if expect == None:
        expect = 0xb0 | (ord(packet[1]) & 0xF)

    if plen == 0:
        plen = len(packet)

    got = None
    while got == None:
        sleep(0.1)
        sock.sendall(packet)
        i = 0
        while got == None and i < 6:
            i+=1
            sleep(0.1)
            got = sock.recv(plen)
            if len(got) == plen:
                #print "<-" + hexlify(got)
                pass
            else:
                if len(got) > 0:
                    #print "Got len == %d" % len(got)
                    pass
                got = None

        if got and len(got) >= 3 and got[0] == packet[0] and ord(got[1]) == expect:
            break
        got = None
        #print "---> Retransmit"
    return got

def send_level(lvl):
    packet = struct.pack('BBBBBB', 0xf0, 0xa6, 0x01, 0x01, lvl+1, (0xf0+0xa6+3+lvl) & 0xFF)
    got = send_ack(packet)
    return got

     */

/*
    send_ack(PING)
    prints(win, "ping done")

    send_ack(INIT_A0, expect=0xb7, plen=6)
    prints(win, "A0 done")

    for i in range(0, 5):
        send_ack(PING)
        prints(win, "ping done")

    send_ack(STATUS, plen=6)
    prints(win, "status done")

    send_ack(PING)
    prints(win, "ping done")

    send_ack(INIT_A3)
    prints(win, "A3 done")

    send_ack(INIT_A4)
    prints(win, "A4 done")

    send_ack(START)
    prints(win, "START done")

    level = 1

    while True:
        sleep(0.25)
        while True:
            key = win.getch()
            if key == ord('q'):
                return
            elif key == ord('a') or key == curses.KEY_UP or key == curses.KEY_RIGHT:
                if level < 31:
                    level += 1
                prints(win, "Level: %d" % level)
                send_level(level)

            elif key == ord('y') or key == curses.KEY_DOWN or key == curses.KEY_LEFT:
                if level > 1:
                    level -= 1
                prints(win, "Level: %d" % level)
                send_level(level)
            elif key == -1:
                break

        got = send_ack(READ, plen=21)
        if len(got) == 21:
            ic = IConsole(got)
            power_meter.update(power = ic.power, cadence = ic.rpm)
            speed.update(ic.speed)
            win.addstr(0,0, "%s - %s - %s - %s - %s - %s - %s - %s" % (ic.time_str,
                                                             ic.speed_str,
                                                             ic.rpm_str,
                                                             ic.distance_str,
                                                             ic.calories_str,
                                                             ic.hf_str,
                                                             ic.power_str,
                                                             ic.lvl_str))

 */

/*
        send_ack(STOP)
        send_ack(PING)

 */

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
