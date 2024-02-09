import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

public class KeyboardHandler implements KeyListener {
    @Override
    public void keyTyped(KeyEvent e) {
        // This method is called when a key is typed (pressed and released)
        char keyChar = e.getKeyChar();
        System.out.println("Key Typed: " + keyChar);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        // This method is called when a key is pressed down
        int keyCode = e.getKeyCode();
        System.out.println("Key Pressed: " + keyCode);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        // This method is called when a key is released
        int keyCode = e.getKeyCode();
        System.out.println("Key Released: " + keyCode);
    }
}

