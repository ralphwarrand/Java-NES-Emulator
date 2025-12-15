package nes;

public class Controller {

    private int buttons = 0;
    private int shiftRegister = 0;
    private boolean strobe = false;

    // Button Constants for setters
    public static final int BUTTON_A = 0x80;
    public static final int BUTTON_B = 0x40;
    public static final int BUTTON_SELECT = 0x20;
    public static final int BUTTON_START = 0x10;
    public static final int BUTTON_UP = 0x08;
    public static final int BUTTON_DOWN = 0x04;
    public static final int BUTTON_LEFT = 0x02;
    public static final int BUTTON_RIGHT = 0x01;

    // 0: A
    // 1: B
    // 2: Select
    // 3: Start
    // 4: Up
    // 5: Down
    // 6: Left
    // 7: Right
    public void setButtonPressed(int buttonBit, boolean pressed) {
        if (pressed) {
            buttons |= (1 << buttonBit);
        } else {
            buttons &= ~(1 << buttonBit);
        }
    }

    public void write(int data) {
        // If bit 0 is 1, strobe is ON
        strobe = (data & 0x01) == 1;
        if (strobe) {
            shiftRegister = buttons;
        }
    }

    public int read() {
        int val = 0;
        if (strobe) {
            // While strobe is high, it keeps reloading the buttons (usually just returns
            // 'A')
            val = buttons & 1;
            // Actually on real hardware returns current status of A button repeatedly if
            // strobe is high
            // But implementing standard behavior:
            shiftRegister = buttons; // Reload constantly
            val = shiftRegister & 1;
        } else {
            // Return LSB
            val = shiftRegister & 1;
            // Shift
            shiftRegister >>= 1;
            // High bits usually set to 1 on real NES serial shift, but 0 is fine for most
            // emu
            shiftRegister |= 0x80; // After 8 reads, it usually returns 1s
        }
        return val | 0x40; // Use 0x40 or 0xE0? Open bus behavior often sets high bits.
                           // $4016 usually returns 0x40 or 0x41 (Bit 0 data, Bit 6,7 open bus/other)
                           // Let's just return key state in bit 0.
    }
}
