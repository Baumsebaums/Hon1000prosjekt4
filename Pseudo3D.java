import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

public class Pseudo3D extends JFrame implements Runnable, KeyListener, MouseListener, MouseMotionListener {
    private static final long serialVersionUID = 1L;
    public int screenWidth = 640;
    public int screenHeight = 480;
    private Thread thread;
    private boolean running;
    private BufferedImage image;
    private int[] pixels;
    
    // Game States
    enum GameState { PLAYING, GAMEOVER }
    private GameState state = GameState.PLAYING;
    
    // Player Health
    private int playerHp = 10;
    private long lastDamageTime = 0;
    private int score = 0;
    
    // Camera
    private double posX = 22.0, posY = 11.5;
    private double dirX = -1.0, dirY = 0.0;
    private double planeX = 0.0, planeY = 0.66;
    private final double moveSpeed = 0.05;
    
    // Keyboard
    private boolean left, right, up, down;
    
    // Mouse
    private Robot robot;
    private boolean robotMovingMouse = false;
    private int pitch = 0;
    private long lastFireTime = 0;
    private int ammo = 7;
    private int shotgunAmmo = 4;
    private int m16Ammo = 30;
    private int minigunAmmo = 180;
    private int railgunAmmo = 1;
    private int blasterAmmo = 30;
    private boolean hasShotgun = false;
    private boolean hasM16 = false;
    private boolean hasMinigun = false;
    private boolean hasRailgun = false;
    private boolean hasBlaster = false;
    private boolean hasBFG = false;
    private int currentWeapon = 1; // 1: Pistol, 2: Shotgun, 3: M16, 4: Minigun, 5: Railgun, 6: Blaster, 7: BFG
    private boolean mouseDown = false;
    private long windUpStartTime = 0;
    private boolean isReloading = false;
    private long reloadStartTime = 0;
    
    // Engine and Entities
    private double[] zBuffer;
    abstract class SpriteEntity {
        double x, y;
        boolean dead = false;
        long spawnTime;
        abstract String[] getSprite();
        abstract int getColor(char c);
    }
    
    class Enemy extends SpriteEntity {
        int hp = 3;
        public Enemy(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return demonSprite; }
        int getColor(char c) {
            switch(c) {
                case '0': return 0xAA0000; // demon red
                case '1': return 0x550000; // dark/horns
                case '2': return 0xFFFFFF; // eyes
                case '3': return 0x000000; // pupil
                case 'L': return 0xAAAA00; // yellow teeth
                default: return 0;
            }
        }
    }
    
    class Potion extends SpriteEntity {
        public Potion(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return potionSprite; }
        int getColor(char c) {
            switch(c) {
                case 'G': return 0xCCDDFF; // glass highlight
                case 'B': return 0x88AADD; // bottle outline
                case 'R': return 0xFF0000; // red liquid
                case 'S': return 0xAA0000; // dark red liquid
                default: return 0;
            }
        }
    }
    
