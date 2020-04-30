package com.group_finity.mascot;

import com.group_finity.mascot.config.Configuration;
import com.group_finity.mascot.config.Entry;
import com.group_finity.mascot.exception.BehaviorInstantiationException;
import com.group_finity.mascot.exception.CantBeAliveException;
import com.group_finity.mascot.imagesetchooser.ImageSetChooser;
import com.group_finity.mascot.win.WindowsInteractiveWindowForm;
import org.w3c.dom.Document;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Program entry point.
 * <p>
 * Original Author: Yuki Yamada of Group Finity (http://www.group-finity.com/Shimeji/)
 * Currently developed by Shimeji-ee Group.
 */
public class Main {
    // Action that matches the "Gather Around Mouse!" context menu command
    static final String BEHAVIOR_GATHER = "跟随鼠标移动";
    private static final Logger log = Logger.getLogger(Main.class.getName());
    private static Main instance = new Main();
    private static Platform platform;
    private static JFrame frame = new javax.swing.JFrame();

    static {
        try {
            LogManager.getLogManager().readConfiguration(Main.class.getResourceAsStream("/logging.properties"));
        } catch (final SecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    private final Manager manager = new Manager();
    private ArrayList<String> imageSets = new ArrayList<>();
    private Hashtable<String, Configuration> configurations = new Hashtable<>();
    private Properties properties = new Properties();

    public static Main getInstance() {
        return instance;
    }

    public static Platform getPlatform() {
        return platform;
    }

    public static void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(final String[] args) {
        try {
            getInstance().run();
        } catch (OutOfMemoryError err) {
            log.log(Level.SEVERE, "Out of Memory Exception.  There are probably have too many "
                    + "Shimeji mascots in the image folder for your computer to handle.  Select fewer"
                    + " image sets or move some to the img/unused folder and try again.", err);
            Main.showError("Out of Memory.  There are probably have too many \n"
                    + "Shimeji mascots for your computer to handle.\n"
                    + "Select fewer image sets or move some to the \n"
                    + "img/unused folder and try again.");
            System.exit(0);
        }
    }

    public void run() {

        // Check platform
        if (!System.getProperty("sun.arch.data.model").equals("64")) {
            platform = Platform.x86;
        } else {
            platform = Platform.x86_64;
        }

        // Load properties
        properties = new Properties();
        FileInputStream input;
        try {
            input = new FileInputStream("./conf/settings.properties");
            properties.load(input);
        } catch (IOException ignored) {
        }

        // Get the image sets to use
        imageSets.addAll(Arrays.asList(properties.getProperty("ActiveShimeji", "").split("/")));
        if (imageSets.get(0).trim().isEmpty()) {
            imageSets = new ImageSetChooser(frame, true).display();
            if (imageSets == null) {
                exit();
            }
        }

        // Load settings
        for (String imageSet : imageSets) {
            loadConfiguration(imageSet);
        }

        // Create the tray icon
        createTrayIcon();

        // Create the first mascot
        for (String imageSet : imageSets) {
            createMascot(imageSet);
        }

        getManager().start();
    }

    private void loadConfiguration(final String imageSet) {

        try {
            String actionsFile = "./conf/actions.xml";
            if (new File("./conf/" + imageSet + "/actions.xml").exists()) {
                actionsFile = "./conf/" + imageSet + "/actions.xml";
            } else if (new File("./img/" + imageSet + "/conf/actions.xml").exists()) {
                actionsFile = "./img/" + imageSet + "/conf/actions.xml";
            }

            log.log(Level.INFO, imageSet + " Read Action File ({0})", actionsFile);

            final Document actions = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    new FileInputStream(new File(actionsFile)));

            Configuration configuration = new Configuration();

            configuration.load(new Entry(actions.getDocumentElement()), imageSet);

            String behaviorsFile = "./conf/behaviors.xml";
            if (new File("./conf/" + imageSet + "/behaviors.xml").exists()) {
                behaviorsFile = "./conf/" + imageSet + "/behaviors.xml";
            } else if (new File("./img/" + imageSet + "/conf/behaviors.xml").exists()) {
                behaviorsFile = "./img/" + imageSet + "/conf/behaviors.xml";
            }

            log.log(Level.INFO, imageSet + " Read Behavior File ({0})", behaviorsFile);

            final Document behaviors = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                    new FileInputStream(new File(behaviorsFile)));

            configuration.load(new Entry(behaviors.getDocumentElement()), imageSet);

            configuration.validate();

            configurations.put(imageSet, configuration);

        } catch (final Exception e) {
            log.log(Level.SEVERE, "Failed to load configuration files", e);
            Main.showError("Failed to load configuration files.\nSee log for more details.");
            exit();
        }
    }

    /**
     * Create a tray icon.
     *
     * @ Throws AWTException
     * @ Throws IOException
     */
    private void createTrayIcon() {
        log.log(Level.INFO, "create a tray icon");

        // "Another One!" menu item
        final JMenuItem increaseMenu = new JMenuItem("再次寻访");
        increaseMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                createMascot();
            }
        });

        // "Follow One!" Menu item
        final JMenuItem gatherMenu = new JMenuItem("跟随鼠标");
        gatherMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                Main.this.getManager().setBehaviorAll(BEHAVIOR_GATHER);
            }
        });

        // "Reduce to One!" menu item
        final JMenuItem oneMenu = new JMenuItem("减少到一个");
        oneMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                Main.this.getManager().remainOne();
            }
        });

        // "Restore IE!" menu item
        final JMenuItem restoreMenu = new JMenuItem("还原窗口");
        restoreMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent event) {
                NativeFactory.getInstance().getEnvironment().restoreWindows();
            }
        });

        // "Bye Everyone!" menu item
        final JMenuItem closeMenu = new JMenuItem("退出程序");
        closeMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                exit();
            }
        });

        // settings
        final JMenuItem chooseShimejiMenu = new JMenuItem("选择干员");
        chooseShimejiMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                boolean isExit = Main.this.getManager().isExitOnLastRemoved();
                Main.this.getManager().setExitOnLastRemoved(false);
                Main.this.getManager().disposeAll();

                // Get the image sets to use
                ArrayList<String> temporaryImageSet = new ArrayList<String>();
                temporaryImageSet = new ImageSetChooser(frame, true).display();
                if (temporaryImageSet != null) {
                    imageSets = temporaryImageSet;
                }

                // Load settings
                for (String imageSet : imageSets) {
                    loadConfiguration(imageSet);
                }

                // Create the first mascot
                for (String imageSet : imageSets) {
                    createMascot(imageSet);
                }

                Main.this.getManager().setExitOnLastRemoved(isExit);
            }
        });

        // "Interactive Windows" menu item
        final JMenuItem interactiveMenu = new JMenuItem("设置可交互窗口");
        interactiveMenu.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                new WindowsInteractiveWindowForm(frame, true).display();
                NativeFactory.getInstance().getEnvironment().refreshCache();
            }
        });

        // Create the context "popup" menus.
        final JPopupMenu trayPopup = new JPopupMenu();
        final JMenu settingsMenu = new JMenu("设置");

        settingsMenu.add(chooseShimejiMenu);
        settingsMenu.add(interactiveMenu);
        trayPopup.add(increaseMenu);
        trayPopup.add(gatherMenu);
        trayPopup.add(oneMenu);
        trayPopup.add(restoreMenu);
        trayPopup.add(settingsMenu);
        trayPopup.add(closeMenu);

        try {
            // Create the tray icon
            final TrayIcon icon = new TrayIcon(ImageIO.read(Main.class.getResource("/icon.png")), "shimeji-ee");
            // 解决TrayIcon和JPopupMenu的兼容问题
            icon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowPopup(e);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowPopup(e);
                }

                private void maybeShowPopup(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        trayPopup.setLocation(e.getX(), e.getY());
                        trayPopup.setInvoker(trayPopup);
                        trayPopup.setVisible(true);
                    }
                }
            });
            // Show tray icon
            SystemTray.getSystemTray().add(icon);
        } catch (final IOException e) {
            log.log(Level.SEVERE, "Failed to create tray icon", e);
            Main.showError("Failed to display system tray.\nSee log for more details.");
            exit();
        } catch (final AWTException e) {
            log.log(Level.SEVERE, "Failed to create tray icon", e);
            Main.showError("Failed to display system tray.\nSee log for more details.");
            getManager().setExitOnLastRemoved(true);
        }

    }

    // Randomly creates a mascot
    public void createMascot() {
        int length = imageSets.size();
        int random = (int) (length * Math.random());
        createMascot(imageSets.get(random));
    }

    /**
     * Create a mascot
     */
    public void createMascot(String imageSet) {
        log.log(Level.INFO, "create a mascot");

        // Create one mascot
        final Mascot mascot = new Mascot(imageSet);

        // Create it outside the bounds of the screen
        mascot.setAnchor(new Point(-1000, -1000));

        // Randomize the initial orientation
        mascot.setLookRight(Math.random() < 0.5);

        try {
            mascot.setBehavior(getConfiguration(imageSet).buildBehavior(null, mascot));
            this.getManager().add(mascot);
        } catch (final BehaviorInstantiationException e) {
            log.log(Level.SEVERE, "Failed to initialize the first action", e);
            Main.showError("Failed to initialize first action.\nSee log for more details.");
            mascot.dispose();
        } catch (final CantBeAliveException e) {
            log.log(Level.SEVERE, "Fatal Error", e);
            Main.showError("Failed to initialize first action.\nSee log for more details.");
            mascot.dispose();
        } catch (Exception e) {
            log.log(Level.SEVERE, imageSet + " fatal error, can not be started.", e);
            Main.showError("Could not create " + imageSet + ".\nSee log for more details.");
            mascot.dispose();
        }
    }

    public Configuration getConfiguration(String imageSet) {
        return configurations.get(imageSet);
    }

    private Manager getManager() {
        return this.manager;
    }

    public Properties getProperties() {
        return properties;
    }

    public void exit() {
        this.getManager().disposeAll();
        this.getManager().stop();

        System.exit(0);
    }
}
