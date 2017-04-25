package org.surfsite.iconsole;

/**
 * Created by harald on 25.04.17.
 */

public class IConsole {
    public static final byte[] PING = { (byte) 0xf0, (byte) 0xa0, (byte) 0x01, (byte) 0x01, (byte) 0x92 };
    /*
    INIT_A0 = struct.pack('BBBBB', 0xf0, 0xa0, 0x02, 0x02, 0x94)
    PING = struct.pack('BBBBB', 0xf0, 0xa0, 0x01, 0x01, 0x92)
    PONG = struct.pack('BBBBB', 0xf0, 0xb0, 0x01, 0x01, 0xa2)
    STATUS = struct.pack('BBBBB', 0xf0, 0xa1, 0x01, 0x01, 0x93)
    INIT_A3 = struct.pack('BBBBBB', 0xf0, 0xa3, 0x01, 0x01, 0x01, 0x96)
    INIT_A4 = struct.pack('BBBBBBBBBBBBBBB', 0xf0, 0xa4, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0xa0)
    START = struct.pack('BBBBBB', 0xf0, 0xa5, 0x01, 0x01, 0x02, 0x99)
    STOP = struct.pack('BBBBBB', 0xf0, 0xa5, 0x01, 0x01, 0x04, 0x9b)
    READ = struct.pack('BBBBB', 0xf0, 0xa2, 0x01, 0x01, 0x94)
*/

    /*
       def __init__(self, got):
        gota = struct.unpack('BBBBBBBBBBBBBBBBBBBBB', got)
        self.time_str = "%02d:%02d:%02d:%02d" % (gota[2]-1, gota[3]-1, gota[4]-1, gota[5]-1)
        self.speed = ((100*(gota[6]-1) + gota[7] -1) / 10.0)
        self.speed_str = "V: % 3.1f km/h" % self.speed
        self.rpm = ((100*(gota[8]-1) + gota[9] -1))
        self.rpm_str = "% 3d RPM" % self.rpm
        self.distance = ((100*(gota[10]-1) + gota[11] -1) / 10.0)
        self.distance_str = "D: % 3.1f km" % self.distance
        self.calories = ((100*(gota[12]-1) + gota[13] -1))
        self.calories_str = "% 3d kcal" % self.calories
        self.hf = ((100*(gota[14]-1) + gota[15] -1))
        self.hf_str = "HF % 3d" % self.hf
        self.power = ((100*(gota[16]-1) + gota[17] -1) / 10.0)
        self.power_str = "% 3.1f W" % self.power
        self.lvl = gota[18] -1
        self.lvl_str = "L: %d" % self.lvl
     */

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
