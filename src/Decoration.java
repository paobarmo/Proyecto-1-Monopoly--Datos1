import java.awt.AlphaComposite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

/**
 * Loads and draws the game's decoration images, kept in the "decoration"
 * folder (next to the src folder, i.e. relative to the directory the program
 * is run from):
 *
 *   background.jpeg  Drawn at its own size in the center of the screen, over
 *                    the gray background and behind everything else (it is
 *                    only reduced if it does not fit in the window).
 *   crow.jpeg        Token of player 1.
 *   pumpkin.jpeg     Token of player 2.
 *   ghost.jpeg       Token of player 3.
 *   witch.jpeg       Token of player 4.
 *
 * The extensions .jpeg, .jpg and .png are accepted. Tokens use the center of
 * the image (cropped to a square if it is not one) shown inside a circle.
 *
 * If an image is missing or cannot be read, a warning is printed once on the
 * console and the game keeps its previous look (plain gray background, color
 * circles for the players), so the game never fails because of an image.
 *
 * Must be used from the Swing event thread (it is only called while painting).
 */
public final class Decoration {

    private static final String FOLDER = "decoration";
    private static final String BACKGROUND = "background";
    private static final String[] AVATARS = {"crow", "pumpkin", "ghost", "witch"};
    private static final String[] EXTENSIONS = {"jpeg", "jpg", "png"};

    private static final int MAX_CACHED_AVATARS = 64;

    private static final Map<String, BufferedImage> loaded = new HashMap<>();
    private static final Set<String> unavailable = new HashSet<>();
    private static final Map<String, BufferedImage> avatarCache = new HashMap<>();

    private Decoration() {
        // Utility class: not meant to be instantiated.
    }

    // ------------------------------------------------------------ background

    /**
     * Draws the background image centered in an area of the given size. Call
     * it right after painting the gray background of a screen.
     */
    public static void paintBackground(Graphics g, int areaWidth, int areaHeight) {
        BufferedImage image = load(BACKGROUND);
        if (image == null) {
            return;
        }

        // Never enlarged (its resolution is limited); only reduced if needed.
        double scale = Math.min(1.0, Math.min(
                (double) areaWidth / image.getWidth(),
                (double) areaHeight / image.getHeight()));
        int width = (int) Math.round(image.getWidth() * scale);
        int height = (int) Math.round(image.getHeight() * scale);
        int x = (areaWidth - width) / 2;
        int y = (areaHeight - height) / 2;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(image, x, y, width, height, null);
        g2.dispose();
    }

    // --------------------------------------------------------------- avatars

    /**
     * Draws the token image of a player inside a circle.
     *
     * @param playerIndex 0-based player number
     * @param x left edge of the circle's bounding square
     * @param y top edge of the circle's bounding square
     * @param diameter size of the circle
     * @return false if the player has no image (nothing is drawn); the caller
     *         should then draw the player's color instead
     */
    public static boolean paintAvatar(Graphics2D g, int playerIndex, int x, int y, int diameter) {
        return paintAvatar(g, playerIndex, x, y, diameter, false);
    }

    /**
     * Same as paintAvatar, optionally in gray tones (used for eliminated
     * players).
     */
    public static boolean paintAvatar(Graphics2D g, int playerIndex, int x, int y,
                                      int diameter, boolean gray) {
        BufferedImage circle = circularAvatar(playerIndex, diameter, gray);
        if (circle == null) {
            return false;
        }
        g.drawImage(circle, x, y, null);
        return true;
    }

    private static BufferedImage circularAvatar(int playerIndex, int diameter, boolean gray) {
        if (playerIndex < 0 || playerIndex >= AVATARS.length || diameter < 1) {
            return null;
        }

        String key = playerIndex + ":" + diameter + ":" + gray;
        BufferedImage cached = avatarCache.get(key);
        if (cached != null) {
            return cached;
        }

        BufferedImage source = load(AVATARS[playerIndex]);
        if (source == null) {
            return null;
        }

        // Center square of the image, scaled to the circle's size.
        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage square = source.getSubimage(
                (source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        BufferedImage scaled = scaleSquare(square, diameter);

        // Cut it into a circle with smooth edges.
        BufferedImage circle = new BufferedImage(diameter, diameter, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = circle.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.fill(new Ellipse2D.Float(0, 0, diameter, diameter));
        g2.setComposite(AlphaComposite.SrcIn);
        g2.drawImage(scaled, 0, 0, null);
        g2.dispose();

        if (gray) {
            toGray(circle);
        }

        if (avatarCache.size() >= MAX_CACHED_AVATARS) {
            avatarCache.clear(); // window resized many times: start over
        }
        avatarCache.put(key, circle);
        return circle;
    }

    // Halves the image repeatedly while it is much larger than the target,
    // which gives a much cleaner result than shrinking it in one step.
    private static BufferedImage scaleSquare(BufferedImage source, int target) {
        BufferedImage current = source;
        int size = source.getWidth();

        while (size / 2 >= target) {
            size /= 2;
            current = resize(current, size);
        }
        return resize(current, target);
    }

    private static BufferedImage resize(BufferedImage source, int size) {
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = result.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, size, size, null);
        g2.dispose();
        return result;
    }

    // Converts the image to gray tones, keeping its transparency.
    private static void toGray(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >> 16) & 0xFF;
                int green = (argb >> 8) & 0xFF;
                int blue = argb & 0xFF;
                int luminance = (int) Math.round(0.299 * red + 0.587 * green + 0.114 * blue);
                image.setRGB(x, y, (alpha << 24) | (luminance << 16) | (luminance << 8) | luminance);
            }
        }
    }

    // --------------------------------------------------------------- loading

    // Returns the image with the given base name, or null if it is not there.
    private static BufferedImage load(String name) {
        BufferedImage image = loaded.get(name);
        if (image != null) {
            return image;
        }
        if (unavailable.contains(name)) {
            return null;
        }

        String problem = "not found";
        for (String extension : EXTENSIONS) {
            String fileName = name + "." + extension;
            try {
                File file = new File(FOLDER, fileName);
                if (file.isFile()) {
                    image = ImageIO.read(file);
                } else {
                    URL resource = Decoration.class.getResource("/" + FOLDER + "/" + fileName);
                    image = resource != null ? ImageIO.read(resource) : null;
                }

                if (image != null) {
                    loaded.put(name, image);
                    return image;
                }
            } catch (IOException e) {
                problem = "could not be read (" + e.getMessage() + ")";
            }
        }

        unavailable.add(name);
        System.err.println("[Decoration] Image '" + name + "' " + problem + " in the \"" + FOLDER
                + "\" folder (looked in " + new File(FOLDER).getAbsolutePath()
                + "); using the default look.");
        return null;
    }
}