    class ShotgunPickup extends SpriteEntity {
        public ShotgunPickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return shotgunSprite; }
        int getColor(char c) {
            switch(c) {
                case 'W': return 0x8B4513; // Wood brown
                case 'S': return 0x444444; // Steel gray
                default: return 0;
            }
        }
    }
    
    class BloodSplat extends SpriteEntity {
        public int customColorIndex = -1; // -1 for red, otherwise color
        public BloodSplat(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        public BloodSplat(double x, double y, int color) {
            this(x, y);
            this.customColorIndex = color;
        }
        String[] getSprite() { return bloodSprite; }
        int getColor(char c) {
            if (customColorIndex != -1 && (c == 'R' || c == 'D')) {
                if (c == 'R') return customColorIndex; // Pure custom color
                else { // darker version for D
                    int r = ((customColorIndex >> 16) & 0xFF) / 2;
                    int g = ((customColorIndex >> 8) & 0xFF) / 2;
                    int b = (customColorIndex & 0xFF) / 2;
                    return (r << 16) | (g << 8) | b;
                }
            }
            switch(c) {
                case 'R': return 0xAA0000; // Blood red
                case 'D': return 0x550000; // Dark blood
                default: return 0;
            }
        }
    }
    
    class M16Pickup extends SpriteEntity {
        public M16Pickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return m16WorldSprite; }
        int getColor(char c) {
            switch(c) {
                case 'S': return 0x333333; // Black steel
                case 'G': return 0x222222; // Grip
                default: return 0;
            }
        }
    }
    
    class MinigunPickup extends SpriteEntity {
        public MinigunPickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return minigunWorldSprite; }
        int getColor(char c) {
            switch(c) {
                case 'S': return 0x222222; // Dark gunmetal
                case 'G': return 0x111111; // Black barrel
                default: return 0;
            }
        }
    }
    
    class RailgunPickup extends SpriteEntity {
        public RailgunPickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return railgunWorldSprite; }
        int getColor(char c) {
            switch(c) {
                case 'S': return 0xEEEEEE; // White futuristic steel
                case 'B': return 0x00FFFF; // Cyan barrel detail
                default: return 0;
            }
        }
    }
    
    class BlasterPickup extends SpriteEntity {
        public BlasterPickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return blasterWorldSprite; }
        int getColor(char c) {
            switch(c) {
                case 'W': return 0xFFFFFF; // White body
                case 'B': return 0x00FFFF; // Cyan glow
                default: return 0;
            }
        }
    }
    
    class BFGProjectile extends SpriteEntity {
        public double vx, vy;
        public BFGProjectile(double x, double y, double vx, double vy) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return bfgBallArt; }
        int getColor(char c) {
            switch(c) {
                case 'G': return 0x00FF00; // Neon Green
                case 'W': return 0xCCFFCC; // Core white-green
                default: return 0;
            }
        }
    }

    class BFGExplosion extends SpriteEntity {
        public BFGExplosion(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return bfgExplosionArt; }
        int getColor(char c) {
            switch(c) {
                case 'G': return 0x00FF00; // Neon Green
                case 'D': return 0x004400; // Dark Green
                default: return 0;
            }
        }
    }

    class BFG9000Pickup extends SpriteEntity {
        public BFG9000Pickup(double x, double y) {
            this.x = x;
            this.y = y;
            this.spawnTime = System.currentTimeMillis();
        }
        String[] getSprite() { return bfgWorldSprite; }
        int getColor(char c) {
            switch(c) {
                case 'G': return 0x006600; // Dark Green body
                case 'C': return 0x00FF00; // Core Green
                case 'S': return 0x444444; // Steel
                default: return 0;
            }
        }
    }
    
    private List<Enemy> enemies = new ArrayList<>();
    private List<Potion> potions = new ArrayList<>();
    private List<ShotgunPickup> shotgunPickups = new ArrayList<>();
    private List<M16Pickup> m16Pickups = new ArrayList<>();
    private List<MinigunPickup> minigunPickups = new ArrayList<>();
    private List<RailgunPickup> railgunPickups = new ArrayList<>();
    private List<BlasterPickup> blasterPickups = new ArrayList<>();
    private List<BFG9000Pickup> bfgPickups = new ArrayList<>();
    private List<BFGProjectile> bfgProjectiles = new ArrayList<>();
    private List<BFGExplosion> bfgExplosions = new ArrayList<>();
    private List<BloodSplat> bloodSplats = new ArrayList<>();
    private String[] demonSprite;
    private String[] potionSprite;
    private String[] shotgunSprite;
    private String[] m16WorldSprite;
    private String[] minigunWorldSprite;
    private String[] railgunWorldSprite;
    private String[] blasterWorldSprite;
    private String[] bfgWorldSprite;
    private String[] bfgBallArt;
    private String[] bfgExplosionArt;
    private String[] bloodSprite;
    
    public int[][] map = {
      {1,1,1,1,1,1,1,2,1,1,1,1,1,2,1,1,1,1,1,2,1,1,1,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,2,1,1,1,2,0,0,0,0,1,0,1,0,1,0,0,0,1},
      {1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,1,0,0,0,1,0,0,0,0,2,0,0,0,2,0,0,0,1},
      {2,0,0,0,0,0,1,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {1,0,0,0,0,0,2,1,0,1,2,0,0,0,0,1,0,1,0,1,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,2,1,1,2,1,1,1,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,1,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,1,0,0,0,0,2,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {2,1,0,1,0,0,0,0,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,2,1,1,1,1,1,1,2,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1},
      {1,2,1,1,2,1,1,2,1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,2},
      {1,1,1,1,1,1,1,1,1,2,1,1,1,2,1,1,1,2,1,1,1,2,1,1}
    };
    private int[] gunPixels = new int[32 * 32];
    private int[] shotgunPixels = new int[32 * 32];
    private int[] m16Pixels = new int[32 * 32];
    private int[] railgunPixels = new int[32 * 32];
    private int[] blasterPixels = new int[32 * 32];
    private int[] bfgPixels = new int[32 * 32];
    private int[][] minigunFrames = new int[3][32 * 32]; 
    
    public Pseudo3D() {
        String[] gunArt = {
          "                                ",
          "                                ",
          "                                ",
          "                                ",
          "                                ",
          "       77777                    ",
          "      777777                    ",
          "     7777777                    ",
          "    77777777                    ",
          "   777777777                    ",
          "  7777777777                    ",
          "  77777777777                   ",
          "   88888888888                  ",
          "    88884448888                 ",
          "     8884444488H                ",
          "      H8444444HHH               ",
          "       HH4444444HHH             ",
          "       HHH44444HHHH             ",
          "       HHHHHHHHHHHH             ",
          "       HHHHHHHHHHHH             ",
          "       HHHHHHHHHHHH             ",
          "        HHHHHHHHHHH             ",
          "        HHHHHHHHHHH             ",
          "        TSSSSSSSTSS             ",
          "         SSSSSSSSSS             ",
          "         SSSSSSSSSS             ",
          "          SSSSSSSSS             ",
          "          SSSSSSSSS             ",
          "          SSSSSSSSS             ",
          "           SSSSSSSS             ",
          "           SSSSSSSS             ",
          "           SSSSSSSS             "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = gunArt[y].charAt(x);
                int color = 0;
                switch(c) {
                    case '7': color = 0xAAAAAA; break;
                    case '8': color = 0x333333; break;
                    case '4': color = 0x5C4033; break;
                    case 'H': color = 0xFFCC99; break;
                    case 'S': color = 0x4A5D23; break;
                    case 'T': color = 0x2A3D03; break;
                }
                gunPixels[x + y * 32] = color;
            }
        }
        
        String[] shotgunArt = {
            "                                ",
            "                                ",
            "                                ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           778877               ",
            "           WWWWWW               ",
            "          WWWWWWWW              ",
            "         WWWWWWWWWW             ",
            "        WWWWWWWWWWWW            ",
            "       WWWWWWWWWWWWWW           ",
            "      WWWWWWWWWWWWWWWW          ",
            "     HWWWW      WWWWWH          ",
            "    HHWWW        WWWHHH         ",
            "   HHHHW          WHHHHH        ",
            "  HHHHHH          HHHHHHH       ",
            " HHHHHHH          HHHHHHHH      ",
            "HHHHHHHH          HHHHHHHHH     ",
            "HHHHHHHH          HHHHHHHHH     ",
            "HHHHHHHH          HHHHHHHHH     ",
            "HHHHHHHH          HHHHHHHHH     ",
            " RRRRRRR          RRRRRRRRR     "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = shotgunArt[y].charAt(x);
                int color = 0;
                switch(c) {
                    case '7': color = 0x888888; break; // light steel
                    case '8': color = 0x444444; break; // dark steel
                    case 'W': color = 0x5C4033; break; // wood
                    case 'H': color = 0xFFCC99; break; // hands
                    case 'R': color = 0xCCAA88; break; // sleeves
                }
                shotgunPixels[x + y * 32] = color;
            }
        }

        String[] m16Art = {
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                7               ",
            "               77               ",
            "              777               ",
            "             7777               ",
            "            77777               ",
            "           777777               ",
            "          7777777               ",
            "         77777777               ",
            "        888888888               ",
            "       8888888888               ",
            "      8888888888                ",
            "     888GGGGGG88                ",
            "    888GGGGGG88                 ",
            "    88GGGGGG88                  ",
            "    88GGGGGG88H                 ",
            "    88GGGGG8HHH                 ",
            "     888G88HHHH                 ",
            "      88888HHHH                 ",
            "       8888HHHH                 ",
            "       H888HHHH                 ",
            "       HHHHHHHH                 ",
            "       HHHHHHHH                 ",
            "       HHHHHHHH                 ",
            "       HHHHHHHH                 ",
            "        HHHHHHH                 ",
            "        HHHHHH                  ",
            "         HHHH                   "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = m16Art[y].charAt(x);
                int color = 0;
                switch(c) {
                    case '7': color = 0x555555; break; // dark barrel
                    case '8': color = 0x222222; break; // black receiver
                    case 'G': color = 0x111111; break; // deep black grip
                    case 'H': color = 0xFFCC99; break; // hand
                }
                m16Pixels[x + y * 32] = color;
            }
        }

        String[][] minigunArtFrames = {
            { // Frame 1
                "                                ",
                "                                ",
                "           SSSSS                ",
                "          SSSSSSS               ",
                "         S B S B S              ",
                "         SSSSSSSSS              ",
                "         S B S B S              ",
                "          SSSSSSS               ",
                "           SSSSS                ",
                "           WWWWW                ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          W66666W               ",
                "          W66666W               ",
                "         WWWWWWWWW              ",
                "        WWWWWWWWWWW             ",
                "       WWWWWWWWWWWWW            ",
                "      WWWWWWWWWWWWWWW           ",
                "     WWWWWWWWWWWWWWWWW          ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "     HHHHHHHHHHHHHHHHH          ",
                "      HHHHHHHHHHHHHHH           ",
                "       HHHHHHHHHHHHH            ",
                "        HHHHHHHHHHH             ",
                "         HHHHHHHHH              "
            },
            { // Frame 2
                "                                ",
                "                                ",
                "           SSSSS                ",
                "          SSSSSSS               ",
                "         B S B S B              ",
                "         SSSSSSSSS              ",
                "         B S B S B              ",
                "          SSSSSSS               ",
                "           SSSSS                ",
                "           WWWWW                ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          W66666W               ",
                "          W66666W               ",
                "         WWWWWWWWW              ",
                "        WWWWWWWWWWW             ",
                "       WWWWWWWWWWWWW            ",
                "      WWWWWWWWWWWWWWW           ",
                "     WWWWWWWWWWWWWWWWW          ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "     HHHHHHHHHHHHHHHHH          ",
                "      HHHHHHHHHHHHHHH           ",
                "       HHHHHHHHHHHHH            ",
                "        HHHHHHHHHHH             ",
                "         HHHHHHHHH              "
            },
            { // Frame 3
                "                                ",
                "                                ",
                "           SSSSS                ",
                "          SSSSSSS               ",
                "         S S S S S              ",
                "         B B B B B              ",
                "         S S S S S              ",
                "          SSSSSSS               ",
                "           SSSSS                ",
                "           WWWWW                ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          WWWWWWW               ",
                "          W66666W               ",
                "          W66666W               ",
                "         WWWWWWWWW              ",
                "        WWWWWWWWWWW             ",
                "       WWWWWWWWWWWWW            ",
                "      WWWWWWWWWWWWWWW           ",
                "     WWWWWWWWWWWWWWWWW          ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "  HHHHHHHHHHHHHHHHHHHHHHH       ",
                "   HHHHHHHHHHHHHHHHHHHHH        ",
                "    HHHHHHHHHHHHHHHHHHH         ",
                "     HHHHHHHHHHHHHHHHH          ",
                "      HHHHHHHHHHHHHHH           ",
                "       HHHHHHHHHHHHH            ",
                "        HHHHHHHHHHH             ",
                "         HHHHHHHHH              "
            }
        };

        for (int f = 0; f < 3; f++) {
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    char c = minigunArtFrames[f][y].charAt(x);
                    int color = 0;
                    switch(c) {
                        case 'S': color = 0x666666; break; // Steel
                        case 'B': color = 0x222222; break; // Barrel
                        case 'W': color = 0x333333; break; // Body
                        case '6': color = 0x111111; break; // Inner
                        case 'H': color = 0xFFCC99; break; // Hand
                    }
                    minigunFrames[f][x + y * 32] = color;
                }
            }
        }

        String[] railgunArt = {
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "         SSSSSSSSSSSSSSS        ",
            "        SBBBBBBBBBBBBBBB        ",
            "        SBBBBBBBBBBBBBBB        ",
            "         SSSSSSSSSSSSSSS        ",
            "          SSSSSSSSSSSSS         ",
            "           SSSSSSSSSSS          ",
            "            SSSSSSSSS           ",
            "             SSSSSSS            ",
            "            WWWWWWWWW           ",
            "           WWWWWWWWWWW          ",
            "          WWWWWWWWWWWWW         ",
            "         WWWWWWWWWWWWWWW        ",
            "        HHHHHHHHHHHHHHHHH       ",
            "       HHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHHH      ",
            "        HHHHHHHHHHHHHHHHH       ",
            "         HHHHHHHHHHHHHHH        ",
            "          HHHHHHHHHHHHH         ",
            "           HHHHHHHHHHH          ",
            "            HHHHHHHHH           "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = railgunArt[y].charAt(x);
                int color = 0;
                switch(c) {
                    case 'S': color = 0xFFFFFF; break; // White futuristic body
                    case 'B': color = 0x00FFFF; break; // Glowing blue rail
                    case 'W': color = 0xCCCCCC; break; // Gray accents
                    case 'H': color = 0xFFCC99; break; // Hand
                }
                railgunPixels[x + y * 32] = color;
            }
        }

        String[] blasterArt = {
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "                                ",
            "            WWWWWWWW            ",
            "          WWWWWWWWWWWW          ",
            "         WWWWWWWWWWWWWW         ",
            "        WWWWWWWWWWWWWWWW        ",
            "       WWWWWWWWBBWWWWWWWW       ",
            "       WWWWWWWWBBWWWWWWWW       ",
            "       WWWWWWWWWWWWWWWWWW       ",
            "        WWWWWWWWWWWWWWWW        ",
            "         WWWWWWWWWWWWWW         ",
            "          WWWWWWWWWWWW          ",
            "           WWWWWWWWWW           ",
            "           SSSSSSSSSS           ",
            "          SSSSSSSSSSSS          ",
            "         SSSSSSSSSSSSSS         ",
            "        SSSSSSSSSSSSSSSS        ",
            "       HHHHHHHHHHHHHHHHHH       ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHH       ",
            "        HHHHHHHHHHHHHHHH        ",
            "         HHHHHHHHHHHHHH         ",
            "          HHHHHHHHHHHH          "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = blasterArt[y].charAt(x);
                int color = 0;
                switch(c) {
                    case 'W': color = 0xFFFFFF; break; // White body
                    case 'B': color = 0x00FFFF; break; // Cyan detail
                    case 'S': color = 0xCCCCCC; break; // Steel detail
                    case 'H': color = 0xFFCC99; break; // Hand
                }
                blasterPixels[x + y * 32] = color;
            }
        }

        String[] bfgArt = {
            "                                ",
            "                                ",
            "                                ",
            "           GGGGGGGGGG           ",
            "          GGGGGGGGGGGG          ",
            "         GGGGGGGGGGGGGG         ",
            "        GGGGGGGGGGGGGGGG        ",
            "       GGCCGGGGGGGGGGCCGG       ",
            "       GGCCGGGGGGGGGGCCGG       ",
            "       GGCCGGGGGGGGGGCCGG       ",
            "       GGGGGGGGGGGGGGGGGG       ",
            "       GGGGGGGGGGGGGGGGGG       ",
            "      GGGGGGGGGGGGGGGGGGGG      ",
            "      GGGGGGGGGGGGGGGGGGGG      ",
            "      GGGGGGGGGGGGGGGGGGGG      ",
            "      GGGGGGGGGGGGGGGGGGGG      ",
            "      GGGGGGGGGGGGGGGGGGGG      ",
            "      SSSSSSSSSSSSSSSSSSSS      ",
            "     SSSSSSSSSSSSSSSSSSSSSS     ",
            "    SSSSSSSSSSSSSSSSSSSSSSSS    ",
            "    SSSSSSSSSSSSSSSSSSSSSSSS    ",
            "    HHHHHHHHHHHHHHHHHHHHHHHH    ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "   HHHHHHHHHHHHHHHHHHHHHHHHHH   ",
            "    HHHHHHHHHHHHHHHHHHHHHHHH    ",
            "     HHHHHHHHHHHHHHHHHHHHHH     ",
            "      HHHHHHHHHHHHHHHHHHHH      ",
            "       HHHHHHHHHHHHHHHHHH       "
        };
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                char c = bfgArt[y].charAt(x);
                int color = 0;
                switch(c) {
                    case 'G': color = 0x004400; break; // Dark green body
                    case 'C': color = 0x00FF00; break; // Glowing green core
                    case 'S': color = 0x222222; break; // Steel dark
                    case 'H': color = 0xFFCC99; break; // Hand
                }
                bfgPixels[x + y * 32] = color;
            }
        }

        thread = new Thread(this);
        image = new BufferedImage(screenWidth, screenHeight, BufferedImage.TYPE_INT_RGB);
        pixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        zBuffer = new double[screenWidth];
        
        demonSprite = new String[] {
            "      1      1    ",
            "     11      11   ",
            "     1000000001   ",
            "    100000000001  ",
            "    002220022200  ",
            "    002320023200  ",
            "    002220022200  ",
            "    000000000000  ",
            "   00011000011000 ",
            "  0000LLLLLLLL0000",
            " 00000LLLLLLLL0000",
            "000000000000000000",
            "000000000000000000",
            " 0000000000000000 ",
            "  000000  000000  ",
            "  0000      0000  "
        };
        
        potionSprite = new String[] {
            "      BBBB      ",
            "     BGGGB      ",
            "     BGGGB      ",
            "    BGGGGGB     ",
            "   BGGGGGGGB    ",
            "  BGGGGGGGGGB   ",
            "  BGGGGGGGGGB   ",
            "  BGRRRRRRRGB   ",
            "  BSRRRRRRRGB   ",
            "  BSRRRRRRRGB   ",
            "  BSRRRRRRRGB   ",
            "  BSRRRRRRRGB   ",
            "  BSSRRRRRSSB   ",
            "   BSSSSSSSB    ",
            "    BBBBBBB     ",
            "                "
        };
        
        shotgunSprite = new String[] {
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "      SSSSSSSSS ",
            "WWWWWWSSSSSSSSS ",
            "WWWWWWSSSSSSSSS ",
            "W     SSSSSSSSS ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        bloodSprite = new String[] {
            "                ",
            "                ",
            "       RR       ",
            "     RRRRRR     ",
            "    RRRRRRRR    ",
            "   RRDRRRRDRR   ",
            "   RRRRRRRRRR   ",
            "    RRRRRRRR    ",
            "     RRRRRR     ",
            "       RR       ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        m16WorldSprite = new String[] {
            "                ",
            "                ",
            "      SSSSS     ",
            "   SSSSSSSSSS   ",
            "  SGGGGGGGGGG   ",
            "      GGGGG     ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        minigunWorldSprite = new String[] {
            // ... omitting for brevity if I could, but I'll update it properly ...
            "                ",
            "   SSSSSSSSSS   ",
            "  SGGGGGGGGGG   ",
            " SGGGGGGGGGGG   ",
            " SGGGGGGGGGGG   ",
            "  SGGGGGGGGGG   ",
            "   SSSSSSSSSS   ",
            "      SSSS      ",
            "      SSSS      ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        railgunWorldSprite = new String[] {
            "                ",
            "                ",
            "  SSSSSSSSSSSS  ",
            "  SBBBBBBBBBBB  ",
            "  SSSSSSSSSSSS  ",
            "      SS        ",
            "      SS        ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        blasterWorldSprite = new String[] {
            "                ",
            "     WWWWWW     ",
            "    WWWWWWWW    ",
            "   WWWWWWWWWW   ",
            "   WWWWBBWWWW   ",
            "   WWWWWWWWWW   ",
            "    WWWWWWWW    ",
            "     WWWWWW     ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        bfgWorldSprite = new String[] {
            "                ",
            "  GGGGGGGGGGGG  ",
            " GGGGGGGGGGGGGG ",
            " GGGCCCCCCCCGGG ",
            " GGGGGGGGGGGGGG ",
            "  GGGGGGGGGGGG  ",
            "      SSSS      ",
            "      SSSS      ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };

        bfgBallArt = new String[] {
            "      GGGG      ",
            "    GGWWWWGG    ",
            "   GWWWWWWWWG   ",
            "  GWWWWWWWWWWG  ",
            "  GWWWWWWWWWWG  ",
            "  GWWWWWWWWWWG  ",
            "  GWWWWWWWWWWG  ",
            "   GWWWWWWWWG   ",
            "    GGWWWWGG    ",
            "      GGGG      ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };

        bfgExplosionArt = new String[] {
            "    GGGGGGGG    ",
            "  GGGGGGGGGGGG  ",
            " GGGGGDDDDGGGGG ",
            "GGGGGDDDDDDGGGGG",
            "GGGGDDDDDDDDGGGG",
            "GGGGDDDDDDDDGGGG",
            "GGGGGDDDDDDGGGGG",
            " GGGGGDDDDGGGGG ",
            "  GGGGGGGGGGGG  ",
            "    GGGGGGGG    ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                ",
            "                "
        };
        
        setSize(screenWidth, screenHeight);
        setResizable(false);
        setTitle("Pseudo-3D Raycaster");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBackground(Color.black);
        setLocationRelativeTo(null);
        setVisible(true);
        addKeyListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
        
        // Hide normal cursor
        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");
        setCursor(blankCursor);
        
        try {
            robot = new Robot();
            // initialize mouse to center
            Point p = getLocationOnScreen();
            robot.mouseMove(p.x + screenWidth / 2, p.y + screenHeight / 2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        start();
    }
    
    private synchronized void start() {
        running = true;
        thread.start();
    }
    
    public synchronized void stop() {
        running = false;
        try {
            thread.join();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void render() {
        // Render Ceiling and Floor (Ray Casting)
        int horizon = screenHeight / 2 + pitch;
        
        float rayDirX0 = (float)(dirX - planeX);
        float rayDirY0 = (float)(dirY - planeY);
        float rayDirX1 = (float)(dirX + planeX);
        float rayDirY1 = (float)(dirY + planeY);
        
        for (int y = 0; y < screenHeight; y++) {
            int p = y - horizon;
            boolean isFloor = p > 0;
            if (p < 0) p = -p; // Mirror distance for ceiling
            if (p == 0) {
                for (int x = 0; x < screenWidth; x++) pixels[x + y * screenWidth] = 0; // Black horizon
                continue;
            }
            
            float rowDistance = (float)screenHeight / (2.0f * p);
            
            float floorStepX = rowDistance * (rayDirX1 - rayDirX0) / screenWidth;
            float floorStepY = rowDistance * (rayDirY1 - rayDirY0) / screenWidth;
            
            float floorX = (float)posX + rowDistance * rayDirX0;
            float floorY = (float)posY + rowDistance * rayDirY0;
            
            for (int x = 0; x < screenWidth; x++) {
                long globalTx = (long)(Math.floor(floorX * 64.0f));
                long globalTy = (long)(Math.floor(floorY * 64.0f));
                long absTx = globalTx >= 0 ? globalTx : (globalTx % 1000000 + 1000000);
                long absTy = globalTy >= 0 ? globalTy : (globalTy % 1000000 + 1000000);
                
                long plankWidth = 16;
                long plankLength = 120;
                long plankIndexX = absTx / plankWidth;
                long staggerY = (plankIndexX * 89) % plankLength;
                long localPy = (absTy + staggerY) % plankLength;
                
                int r, g, b;
                if (isFloor) {
                    r = 110; g = 70; b = 35; // Base wood color
                } else {
                    r = 80; g = 50; b = 25; // Darker wood for ceiling
                }
                
                if (absTx % plankWidth < 2 || localPy < 2) {
                    if (isFloor) { r = 30; g = 15; b = 5; } // Crevices
                    else { r = 20; g = 10; b = 3; }
                } else {
                    int noise = (int)(Math.sin(floorX * 80) * 8 + Math.sin(floorY * 200) * 6);
                    r += noise; g += noise; b += (noise / 2);
                    r = Math.min(255, Math.max(0, r));
                    g = Math.min(255, Math.max(0, g));
                    b = Math.min(255, Math.max(0, b));
                }
                
                // Depth shading
                float shade = 1.0f - Math.min(1.0f, rowDistance / 12.0f);
                if (shade < 0) shade = 0;
                r = (int)(r * shade);
                g = (int)(g * shade);
                b = (int)(b * shade);
                
                pixels[x + y * screenWidth] = (r << 16) | (g << 8) | b;
                
                floorX += floorStepX;
                floorY += floorStepY;
            }
        }
        
        for(int x = 0; x < screenWidth; x++) {
            // calculate ray position and direction
            double cameraX = 2 * x / (double)screenWidth - 1; // x-coordinate in camera space
            double rayDirX = dirX + planeX * cameraX;
            double rayDirY = dirY + planeY * cameraX;
            
            // which box of the map we're in
            int mapX = (int)posX;
            int mapY = (int)posY;
            
            // length of ray from current position to next x or y-side
            double sideDistX;
            double sideDistY;
            
            // length of ray from one x or y-side to next x or y-side
            double deltaDistX = Math.abs(1 / rayDirX);
            double deltaDistY = Math.abs(1 / rayDirY);
            double perpWallDist;
            
            // what direction to step in x or y-direction (either +1 or -1)
            int stepX;
            int stepY;
            
            int hit = 0; // was there a wall hit?
            int side = 0; // was a NS or a EW wall hit?
            
            // calculate step and initial sideDist
            if (rayDirX < 0) {
                stepX = -1;
                sideDistX = (posX - mapX) * deltaDistX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1.0 - posX) * deltaDistX;
            }
            if (rayDirY < 0) {
                stepY = -1;
                sideDistY = (posY - mapY) * deltaDistY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1.0 - posY) * deltaDistY;
            }
            
            // perform DDA
            while (hit == 0) {
                // jump to next map square, either in x-direction, or in y-direction
                if (sideDistX < sideDistY) {
                    sideDistX += deltaDistX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaDistY;
                    mapY += stepY;
                    side = 1;
                }
                // Check if ray has hit a wall
                if (map[mapX][mapY] > 0) hit = 1;
            }
            
            // Calculate distance projected on camera direction (Euclidean distance would give fisheye effect!)
            if (side == 0) perpWallDist = (mapX - posX + (1 - stepX) / 2) / rayDirX;
            else           perpWallDist = (mapY - posY + (1 - stepY) / 2) / rayDirY;
            
            // Calculate height of line to draw on screen
            // Add a small Math.max to prevent Division By Zero just in case
            int lineHeight = (int)(screenHeight / Math.max(perpWallDist, 0.0001));
            zBuffer[x] = perpWallDist;
            
            // calculate lowest and highest pixel to fill in current stripe
            int drawStart = -lineHeight / 2 + screenHeight / 2 + pitch;
            if(drawStart < 0) drawStart = 0;
            int drawEnd = lineHeight / 2 + screenHeight / 2 + pitch;
            if(drawEnd >= screenHeight) drawEnd = screenHeight - 1;
            
            // Calculate exact value of wallX
            double wallX;
            if (side == 0) wallX = posY + perpWallDist * rayDirY;
            else           wallX = posX + perpWallDist * rayDirX;
            wallX -= Math.floor(wallX);
            
            int texWidth = 64;
            int texHeight = 64;
            int texX = (int)(wallX * texWidth);
            if((side == 0 && rayDirX > 0) || (side == 1 && rayDirY < 0)) texX = texWidth - texX - 1;
            
            // draw the pixels of the stripe as a vertical line
            for(int y = drawStart; y < drawEnd; y++) {
                int d = y - pitch - screenHeight / 2 + lineHeight / 2;
                int texY = ((d * texHeight) / lineHeight);
                
                if (texY < 0) texY = 0;
                if (texY >= texHeight) texY = texHeight - 1;
                
                boolean isMortar = false;
                if (texY % 16 < 2) isMortar = true; // Horizontal mortar
                
                int brickRow = texY / 16;
                int offsetTexX = texX + (brickRow % 2) * 16;
                if (offsetTexX % 32 < 2) isMortar = true; // Vertical mortar
                
                int c;
                if (isMortar) {
                    c = 0x333333; // Dark gray mortar
                } else {
                    int noise = ((texX * 13 + texY * 27) % 21) - 10;
                    int r = Math.min(255, Math.max(0, 120 + noise));
                    c = (r << 16) | (r << 8) | r; // Gray brick with noise
                }
                
                // give x and y sides different brightness
                if(side == 1) {
                    int r = ((c >> 16) & 0xFF) / 2;
                    int g = ((c >> 8) & 0xFF) / 2;
                    int b = (c & 0xFF) / 2;
                    c = (r << 16) | (g << 8) | b;
                }

                // Procedural Torch Decal
                if (map[mapX][mapY] == 2) {
                    if (texX >= 28 && texX <= 35 && texY >= 26 && texY <= 44) {
                        // Stick
                        if (texX > 32) c = 0x3E2723; // Stick shadow
                        else c = 0x5C4033; // Stick highlight
                    } else if (texX >= 16 && texX <= 47 && texY >= 4 && texY <= 30) {
                        double dy = 28.0 - texY; 
                        if (dy > 0) {
                            double ratio = Math.min(1.0, dy / 20.0); // 0 (bottom) to 1 (top)
                            long time = System.currentTimeMillis();
                            double maxRadius = 6.0 + Math.sin(time / 100.0) * 1.5;
                            double currentRadius = maxRadius * (1.0 - Math.pow(ratio, 1.5)); 
                            
                            double flickerX = Math.sin(dy * 0.5 + time / 80.0) * 3.0 * ratio; // Wavy fire
                            double cd = Math.abs(texX - 31.5 - flickerX);
                            
                            if (cd < currentRadius) {
                                // Core flame
                                int fr = 255;
                                int fg = (int)(255 * (1.0 - ratio * 1.2));
                                if (fg < 0) fg = 0;
                                int fb = 30 - (int)(ratio * 30);
                                if (fb < 0) fb = 0;
                                c = (fr << 16) | (fg << 8) | fb;
                            } else if (cd < currentRadius + 12.0) {
                                // Glow cast onto the wall
                                double glowInt = 1.0 - ((cd - currentRadius) / 12.0);
                                glowInt *= (1.0 - ratio * 0.5) * (0.8 + Math.sin(time / 150.0) * 0.2); // flicker glow
                                if (glowInt < 0) glowInt = 0;
                                
                                int wr = (c >> 16) & 0xFF;
                                int wg = (c >> 8) & 0xFF;
                                int wb = c & 0xFF;
                                
                                int gr = (int)(wr + 255 * glowInt);
                                int gg = (int)(wg + 120 * glowInt);
                                int gb = wb; // No blue in fire glow
                                
                                c = (Math.min(255, gr) << 16) | (Math.min(255, gg) << 8) | Math.min(255, gb);
                            }
                        }
                    }
                }
                
                pixels[x + y * screenWidth] = c;
            }
        }

        // Sprite casting (Enemies, Potions, Shotgun Pickups, M16 Pickups, Blood)
        List<SpriteEntity> allSprites = new ArrayList<>();
        allSprites.addAll(enemies);
        allSprites.addAll(potions);
        allSprites.addAll(shotgunPickups);
        allSprites.addAll(m16Pickups);
        allSprites.addAll(minigunPickups);
        allSprites.addAll(railgunPickups);
        allSprites.addAll(blasterPickups);
        allSprites.addAll(bfgPickups);
        allSprites.addAll(bfgProjectiles);
        allSprites.addAll(bfgExplosions);
        allSprites.addAll(bloodSplats);
        
        allSprites.sort((a, b) -> {
            double distA = (posX - a.x) * (posX - a.x) + (posY - a.y) * (posY - a.y);
            double distB = (posX - b.x) * (posX - b.x) + (posY - b.y) * (posY - b.y);
            return Double.compare(distB, distA);
        });

        for (SpriteEntity sprite : allSprites) {
            double spriteX = sprite.x - posX;
            double spriteY = sprite.y - posY;

            double invDet = 1.0 / (planeX * dirY - dirX * planeY);
            
            double transformX = invDet * (dirY * spriteX - dirX * spriteY);
            double transformY = invDet * (-planeY * spriteX + planeX * spriteY);

            if (transformY <= 0) continue;

            int spriteScreenX = (int)((screenWidth / 2) * (1 + transformX / transformY));
            int spriteHeight = Math.abs((int)(screenHeight / transformY));
            
            int drawStartY = -spriteHeight / 2 + screenHeight / 2 + pitch;
            if (drawStartY < 0) drawStartY = 0;
            int drawEndY = spriteHeight / 2 + screenHeight / 2 + pitch;
            if (drawEndY >= screenHeight) drawEndY = screenHeight - 1;

            int spriteWidth = Math.abs((int)(screenHeight / transformY));
            int drawStartX = -spriteWidth / 2 + spriteScreenX;
            if (drawStartX < 0) drawStartX = 0;
            int drawEndX = spriteWidth / 2 + spriteScreenX;
            if (drawEndX >= screenWidth) drawEndX = screenWidth - 1;

            String[] activeSprite = sprite.getSprite();
            int texW = activeSprite[0].length();
            int texH = activeSprite.length;

            for (int stripe = drawStartX; stripe < drawEndX; stripe++) {
                int texX = (int)(256 * (stripe - (-spriteWidth / 2 + spriteScreenX)) * texW / spriteWidth) / 256;
                if (texX < 0) texX = 0;
                if (texX >= texW) texX = texW - 1;
                // constrain Z-buffer
                if (transformY > 0 && stripe > 0 && stripe < screenWidth && transformY < zBuffer[stripe]) {
                    for (int y = drawStartY; y < drawEndY; y++) {
                        int d = y - pitch - screenHeight / 2 + spriteHeight / 2;
                        int texY = ((d * texH) / spriteHeight);
                        if (texY < 0) texY = 0;
                        if (texY >= texH) texY = texH - 1;
                        
                        char c = activeSprite[texY].charAt(texX);
                        if (c != ' ') {
                            pixels[stripe + y * screenWidth] = sprite.getColor(c);
                        }
                    }
                }
            }
        }

        // Draw weapon overlay
        int scale = 14;
        long time = System.currentTimeMillis();
        boolean moving = up || down || left || right;
        int swayX = (int)(Math.sin(time / 200.0) * (moving ? 20 : 3));
        int swayY = (int)(Math.abs(Math.cos(time / 200.0)) * (moving ? 15 : 2));

        long timeSinceFire = time - lastFireTime;
        int recoilX = 0, recoilY = 0;
        if (timeSinceFire < 200) {
            if (timeSinceFire < 50) {
                 recoilX = 15;
                 recoilY = 30;
            } else {
                 double r = 1.0 - ((timeSinceFire - 50) / 150.0);
                 recoilX = (int)(15 * r);
                 recoilY = (int)(30 * r);
            }
        }
        
        long timeSinceReload = time - reloadStartTime;
        if (isReloading) {
            int reloadTime = 3000;
            if (currentWeapon == 4) reloadTime = 5000;
            else if (currentWeapon == 5) reloadTime = 1500;
            else if (currentWeapon == 6) reloadTime = 2000;
            
            if (timeSinceReload >= reloadTime) {
                isReloading = false;
                if (currentWeapon == 1) ammo = 7;
                else if (currentWeapon == 2) shotgunAmmo = 4;
                else if (currentWeapon == 3) m16Ammo = 30;
                else if (currentWeapon == 4) minigunAmmo = 180;
                else if (currentWeapon == 5) railgunAmmo = 1;
                else if (currentWeapon == 6) blasterAmmo = 30;
            }
        }

        int animOffsetY = 0;
        if (isReloading) {
            int reloadTime = 3000;
            if (currentWeapon == 4) reloadTime = 5000;
            else if (currentWeapon == 5) reloadTime = 1500;
            else if (currentWeapon == 6) reloadTime = 2000; // 2 seconds
            
            if (timeSinceReload < 600) {
                animOffsetY = (int)((timeSinceReload / 600.0) * screenHeight);
            } else if (timeSinceReload < reloadTime - 600) {
                animOffsetY = screenHeight; // Out of sight
            } else {
                animOffsetY = (int)((1.0 - ((timeSinceReload - (reloadTime - 600)) / 600.0)) * screenHeight);
            }
        }

        int[] activeGunPixels;
        if (currentWeapon == 1) activeGunPixels = gunPixels;
        else if (currentWeapon == 2) activeGunPixels = shotgunPixels;
        else if (currentWeapon == 3) activeGunPixels = m16Pixels;
        else if (currentWeapon == 5) activeGunPixels = railgunPixels;
        else if (currentWeapon == 6) activeGunPixels = blasterPixels;
        else if (currentWeapon == 7) activeGunPixels = bfgPixels;
        else {
            int frame = 0;
            if (windUpStartTime > 0 && currentWeapon == 4) {
                frame = (int)((System.currentTimeMillis() / 50) % 3);
            }
            activeGunPixels = minigunFrames[frame];
        }

        int gunStartX;
        if (currentWeapon == 1) gunStartX = screenWidth - (32 * scale) + 150 + recoilX;
        else if (currentWeapon == 2) gunStartX = screenWidth - (32 * scale) + 50 + recoilX;
        else if (currentWeapon == 3) gunStartX = screenWidth - (32 * scale) + 60 + recoilX;
        else if (currentWeapon == 4) gunStartX = screenWidth / 2 - (16 * scale) + recoilX; // Minigun center
        else if (currentWeapon == 5) gunStartX = screenWidth / 2 - (16 * scale) + recoilX; // Railgun center
        else if (currentWeapon == 6) gunStartX = screenWidth / 2 - (16 * scale) + recoilX; // Blaster center
        else gunStartX = screenWidth / 2 - (16 * scale) + recoilX; // BFG center

        int gunStartY = screenHeight - (32 * scale) + 120 + recoilY + animOffsetY;

        for(int y = 0; y < 32; y++) {
            for(int x = 0; x < 32; x++) {
                int color = activeGunPixels[x + y * 32];
                if (color != 0) {
                    int drawX = gunStartX + x * scale + (currentWeapon == 1 ? swayX : 0);
                    int drawY = gunStartY + y * scale + swayY;
                    for(int dy = 0; dy < scale; dy++) {
                        for(int dx = 0; dx < scale; dx++) {
                            int px = drawX + dx;
                            int py = drawY + dy;
                            if (px >= 0 && px < screenWidth && py >= 0 && py < screenHeight) {
                                pixels[px + py * screenWidth] = color;
                            }
                        }
                    }
                }
            }
        }

        // Muzzle flash
        if (timeSinceFire < 70 && !isReloading) { 
            int flashTipX;
            int flashTipY;
            int flashSize;
            int starSize;
            
            if (currentWeapon == 1) {
                flashTipX = gunStartX + 7 * scale + swayX;
                flashTipY = gunStartY + 5 * scale + swayY;
                flashSize = 15;
                starSize = 4;
            } else if (currentWeapon == 2) {
                flashTipX = gunStartX + 10 * scale;
                flashTipY = gunStartY + 3 * scale;
                flashSize = 35;
                starSize = 8;
            } else if (currentWeapon == 3) {
                flashTipX = gunStartX + 14 * scale;
                flashTipY = gunStartY + 2 * scale;
                flashSize = 25;
                starSize = 6;
            } else if (currentWeapon == 5) {
                flashTipX = gunStartX + 16 * scale;
                flashTipY = gunStartY + 8 * scale;
                flashSize = 30;
                starSize = 7;
            } else if (currentWeapon == 6) {
                flashTipX = gunStartX + 16 * scale;
                flashTipY = gunStartY + 12 * scale;
                flashSize = 25;
                starSize = 6;
            } else if (currentWeapon == 7) {
                flashTipX = gunStartX + 16 * scale;
                flashTipY = gunStartY + 10 * scale;
                flashSize = 35;
                starSize = 8;
            } else { // Minigun
                flashTipX = gunStartX + 16 * scale;
                flashTipY = gunStartY + 2 * scale;
                flashSize = 40;
                starSize = 10;
            }

            for (int my = -60; my <= 60; my++) {
                for (int mx = -60; mx <= 60; mx++) {
                    double dist = Math.sqrt(mx*mx + my*my);
                    double star = (Math.abs(mx) < starSize || Math.abs(my) < starSize) ? 20.0 : 0.0;
                    if (dist < flashSize + star + Math.random() * 10) {
                        int fX = flashTipX + mx;
                        int fY = flashTipY + my;
                        if (fX >= 0 && fX < screenWidth && fY >= 0 && fY < screenHeight) {
                            int fr, fg, fb;
                            if (currentWeapon == 5 || currentWeapon == 6) {
                                // Light Blue / Cyan
                                fr = (int)(173 + Math.random() * 50);
                                fg = (int)(216 + Math.random() * 39);
                                fb = 255;
                            } else if (currentWeapon == 7) {
                                // Green Flash
                                fr = (int)(50 + Math.random() * 50);
                                fg = 255;
                                fb = (int)(50 + Math.random() * 50);
                            } else {
                                // Yellow / White
                                fr = 255;
                                fg = (int)(150 + Math.random() * 105);
                                fb = (int)(Math.random() * 100);
                            }
                            pixels[fX + fY * screenWidth] = (fr << 16) | (fg << 8) | fb;
                        }
                    }
                }
            }
        }

        // Render crosshair directly to pixel buffer
        int crosshairSize = 10;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        for (int i = -crosshairSize; i <= crosshairSize; i++) {
            if (centerX + i >= 0 && centerX + i < screenWidth) {
                pixels[(centerX + i) + centerY * screenWidth] = 0x000000; // black crosshair
            }
            if (centerY + i >= 0 && centerY + i < screenHeight) {
                pixels[centerX + (centerY + i) * screenWidth] = 0x000000;
            }
        }
    }
    
    public void run() {
        long lastTime = System.nanoTime();
        final double ns = 1000000000.0 / 60.0;
        double delta = 0;
        requestFocus();
        while(running) {
            long now = System.nanoTime();
            delta = delta + ((now-lastTime) / ns);
            lastTime = now;
            boolean shouldRender = false;
            while (delta >= 1)
            {
                shouldRender = true;
                if (state == GameState.PLAYING) {
                    if(up) {
                        if(map[(int)(posX + dirX * moveSpeed)][(int)posY] == 0) posX += dirX * moveSpeed;
                        if(map[(int)posX][(int)(posY + dirY * moveSpeed)] == 0) posY += dirY * moveSpeed;
                    }
                    if(down) {
                        if(map[(int)(posX - dirX * moveSpeed)][(int)posY] == 0) posX -= dirX * moveSpeed;
                        if(map[(int)posX][(int)(posY - dirY * moveSpeed)] == 0) posY -= dirY * moveSpeed;
                    }
                    if(right) {
                        // Strafe right
                        double strafeX = dirY;
                        double strafeY = -dirX;
                        if(map[(int)(posX + strafeX * moveSpeed)][(int)posY] == 0) posX += strafeX * moveSpeed;
                        if(map[(int)posX][(int)(posY + strafeY * moveSpeed)] == 0) posY += strafeY * moveSpeed;
                    }
                    if(left) {
                        // Strafe left
                        double strafeX = -dirY;
                        double strafeY = dirX;
                        if(map[(int)(posX + strafeX * moveSpeed)][(int)posY] == 0) posX += strafeX * moveSpeed;
                        if(map[(int)posX][(int)(posY + strafeY * moveSpeed)] == 0) posY += strafeY * moveSpeed;
                    }
                    
                    // Spawn enemies
                    double spawnChance = 0.01 + (score * 0.002);
                    int maxEnemies = 10 + (score / 5);
                    if (enemies.size() < maxEnemies && Math.random() < spawnChance) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            double spawnDist = Math.sqrt((rx-posX)*(rx-posX) + (ry-posY)*(ry-posY));
                            if(spawnDist > 5.0) {
                                enemies.add(new Enemy(rx + 0.5, ry + 0.5));
                            }
                        }
                    }
                    
                    // Spawn potions (average every 30 seconds = 1/1800 chance at 60fps)
                    if (Math.random() < 0.00055) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            potions.add(new Potion(rx + 0.5, ry + 0.5));
                        }
                    }
                    
                    // Spawn Shotgun (MTTH 10s = ~0.0016 chance per frame, after score 20)
                    if (!hasShotgun && shotgunPickups.isEmpty() && score >= 20 && Math.random() < 0.0016) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            shotgunPickups.add(new ShotgunPickup(rx + 0.5, ry + 0.5));
                        }
                    }

                    // Spawn M16 (MTTH 15s = ~0.0011 chance per frame, after score 50)
                    if (!hasM16 && m16Pickups.isEmpty() && score >= 50 && Math.random() < 0.0011) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            m16Pickups.add(new M16Pickup(rx + 0.5, ry + 0.5));
                        }
                    }

                    // Spawn Minigun (MTTH 20s = ~0.00082 chance per frame, after score 100)
                    if (!hasMinigun && minigunPickups.isEmpty() && score >= 100 && Math.random() < 0.00082) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            minigunPickups.add(new MinigunPickup(rx + 0.5, ry + 0.5));
                        }
                    }

                    // Spawn Railgun (MTTH 22s = ~0.00074 chance per frame, after score 175)
                    if (!hasRailgun && railgunPickups.isEmpty() && score >= 175 && Math.random() < 0.00074) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            railgunPickups.add(new RailgunPickup(rx + 0.5, ry + 0.5));
                        }
                    }

                    // Spawn Blaster (MTTH 25s = ~0.00066 chance per frame, after score 300)
                    if (!hasBlaster && blasterPickups.isEmpty() && score >= 300 && Math.random() < 0.00066) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            blasterPickups.add(new BlasterPickup(rx + 0.5, ry + 0.5));
                        }
                    }

                    // Spawn BFG 9000 (MTTH 30s = ~0.00056 chance per frame, after score 500)
                    if (!hasBFG && bfgPickups.isEmpty() && score >= 500 && Math.random() < 0.00056) {
                        int rx = (int)(Math.random() * map.length);
                        int ry = (int)(Math.random() * map[0].length);
                        if (map[rx][ry] == 0) {
                            bfgPickups.add(new BFG9000Pickup(rx + 0.5, ry + 0.5));
                        }
                    }
                    
                    // Update enemies
                    for (int i = enemies.size() - 1; i >= 0; i--) {
                        Enemy e = enemies.get(i);
                        if (e.dead) {
                            enemies.remove(i);
                            score++;
                            continue;
                        }
                        double dx = posX - e.x;
                        double dy = posY - e.y;
                        double length = Math.sqrt(dx*dx + dy*dy);
                        
                        // Player Damage Check
                        if (length < 0.8) {
                           long nowTime = System.currentTimeMillis();
                           if (nowTime - lastDamageTime > 1000) { // 1 second i-frames
                               playerHp--;
                               lastDamageTime = nowTime;
                               if (playerHp <= 0) {
                                   state = GameState.GAMEOVER;
                                   // Unlock cursor
                                   setCursor(Cursor.getDefaultCursor());
                               }
                           }
                        }

                        if (length > 0.5) { // don't get exactly inside player
                            double eMoveSpeed = 0.02; // slow pacing
                            double nX = e.x + (dx / length) * eMoveSpeed;
                            double nY = e.y + (dy / length) * eMoveSpeed;
                            if (map[(int)e.x][(int)nY] == 0) e.y = nY;
                            if (map[(int)nX][(int)e.y] == 0) e.x = nX;
                        }
                    }
                    
                    // Update potions
                    for (int i = potions.size() - 1; i >= 0; i--) {
                        Potion p = potions.get(i);
                        double dx = posX - p.x;
                        double dy = posY - p.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            playerHp = 10;
                            potions.remove(i);
                        }
                    }
                    
                    // Update shotgun pickups
                    for (int i = shotgunPickups.size() - 1; i >= 0; i--) {
                        ShotgunPickup s = shotgunPickups.get(i);
                        double dx = posX - s.x;
                        double dy = posY - s.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasShotgun = true;
                            currentWeapon = 2; // Auto equip on pickup
                            shotgunPickups.remove(i);
                        }
                    }

                    // Update M16 pickups
                    for (int i = m16Pickups.size() - 1; i >= 0; i--) {
                        M16Pickup m = m16Pickups.get(i);
                        double dx = posX - m.x;
                        double dy = posY - m.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasM16 = true;
                            currentWeapon = 3; // Auto equip on pickup
                            m16Pickups.remove(i);
                        }
                    }
                    
                    // Update minigun pickups
                    for (int i = minigunPickups.size() - 1; i >= 0; i--) {
                        MinigunPickup m = minigunPickups.get(i);
                        double dx = posX - m.x;
                        double dy = posY - m.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasMinigun = true;
                            currentWeapon = 4; // Auto equip on pickup
                            minigunPickups.remove(i);
                        }
                    }
                    
                    // Update blood splats
                    long nowTime = System.currentTimeMillis();
                    for (int i = bloodSplats.size() - 1; i >= 0; i--) {
                        if (nowTime - bloodSplats.get(i).spawnTime > 500) {
                            bloodSplats.remove(i);
                        }
                    }
                    
                    // Update railgun pickups
                    for (int i = railgunPickups.size() - 1; i >= 0; i--) {
                        RailgunPickup r = railgunPickups.get(i);
                        double dx = posX - r.x;
                        double dy = posY - r.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasRailgun = true;
                            currentWeapon = 5; // Auto equip on pickup
                            railgunPickups.remove(i);
                        }
                    }

                    // Update blaster pickups
                    for (int i = blasterPickups.size() - 1; i >= 0; i--) {
                        BlasterPickup b = blasterPickups.get(i);
                        double dx = posX - b.x;
                        double dy = posY - b.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasBlaster = true;
                            currentWeapon = 6; // Auto equip on pickup
                            blasterPickups.remove(i);
                        }
                    }

                    // Update bfg pickups
                    for (int i = bfgPickups.size() - 1; i >= 0; i--) {
                        BFG9000Pickup b = bfgPickups.get(i);
                        double dx = posX - b.x;
                        double dy = posY - b.y;
                        double dist = Math.sqrt(dx*dx + dy*dy);
                        if (dist < 0.5) {
                            hasBFG = true;
                            currentWeapon = 7; // Auto equip on pickup
                            bfgPickups.remove(i);
                        }
                    }

                    // Update projectils (BFG Ball)
                    for (int i = bfgProjectiles.size() - 1; i >= 0; i--) {
                        BFGProjectile p = bfgProjectiles.get(i);
                        double speed = 0.15;
                        double nextX = p.x + p.vx * speed;
                        double nextY = p.y + p.vy * speed;
                        
                        // Wall Collision
                        if (map[(int)nextX][(int)nextY] > 0) {
                            triggerBFGExplosion(p.x, p.y);
                            bfgProjectiles.remove(i);
                            continue;
                        }
                        
                        // Enemy Collision
                        boolean hit = false;
                        for (Enemy e : enemies) {
                            double edx = p.x - e.x;
                            double edy = p.y - e.y;
                            if (Math.sqrt(edx*edx + edy*edy) < 0.5) {
                                triggerBFGExplosion(p.x, p.y);
                                bfgProjectiles.remove(i);
                                hit = true;
                                break;
                            }
                        }
                        if (hit) continue;
                        
                        p.x = nextX;
                        p.y = nextY;
                        
                        // Lifetime check
                        if (System.currentTimeMillis() - p.spawnTime > 5000) {
                             bfgProjectiles.remove(i);
                        }
                    }
                    
                    // Update explosions
                    for (int i = bfgExplosions.size() - 1; i >= 0; i--) {
                        if (System.currentTimeMillis() - bfgExplosions.get(i).spawnTime > 500) {
                            bfgExplosions.remove(i);
                        }
                    }
                    
                    if (mouseDown && currentWeapon == 3) {
                        fireWeapon();
                    } else if (mouseDown && currentWeapon == 4) {
                        if (windUpStartTime == 0) windUpStartTime = System.currentTimeMillis();
                        if (System.currentTimeMillis() - windUpStartTime >= 750) fireWeapon();
                    } else if (mouseDown && currentWeapon == 7) {
                        fireWeapon(); // Handle charge and firing
                    } else {
                        if (currentWeapon == 4) windUpStartTime = 0;
                        if (currentWeapon == 7 && !mouseDown) windUpStartTime = 0; // Reset BFG charge
                    }
                    
                    render();
                }
                delta--;
            }
            
            if (shouldRender) {
                Graphics g = getGraphics();
                if (g != null) {
                    Graphics g2 = image.getGraphics();
                    if (state == GameState.PLAYING) {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 24));
                        String ammoStr;
                        if (currentWeapon == 1) ammoStr = "AMMO (PISTOL): " + ammo + " / 7";
                        else if (currentWeapon == 2) ammoStr = "AMMO (SHOTGUN): " + shotgunAmmo + " / 4";
                        else if (currentWeapon == 3) ammoStr = "AMMO (M16): " + m16Ammo + " / 30";
                        else if (currentWeapon == 4) {
                            ammoStr = "AMMO (MINIGUN): " + minigunAmmo + " / 180";
                            if (windUpStartTime > 0 && (System.currentTimeMillis() - windUpStartTime < 750)) {
                                g2.drawString("SPINNING UP...", 20, screenHeight - 130);
                            }
                        } else if (currentWeapon == 5) {
                            ammoStr = "AMMO (RAILGUN): " + railgunAmmo + " / 1";
                        } else if (currentWeapon == 6) {
                            ammoStr = "AMMO (BLASTER): " + blasterAmmo + " / 30";
                        } else if (currentWeapon == 7) {
                            ammoStr = "AMMO (BFG 9000): INFINITE";
                        } else ammoStr = "";
                        
                        g2.drawString(ammoStr, 20, screenHeight - 40);
                        g2.drawString("HEALTH: " + playerHp, 20, screenHeight - 70);
                        g2.drawString("SCORE: " + score, screenWidth - 150, 70);
                        if (isReloading) {
                            g2.drawString("RELOADING...", 20, screenHeight - 100);
                        }
                    } else if (state == GameState.GAMEOVER) {
                        drawGameOverGraphics(g2);
                    }
                    g2.dispose();
                    
                    g.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
                    g.dispose();
                }
            }
            
            try {
                Thread.sleep(2);
            } catch (Exception e) {}
        }
    }
    
    private void drawGameOverGraphics(Graphics g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, screenWidth, screenHeight);
        
        g.setColor(Color.RED);
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 72));
        String text = "YOU DIED";
        int textWidth = g.getFontMetrics().stringWidth(text);
        g.drawString(text, screenWidth / 2 - textWidth / 2, 150);
        
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 32));
        String scoreText = "FINAL SCORE: " + score;
        int scoreWidth = g.getFontMetrics().stringWidth(scoreText);
        g.drawString(scoreText, screenWidth / 2 - scoreWidth / 2, 210);
        
        // Buttons
        g.setFont(new java.awt.Font("Arial", java.awt.Font.BOLD, 36));
        
        // Try Again Button
        g.setColor(Color.GRAY);
        g.fillRect(screenWidth / 2 - 150, 250, 300, 60);
        g.setColor(Color.WHITE);
        g.drawRect(screenWidth / 2 - 150, 250, 300, 60);
        String tryAgain = "TRY AGAIN";
        int tryWidth = g.getFontMetrics().stringWidth(tryAgain);
        g.drawString(tryAgain, screenWidth / 2 - tryWidth / 2, 290);
        
        // Quit Button
        g.setColor(Color.GRAY);
        g.fillRect(screenWidth / 2 - 150, 350, 300, 60);
        g.setColor(Color.WHITE);
        g.drawRect(screenWidth / 2 - 150, 350, 300, 60);
        String quitText = "QUIT";
        int quitWidth = g.getFontMetrics().stringWidth(quitText);
        g.drawString(quitText, screenWidth / 2 - quitWidth / 2, 390);
    }
    
    private void restartGame() {
        playerHp = 10;
        ammo = 7;
        shotgunAmmo = 4;
        m16Ammo = 30;
        minigunAmmo = 180;
        railgunAmmo = 1;
        blasterAmmo = 30;
        hasShotgun = false;
        hasM16 = false;
        hasMinigun = false;
        hasRailgun = false;
        hasBlaster = false;
        hasBFG = false;
        currentWeapon = 1;
        score = 0;
        mouseDown = false;
        windUpStartTime = 0;
        isReloading = false;
        enemies.clear();
        potions.clear();
        shotgunPickups.clear();
        m16Pickups.clear();
        minigunPickups.clear();
        railgunPickups.clear();
        blasterPickups.clear();
        bfgPickups.clear();
        bfgProjectiles.clear();
        bfgExplosions.clear();
        bloodSplats.clear();
        posX = 22.0;
        posY = 11.5;
        dirX = -1.0;
        dirY = 0.0;
        planeX = 0.0;
        planeY = 0.66;
        pitch = 0;
        state = GameState.PLAYING;
        
        // Re-hide cursor
        BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "blank cursor");
        setCursor(blankCursor);
    }

    public void keyPressed(KeyEvent key) {
        if(key.getKeyCode() == KeyEvent.VK_LEFT || key.getKeyCode() == KeyEvent.VK_A)
            left = true;
        if(key.getKeyCode() == KeyEvent.VK_RIGHT || key.getKeyCode() == KeyEvent.VK_D)
            right = true;
        if(key.getKeyCode() == KeyEvent.VK_UP || key.getKeyCode() == KeyEvent.VK_W)
            up = true;
        if(key.getKeyCode() == KeyEvent.VK_DOWN || key.getKeyCode() == KeyEvent.VK_S)
            down = true;
        if(key.getKeyCode() == KeyEvent.VK_R) {
            if (!isReloading) {
                int maxAmmo = 7;
                int curAmmo = ammo;
                if (currentWeapon == 1) { maxAmmo = 7; curAmmo = ammo; }
                else if (currentWeapon == 2) { maxAmmo = 4; curAmmo = shotgunAmmo; }
                else if (currentWeapon == 3) { maxAmmo = 30; curAmmo = m16Ammo; }
                else if (currentWeapon == 4) { maxAmmo = 180; curAmmo = minigunAmmo; }
                else if (currentWeapon == 5) { maxAmmo = 1; curAmmo = railgunAmmo; }
                else if (currentWeapon == 6) { maxAmmo = 30; curAmmo = blasterAmmo; }
                
                if (curAmmo < maxAmmo) {
                    isReloading = true;
                    reloadStartTime = System.currentTimeMillis();
                }
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_1) {
            currentWeapon = 1;
        }
        if(key.getKeyCode() == KeyEvent.VK_2) {
            if (hasShotgun) {
                currentWeapon = 2;
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_3) {
            if (hasM16) {
                currentWeapon = 3;
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_4) {
            if (hasMinigun) {
                currentWeapon = 4;
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_5) {
            if (hasRailgun) {
                currentWeapon = 5;
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_6) {
            if (hasBlaster) {
                currentWeapon = 6;
            }
        }
        if(key.getKeyCode() == KeyEvent.VK_7) {
            if (hasBFG) {
                currentWeapon = 7;
            }
        }
    }
    
    public void keyReleased(KeyEvent key) {
        if(key.getKeyCode() == KeyEvent.VK_LEFT || key.getKeyCode() == KeyEvent.VK_A)
            left = false;
        if(key.getKeyCode() == KeyEvent.VK_RIGHT || key.getKeyCode() == KeyEvent.VK_D)
            right = false;
        if(key.getKeyCode() == KeyEvent.VK_UP || key.getKeyCode() == KeyEvent.VK_W)
            up = false;
        if(key.getKeyCode() == KeyEvent.VK_DOWN || key.getKeyCode() == KeyEvent.VK_S)
            down = false;
        // Escape key to exit safely
        if(key.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }
    }
    
    public void keyTyped(KeyEvent key) {
    }

    public void mouseMoved(MouseEvent e) {
        if(state == GameState.GAMEOVER) return;
        
        if(robotMovingMouse || robot == null) {
            robotMovingMouse = false;
            return;
        }

        int diffX = e.getX() - (screenWidth / 2);
        int diffY = e.getY() - (screenHeight / 2);
        
        if (diffX != 0) {
            double rotSpeedRatio = 0.002;
            double rot = diffX * rotSpeedRatio;
            
            // Allow negative rot
            double oldDirX = dirX;
            // negative rotation because screen X goes left-to-right
            rot = -rot;

            dirX = dirX * Math.cos(rot) - dirY * Math.sin(rot);
            dirY = oldDirX * Math.sin(rot) + dirY * Math.cos(rot);
            double oldPlaneX = planeX;
            planeX = planeX * Math.cos(rot) - planeY * Math.sin(rot);
            planeY = oldPlaneX * Math.sin(rot) + planeY * Math.cos(rot);
        }

        if (diffY != 0) {
            pitch -= diffY;
            // clamp pitch to avoid stretching the walls too far
            if (pitch > screenHeight / 2) pitch = screenHeight / 2;
            if (pitch < -screenHeight / 2) pitch = -screenHeight / 2;
        }
        
        try {
            Point p = getLocationOnScreen();
            robotMovingMouse = true;
            robot.mouseMove(p.x + screenWidth / 2, p.y + screenHeight / 2);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {}
    
    private void fireWeapon() {
        if (state != GameState.PLAYING || isReloading) return;
        
        long time = System.currentTimeMillis();
        int fireDelay = 500;
        int damage = 1;
        
        if (currentWeapon == 1) {
            if (ammo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 500;
            damage = 1;
        } else if (currentWeapon == 2) {
            if (shotgunAmmo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 500;
            damage = 3;
        } else if (currentWeapon == 3) {
            if (m16Ammo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 200; // 5 per second
            damage = 1;
        } else if (currentWeapon == 4) {
            if (minigunAmmo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 100; // 10 per second
            damage = 1;
        } else if (currentWeapon == 5) {
            if (railgunAmmo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 1000; // 1 per shot essentially
            damage = 99; // lethal
        } else if (currentWeapon == 6) {
            if (blasterAmmo <= 0) { isReloading = true; reloadStartTime = time; return; }
            fireDelay = 333; // 3 rounds per second
            damage = 99; // lethal
        } else if (currentWeapon == 7) {
            // BFG Wind-up
            if (windUpStartTime == 0) {
                windUpStartTime = time;
                return;
            }
            if (time - windUpStartTime < 500) {
                return; // Still charging
            }
            // Fire Projectile
            fireDelay = 1000;
            damage = 0; // Handled by projectile
        }
        
        if (time - lastFireTime < fireDelay) return;
        
        if (currentWeapon != 7 && currentWeapon == 1) ammo--;
        else if (currentWeapon == 2) shotgunAmmo--;
        else if (currentWeapon == 3) m16Ammo--;
        else if (currentWeapon == 4) minigunAmmo--;
        else if (currentWeapon == 5) railgunAmmo--;
        else if (currentWeapon == 6) blasterAmmo--;
        
        lastFireTime = time;
        if (currentWeapon != 4) windUpStartTime = 0; // Reset charge for single-shot/charging weapons

        if (currentWeapon == 7) {
             // Spawn Projectile
             bfgProjectiles.add(new BFGProjectile(posX + dirX * 0.5, posY + dirY * 0.5, dirX, dirY));
             return;
        }
        
        // Hit Scan Calculation
        if (currentWeapon == 5) {
            // Penetrating Shot Logic
            for (Enemy enemy : enemies) {
                double spriteX = enemy.x - posX;
                double spriteY = enemy.y - posY;
                double invDet = 1.0 / (planeX * dirY - dirX * planeY);
                double transformX = invDet * (dirY * spriteX - dirX * spriteY);
                double transformY = invDet * (-planeY * spriteX + planeX * spriteY);
                if (transformY <= 0) continue;
                int spriteScreenX = (int)((screenWidth / 2) * (1 + transformX / transformY));
                int spriteWidth = Math.abs((int)(screenHeight / transformY));
                int hitRange = spriteWidth / 2;
                if (Math.abs(spriteScreenX - screenWidth / 2) < hitRange) {
                    if (transformY < zBuffer[screenWidth / 2]) {
                        enemy.hp = 0;
                        enemy.dead = true;
                        bloodSplats.add(new BloodSplat(enemy.x, enemy.y));
                    }
                }
            }
            return;
        }
        
        Enemy hitEnemy = null;
        double closestDist = Double.MAX_VALUE;

        for (Enemy enemy : enemies) {
            double spriteX = enemy.x - posX;
            double spriteY = enemy.y - posY;

            double invDet = 1.0 / (planeX * dirY - dirX * planeY);
            
            double transformX = invDet * (dirY * spriteX - dirX * spriteY);
            double transformY = invDet * (-planeY * spriteX + planeX * spriteY);

            if (transformY <= 0) continue;

            int spriteScreenX = (int)((screenWidth / 2) * (1 + transformX / transformY));
            int spriteWidth = Math.abs((int)(screenHeight / transformY));
            
            int hitRange = spriteWidth / 2;
            
            // Center of screen is screenWidth / 2
            if (Math.abs(spriteScreenX - screenWidth / 2) < hitRange) {
                // Check if it's not occluded by a wall
                if (transformY < zBuffer[screenWidth / 2] && transformY < closestDist) {
                    closestDist = transformY;
                    hitEnemy = enemy;
                }
            }
        }
        
        if (hitEnemy != null) {
            hitEnemy.hp -= damage;
            if (hitEnemy.hp <= 0) {
                hitEnemy.dead = true;
                if (currentWeapon == 6) {
                    bloodSplats.add(new BloodSplat(hitEnemy.x, hitEnemy.y, 0x0000FF)); // Blue splash
                } else {
                    bloodSplats.add(new BloodSplat(hitEnemy.x, hitEnemy.y));
                }
            } else {
                bloodSplats.add(new BloodSplat(hitEnemy.x, hitEnemy.y));
            }
        }
    }

    private void triggerBFGExplosion(double x, double y) {
        bfgExplosions.add(new BFGExplosion(x, y));
        // AOE Damage
        double radius = 3.0;
        for (Enemy e : enemies) {
            double edx = e.x - x;
            double edy = e.y - y;
            if (Math.sqrt(edx*edx + edy*edy) < radius) {
                e.hp = 0;
                e.dead = true;
                bloodSplats.add(new BloodSplat(e.x, e.y, 0x00FF00)); // Green incineration
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (state == GameState.PLAYING) {
            if (SwingUtilities.isLeftMouseButton(e)) {
                mouseDown = true;
                fireWeapon(); // Initial shot
            }
        } else if (state == GameState.GAMEOVER) {
            int mx = e.getX();
            int my = e.getY();
            
            // Try Again Button: screenWidth / 2 - 150, 250, 300, 60
            if (mx >= screenWidth / 2 - 150 && mx <= screenWidth / 2 + 150 &&
                my >= 250 && my <= 310) {
                restartGame();
            }
            
            // Quit Button: screenWidth / 2 - 150, 350, 300, 60
            if (mx >= screenWidth / 2 - 150 && mx <= screenWidth / 2 + 150 &&
                my >= 350 && my <= 410) {
                System.exit(0);
            }
        }
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            mouseDown = false;
        }
    }
    
    @Override
    public void mouseEntered(MouseEvent e) {}
    
    @Override
    public void mouseExited(MouseEvent e) {}
    
    public static void main(String[] args) {
        new Pseudo3D();
    }
}
