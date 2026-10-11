import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.io.File;
import java.net.URL;
import javax.swing.ImageIcon;

/** Avatar selection and drawing only. Does not modify gameplay state. */
public final class AvatarSprites {
    private static final String[] FILES = {"cuervo.jpg", "fantasma.jpg", "cafe.jpg", "guadana.jpg", "bruja.jpg"};
    private static final String[] NAMES = {"Cuervo", "Fantasma", "Café", "Calabaza con guadaña", "Brujita"};
    private static final Image[] IMAGES = new Image[FILES.length];
    private static final int[] CHOICES = {0, 1, 2, 3};
    private AvatarSprites() { }
    public static int count() { return FILES.length; }
    public static String name(int id) { return NAMES[Math.floorMod(id, FILES.length)]; }
    public static void select(int player, int avatar) {
        if (player >= 0 && player < CHOICES.length && avatar >= 0 && avatar < FILES.length) CHOICES[player] = avatar;
    }
    public static int choice(int player) { return CHOICES[player]; }
    private static Image image(int avatar) {
        int id = Math.floorMod(avatar, FILES.length);
        if (IMAGES[id] == null) {
            URL url = AvatarSprites.class.getResource("/avatars/" + FILES[id]);
            if (url != null) IMAGES[id] = new ImageIcon(url).getImage();
            else {
                File local = new File("src/avatars/" + FILES[id]);
                if (!local.isFile()) local = new File("avatars/" + FILES[id]);
                if (local.isFile()) IMAGES[id] = new ImageIcon(local.getPath()).getImage();
            }
        }
        return IMAGES[id];
    }
    public static Image forAvatar(int id) { return image(id); }
    public static void draw(Graphics2D g, int player, int x, int y, int size) {
        Image image = image(choice(player));
        if (image == null) return;
        Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(image, x, y, size, size, null);
        if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
    }
}
